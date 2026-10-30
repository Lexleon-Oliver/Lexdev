package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

const (
	protocolVersion      = 1
	requestDomain        = "SYSTEMPRO-HTTP-REQUEST-V1"
	signedObjectDomain   = "SYSTEMPRO-SIGNED-OBJECT-V1"
	machineBindingDomain = "SYSTEMPRO-MACHINE-BINDING-V1"
)

type Config struct {
	ServerURL                string `json:"serverUrl"`
	ActivationPath           string `json:"activationPath"`
	HeartbeatPath            string `json:"heartbeatPath"`
	RenewalPath              string `json:"renewalPath"`
	SystemProVersion         string `json:"systemProVersion"`
	InstanceDir              string `json:"instanceDir"`
	RootPublicFile           string `json:"rootPublicFile"`
	RequestTimeoutSeconds    int    `json:"requestTimeoutSeconds"`
	HeartbeatIntervalSeconds int    `json:"heartbeatIntervalSeconds"`
	RenewalIntervalSeconds   int    `json:"renewalIntervalSeconds"`
	JitterSeconds            int    `json:"jitterSeconds"`
}

type RootDescriptor struct {
	FormatVersion int    `json:"formatVersion"`
	KeyID         string `json:"keyId"`
	Algorithm     string `json:"algorithm"`
	PublicKey     string `json:"publicKey"`
}

type Identity struct {
	FormatVersion  int    `json:"formatVersion"`
	InstallationID string `json:"installationId"`
	KeyID          string `json:"keyId"`
	PublicKey      string `json:"publicKey"`
	PrivateKey     string `json:"privateKey"`
}

type PublicInstance struct {
	FormatVersion  int    `json:"formatVersion"`
	InstallationID string `json:"installationId"`
	KeyID          string `json:"keyId"`
	PublicKey      string `json:"publicKey"`
	CreatedAt      string `json:"createdAt"`
}

type Binding struct {
	BindingVersion int    `json:"bindingVersion"`
	OSIDHash       string `json:"osIdHash"`
	SystemUUIDHash string `json:"systemUuidHash"`
}

type SignedEnvelope struct {
	EnvelopeVersion int    `json:"envelopeVersion"`
	KeyID           string `json:"keyId"`
	Algorithm       string `json:"algorithm"`
	Payload         string `json:"payload"`
	Signature       string `json:"signature"`
}

type ActivationBody struct {
	ProtocolVersion  int     `json:"protocolVersion"`
	ActivationCode   string  `json:"activationCode"`
	InstallationID   string  `json:"installationId"`
	KeyID            string  `json:"keyId"`
	PublicKey        string  `json:"publicKey"`
	Binding          Binding `json:"binding"`
	SystemProVersion string  `json:"systemProVersion"`
}

type HeartbeatBody struct {
	ProtocolVersion  int     `json:"protocolVersion"`
	InstallationID   string  `json:"installationId"`
	Binding          Binding `json:"binding"`
	SystemProVersion string  `json:"systemProVersion"`
}

type RenewalBody struct {
	ProtocolVersion  int     `json:"protocolVersion"`
	InstallationID   string  `json:"installationId"`
	BindingID        string  `json:"bindingId"`
	CurrentLeaseID   string  `json:"currentLeaseId"`
	Binding          Binding `json:"binding"`
	SystemProVersion string  `json:"systemProVersion"`
}

type ActivationResponse struct {
	InstallationID       string         `json:"installationId"`
	Lease                SignedEnvelope `json:"lease"`
	ServerKeyCertificate SignedEnvelope `json:"serverKeyCertificate"`
}

type AgentState struct {
	FormatVersion   int    `json:"formatVersion"`
	LastHeartbeatAt string `json:"lastHeartbeatAt,omitempty"`
	LastRenewalAt   string `json:"lastRenewalAt,omitempty"`
	LastSuccessAt   string `json:"lastSuccessAt,omitempty"`
	LastErrorCode   string `json:"lastErrorCode,omitempty"`
	LastErrorAt     string `json:"lastErrorAt,omitempty"`
}

type Agent struct {
	cfg    Config
	client *http.Client
	mu     sync.Mutex
}

func main() {
	if err := realMain(); err != nil {
		fmt.Fprintln(os.Stderr, "SYSTEMPRO_LICENSE_AGENT_ERROR:", err)
		os.Exit(1)
	}
}

func realMain() error {
	if len(os.Args) < 2 {
		return usageError()
	}
	command := os.Args[1]
	fs := flag.NewFlagSet(command, flag.ContinueOnError)
	configPath := fs.String("config", defaultConfigPath(), "caminho do config.json")
	codeStdin := fs.Bool("code-stdin", false, "ler activation code do stdin")
	if err := fs.Parse(os.Args[2:]); err != nil {
		return err
	}

	cfg, err := loadConfig(*configPath)
	if err != nil {
		return err
	}
	agent := &Agent{cfg: cfg, client: &http.Client{Timeout: time.Duration(cfg.RequestTimeoutSeconds) * time.Second}}

	switch command {
	case "activate":
		if !*codeStdin {
			return errors.New("activate exige --code-stdin; o código não deve aparecer na linha de comando")
		}
		code, err := readActivationCode(os.Stdin)
		if err != nil {
			return err
		}
		return agent.activate(context.Background(), code)
	case "heartbeat":
		return agent.heartbeat(context.Background())
	case "renew":
		return agent.renew(context.Background())
	case "once":
		return agent.runDue(context.Background(), true)
	case "status":
		return agent.printStatus(os.Stdout)
	case "service":
		return runAsService(agent)
	default:
		return usageError()
	}
}

func usageError() error {
	return errors.New("uso: systempro-license-agent {activate --code-stdin|heartbeat|renew|once|status|service} [--config arquivo]")
}

func defaultConfigPath() string {
	exe, err := os.Executable()
	if err == nil {
		return filepath.Join(filepath.Dir(exe), "config.json")
	}
	return "config.json"
}

func loadConfig(path string) (Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return Config{}, fmt.Errorf("ler configuração: %w", err)
	}
	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return Config{}, fmt.Errorf("configuração JSON inválida: %w", err)
	}
	cfg.ServerURL = strings.TrimRight(cfg.ServerURL, "/")
	if cfg.ServerURL == "" || !strings.HasPrefix(cfg.ServerURL, "https://") {
		return Config{}, errors.New("serverUrl deve usar HTTPS")
	}
	if cfg.ActivationPath == "" {
		cfg.ActivationPath = "/api/v1/activations"
	}
	if cfg.HeartbeatPath == "" {
		cfg.HeartbeatPath = "/api/v1/heartbeats"
	}
	if cfg.RenewalPath == "" {
		cfg.RenewalPath = "/api/v1/leases/renewals"
	}
	for _, p := range []string{cfg.ActivationPath, cfg.HeartbeatPath, cfg.RenewalPath} {
		if !strings.HasPrefix(p, "/") || strings.ContainsAny(p, "\r\n") {
			return Config{}, errors.New("path de API inválido")
		}
	}
	if cfg.SystemProVersion == "" {
		return Config{}, errors.New("systemProVersion é obrigatório")
	}
	if cfg.InstanceDir == "" || cfg.RootPublicFile == "" {
		return Config{}, errors.New("instanceDir/rootPublicFile são obrigatórios")
	}
	if cfg.RequestTimeoutSeconds <= 0 {
		cfg.RequestTimeoutSeconds = 30
	}
	if cfg.HeartbeatIntervalSeconds <= 0 {
		cfg.HeartbeatIntervalSeconds = 21600
	}
	if cfg.RenewalIntervalSeconds <= 0 {
		cfg.RenewalIntervalSeconds = 86400
	}
	if cfg.JitterSeconds < 0 {
		cfg.JitterSeconds = 0
	}
	return cfg, nil
}

func readActivationCode(r io.Reader) (string, error) {
	scanner := bufio.NewScanner(io.LimitReader(r, 4096))
	if !scanner.Scan() {
		return "", errors.New("activation code não informado")
	}
	code := strings.TrimSpace(scanner.Text())
	if code == "" || len(code) > 512 {
		return "", errors.New("activation code inválido")
	}
	return code, nil
}

func (a *Agent) activate(ctx context.Context, activationCode string) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	if err := a.ensureDirs(); err != nil {
		return err
	}
	identity, err := a.loadOrCreateIdentity()
	if err != nil {
		return err
	}
	binding, err := collectBinding()
	if err != nil {
		return err
	}
	body := ActivationBody{protocolVersion, activationCode, identity.InstallationID, identity.KeyID, identity.PublicKey, binding, a.cfg.SystemProVersion}
	responseBytes, status, err := a.signedPOST(ctx, a.cfg.ActivationPath, identity, body)
	if err != nil {
		return err
	}
	if status != http.StatusCreated && status != http.StatusOK {
		return apiError(status, responseBytes)
	}

	var response ActivationResponse
	if err := json.Unmarshal(responseBytes, &response); err != nil {
		return fmt.Errorf("resposta de ativação inválida: %w", err)
	}
	if response.InstallationID != identity.InstallationID {
		return errors.New("License Server retornou installationId divergente")
	}
	if err := a.verifyAndStoreCertificate(response.ServerKeyCertificate); err != nil {
		return err
	}
	leasePayload, err := a.verifyServerEnvelope(response.Lease, "LICENSE_LEASE")
	if err != nil {
		return fmt.Errorf("lease inicial inválido: %w", err)
	}
	if text(leasePayload, "installationId") != identity.InstallationID {
		return errors.New("lease inicial pertence a outra instalação")
	}
	if err := writeJSONAtomic(a.publicPath("lease.json"), response.Lease, 0644); err != nil {
		return err
	}
	public := PublicInstance{1, identity.InstallationID, identity.KeyID, identity.PublicKey, time.Now().UTC().Format(time.RFC3339Nano)}
	if err := writeJSONAtomic(a.publicPath("instance.json"), public, 0644); err != nil {
		return err
	}
	_ = os.Remove(a.publicPath("license-status.json"))
	now := time.Now().UTC().Format(time.RFC3339Nano)
	return a.saveState(AgentState{FormatVersion: 1, LastRenewalAt: now, LastSuccessAt: now})
}

func (a *Agent) heartbeat(ctx context.Context) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	identity, err := a.loadIdentity()
	if err != nil {
		return err
	}
	binding, err := collectBinding()
	if err != nil {
		return err
	}
	body := HeartbeatBody{protocolVersion, identity.InstallationID, binding, a.cfg.SystemProVersion}
	responseBytes, status, err := a.signedPOST(ctx, a.cfg.HeartbeatPath, identity, body)
	if err != nil {
		a.recordError("NETWORK_HEARTBEAT")
		return err
	}
	if err := a.processServerResponse(responseBytes, status, false); err != nil {
		a.recordError("HEARTBEAT_REJECTED")
		return err
	}
	state := a.loadState()
	now := time.Now().UTC().Format(time.RFC3339Nano)
	state.FormatVersion = 1
	state.LastHeartbeatAt = now
	state.LastSuccessAt = now
	state.LastErrorCode = ""
	state.LastErrorAt = ""
	return a.saveState(state)
}

func (a *Agent) renew(ctx context.Context) error {
	a.mu.Lock()
	defer a.mu.Unlock()
	identity, err := a.loadIdentity()
	if err != nil {
		return err
	}
	lease, err := a.readEnvelope(a.publicPath("lease.json"))
	if err != nil {
		return err
	}
	leasePayload, err := a.verifyServerEnvelope(lease, "LICENSE_LEASE")
	if err != nil {
		return err
	}
	bindingID := text(leasePayload, "bindingId")
	if bindingID == "" {
		return errors.New("lease atual não contém bindingId")
	}
	currentLeaseID := text(leasePayload, "leaseId")
	if currentLeaseID == "" {
		return errors.New("lease atual não contém leaseId")
	}
	binding, err := collectBinding()
	if err != nil {
		return err
	}
	body := RenewalBody{protocolVersion, identity.InstallationID, bindingID, currentLeaseID, binding, a.cfg.SystemProVersion}
	responseBytes, status, err := a.signedPOST(ctx, a.cfg.RenewalPath, identity, body)
	if err != nil {
		a.recordError("NETWORK_RENEWAL")
		return err
	}
	if err := a.processServerResponse(responseBytes, status, true); err != nil {
		a.recordError("RENEWAL_REJECTED")
		return err
	}
	state := a.loadState()
	now := time.Now().UTC().Format(time.RFC3339Nano)
	state.FormatVersion = 1
	state.LastRenewalAt = now
	state.LastSuccessAt = now
	state.LastErrorCode = ""
	state.LastErrorAt = ""
	return a.saveState(state)
}

func (a *Agent) processServerResponse(body []byte, status int, requireLease bool) error {
	var root map[string]json.RawMessage
	if len(bytes.TrimSpace(body)) == 0 {
		if status >= 200 && status < 300 && !requireLease {
			_ = os.Remove(a.publicPath("license-status.json"))
			return nil
		}
		return fmt.Errorf("License Server retornou HTTP %d sem corpo", status)
	}
	if err := json.Unmarshal(body, &root); err != nil {
		return apiError(status, body)
	}

	if raw, ok := root["serverKeyCertificate"]; ok {
		var cert SignedEnvelope
		if json.Unmarshal(raw, &cert) == nil {
			if err := a.verifyAndStoreCertificate(cert); err != nil {
				return err
			}
		}
	}

	var savedStatus bool
	for _, key := range []string{"licenseStatus", "signedStatus", "authorizationStatus", "status"} {
		raw, ok := root[key]
		if !ok {
			continue
		}
		var env SignedEnvelope
		if json.Unmarshal(raw, &env) != nil || env.EnvelopeVersion == 0 {
			continue
		}
		if _, err := a.verifyServerEnvelope(env, "LICENSE_STATUS"); err != nil {
			return fmt.Errorf("status assinado inválido: %w", err)
		}
		if err := writeJSONAtomic(a.publicPath("license-status.json"), env, 0644); err != nil {
			return err
		}
		savedStatus = true
		break
	}

	var savedLease bool
	if raw, ok := root["lease"]; ok {
		var env SignedEnvelope
		if err := json.Unmarshal(raw, &env); err == nil && env.EnvelopeVersion != 0 {
			payload, err := a.verifyServerEnvelope(env, "LICENSE_LEASE")
			if err != nil {
				return fmt.Errorf("lease renovado inválido: %w", err)
			}
			identity, _ := a.loadIdentity()
			if identity != nil && text(payload, "installationId") != identity.InstallationID {
				return errors.New("lease renovado pertence a outra instalação")
			}
			if err := writeJSONAtomic(a.publicPath("lease.json"), env, 0644); err != nil {
				return err
			}
			savedLease = true
		}
	}

	// Compatibilidade defensiva com nomes de campos de resposta: qualquer
	// envelope assinado de topo é classificado pelo purpose apenas para
	// escolher o verificador; o conteúdo só é aceito depois da assinatura.
	if !savedStatus || (requireLease && !savedLease) {
		for _, raw := range root {
			var env SignedEnvelope
			if err := json.Unmarshal(raw, &env); err != nil || env.EnvelopeVersion != 1 || env.Payload == "" {
				continue
			}
			payloadBytes, err := b64decode(env.Payload)
			if err != nil {
				continue
			}
			var hint map[string]any
			if json.Unmarshal(payloadBytes, &hint) != nil {
				continue
			}
			switch text(hint, "purpose") {
			case "LICENSE_STATUS":
				if savedStatus {
					continue
				}
				if _, err := a.verifyServerEnvelope(env, "LICENSE_STATUS"); err != nil {
					return fmt.Errorf("status assinado inválido: %w", err)
				}
				if err := writeJSONAtomic(a.publicPath("license-status.json"), env, 0644); err != nil {
					return err
				}
				savedStatus = true
			case "LICENSE_LEASE":
				if savedLease {
					continue
				}
				payload, err := a.verifyServerEnvelope(env, "LICENSE_LEASE")
				if err != nil {
					return fmt.Errorf("lease renovado inválido: %w", err)
				}
				identity, _ := a.loadIdentity()
				if identity != nil && text(payload, "installationId") != identity.InstallationID {
					return errors.New("lease renovado pertence a outra instalação")
				}
				if err := writeJSONAtomic(a.publicPath("lease.json"), env, 0644); err != nil {
					return err
				}
				savedLease = true
			}
		}
	}

	if status >= 200 && status < 300 {
		if !savedStatus {
			_ = os.Remove(a.publicPath("license-status.json"))
		}
		if requireLease && !savedLease {
			return errors.New("renewal bem sucedido sem lease assinado")
		}
		return nil
	}
	if savedStatus {
		return fmt.Errorf("License Server restringiu a instalação (HTTP %d)", status)
	}
	return apiError(status, body)
}

func (a *Agent) runDue(ctx context.Context, force bool) error {
	state := a.loadState()
	now := time.Now().UTC()
	identity, _ := a.loadIdentity()
	installationID := "unactivated"
	if identity != nil {
		installationID = identity.InstallationID
	}
	var errs []error
	if force || due(state.LastHeartbeatAt, a.cfg.HeartbeatIntervalSeconds, stableJitterSeconds(installationID, "heartbeat", a.cfg.JitterSeconds), now) {
		if err := a.heartbeat(ctx); err != nil {
			errs = append(errs, err)
		}
	}
	state = a.loadState()
	if force || due(state.LastRenewalAt, a.cfg.RenewalIntervalSeconds, stableJitterSeconds(installationID, "renewal", a.cfg.JitterSeconds), now) {
		if err := a.renew(ctx); err != nil {
			errs = append(errs, err)
		}
	}
	return errors.Join(errs...)
}

func due(last string, intervalSeconds, jitterSeconds int, now time.Time) bool {
	if last == "" {
		return true
	}
	t, err := time.Parse(time.RFC3339Nano, last)
	if err != nil {
		return true
	}
	threshold := intervalSeconds + jitterSeconds
	if threshold < 60 {
		threshold = 60
	}
	return now.Sub(t) >= time.Duration(threshold)*time.Second
}

func stableJitterSeconds(installationID, purpose string, max int) int {
	if max <= 0 {
		return 0
	}
	digest := sha256.Sum256([]byte(installationID + "\x00" + purpose))
	n := int(digest[0])<<8 | int(digest[1])
	return (n % (max*2 + 1)) - max
}

func (a *Agent) serviceLoop(ctx context.Context) error {
	// Small wake-up interval; actual heartbeat/renewal cadence is persisted and checked by runDue.
	timer := time.NewTicker(60 * time.Second)
	defer timer.Stop()
	_ = a.runDue(ctx, false)
	for {
		select {
		case <-ctx.Done():
			return nil
		case <-timer.C:
			_ = a.runDue(ctx, false)
		}
	}
}

func (a *Agent) signedPOST(ctx context.Context, path string, identity *Identity, body any) ([]byte, int, error) {
	bodyBytes, err := json.Marshal(body)
	if err != nil {
		return nil, 0, err
	}
	requestID, err := uuidV4()
	if err != nil {
		return nil, 0, err
	}
	timestamp := time.Now().UTC().Format(time.RFC3339Nano)
	digest := sha256.Sum256(bodyBytes)
	canonical := strings.Join([]string{requestDomain, "POST", path, identity.InstallationID, identity.KeyID, requestID, timestamp, b64(digest[:])}, "\n")
	privateBytes, err := b64decode(identity.PrivateKey)
	if err != nil {
		return nil, 0, err
	}
	if len(privateBytes) != ed25519.PrivateKeySize {
		return nil, 0, errors.New("installation private key inválida")
	}
	signature := ed25519.Sign(ed25519.PrivateKey(privateBytes), []byte(canonical))

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, a.cfg.ServerURL+path, bytes.NewReader(bodyBytes))
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "SystemPro-License-Agent/"+a.cfg.SystemProVersion)
	req.Header.Set("X-SystemPro-Protocol", "1")
	req.Header.Set("Installation-Id", identity.InstallationID)
	req.Header.Set("Key-Id", identity.KeyID)
	req.Header.Set("Request-Id", requestID)
	req.Header.Set("Timestamp", timestamp)
	req.Header.Set("Signature", b64(signature))
	resp, err := a.client.Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()
	responseBytes, err := io.ReadAll(io.LimitReader(resp.Body, 2*1024*1024))
	return responseBytes, resp.StatusCode, err
}

func (a *Agent) verifyAndStoreCertificate(cert SignedEnvelope) error {
	root, err := loadRoot(a.cfg.RootPublicFile)
	if err != nil {
		return err
	}
	payload, err := verifyEnvelope(cert, "SERVER_KEY_CERTIFICATE", root.KeyID, root.PublicKey)
	if err != nil {
		return fmt.Errorf("certificado do servidor inválido: %w", err)
	}
	if strings.ToUpper(text(payload, "algorithm")) != "ED25519" {
		return errors.New("certificado usa algoritmo inválido")
	}
	if text(payload, "keyId") == "" || text(payload, "publicKey") == "" {
		return errors.New("certificado sem identidade da server key")
	}
	now := time.Now().UTC()
	nb, e1 := time.Parse(time.RFC3339Nano, text(payload, "notBefore"))
	na, e2 := time.Parse(time.RFC3339Nano, text(payload, "notAfter"))
	if e1 != nil || e2 != nil || now.Before(nb) || now.After(na) {
		return errors.New("certificado do servidor fora da validade")
	}
	return writeJSONAtomic(a.publicPath("server-key-certificate.json"), cert, 0644)
}

func (a *Agent) verifyServerEnvelope(env SignedEnvelope, purpose string) (map[string]any, error) {
	cert, err := a.readEnvelope(a.publicPath("server-key-certificate.json"))
	if err != nil {
		return nil, err
	}
	root, err := loadRoot(a.cfg.RootPublicFile)
	if err != nil {
		return nil, err
	}
	certPayload, err := verifyEnvelope(cert, "SERVER_KEY_CERTIFICATE", root.KeyID, root.PublicKey)
	if err != nil {
		return nil, err
	}
	if strings.ToUpper(text(certPayload, "algorithm")) != "ED25519" {
		return nil, errors.New("certificado usa algoritmo inválido")
	}
	serverKeyID := text(certPayload, "keyId")
	serverPublic := text(certPayload, "publicKey")
	if serverKeyID == "" || serverPublic == "" {
		return nil, errors.New("certificado sem identidade da server key")
	}
	now := time.Now().UTC()
	nb, e1 := time.Parse(time.RFC3339Nano, text(certPayload, "notBefore"))
	na, e2 := time.Parse(time.RFC3339Nano, text(certPayload, "notAfter"))
	if e1 != nil || e2 != nil || now.Before(nb) || now.After(na) {
		return nil, errors.New("certificado do servidor fora da validade")
	}
	return verifyEnvelope(env, purpose, serverKeyID, serverPublic)
}

func verifyEnvelope(env SignedEnvelope, purpose, expectedKeyID, publicKeyString string) (map[string]any, error) {
	if env.EnvelopeVersion != 1 || !strings.EqualFold(env.Algorithm, "Ed25519") || env.KeyID != expectedKeyID {
		return nil, errors.New("estrutura/identidade do envelope inválida")
	}
	payloadBytes, err := b64decode(env.Payload)
	if err != nil {
		return nil, err
	}
	sig, err := b64decode(env.Signature)
	if err != nil {
		return nil, err
	}
	pub, err := b64decode(publicKeyString)
	if err != nil {
		return nil, err
	}
	if len(pub) != ed25519.PublicKeySize || len(sig) != ed25519.SignatureSize {
		return nil, errors.New("tamanho de chave/assinatura inválido")
	}
	message := append([]byte(signedObjectDomain+"\x00"+purpose+"\x00"), payloadBytes...)
	if !ed25519.Verify(ed25519.PublicKey(pub), message, sig) {
		return nil, errors.New("assinatura Ed25519 inválida")
	}
	var payload map[string]any
	if err := json.Unmarshal(payloadBytes, &payload); err != nil {
		return nil, err
	}
	if intNumber(payload["protocolVersion"]) != 1 || text(payload, "purpose") != purpose {
		return nil, errors.New("purpose/protocolo do payload inválido")
	}
	return payload, nil
}

func loadRoot(path string) (RootDescriptor, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return RootDescriptor{}, err
	}
	var root RootDescriptor
	if err := json.Unmarshal(data, &root); err != nil {
		return root, err
	}
	if root.FormatVersion != 1 || root.KeyID == "" || !strings.EqualFold(root.Algorithm, "Ed25519") {
		return root, errors.New("root public descriptor inválido")
	}
	pub, err := b64decode(root.PublicKey)
	if err != nil || len(pub) != ed25519.PublicKeySize {
		return root, errors.New("root public key inválida")
	}
	return root, nil
}

func (a *Agent) loadOrCreateIdentity() (*Identity, error) {
	path := a.privatePath("installation-key.json")
	if _, err := os.Stat(path); err == nil {
		return a.loadIdentity()
	}
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, err
	}
	installationID, err := uuidV4()
	if err != nil {
		return nil, err
	}
	digest := sha256.Sum256(pub)
	keyID := "installation-" + hex.EncodeToString(digest[:8])
	identity := &Identity{1, installationID, keyID, b64(pub), b64(priv)}
	if err := writeJSONAtomic(path, identity, 0600); err != nil {
		return nil, err
	}
	return identity, nil
}

func (a *Agent) loadIdentity() (*Identity, error) {
	data, err := os.ReadFile(a.privatePath("installation-key.json"))
	if err != nil {
		return nil, fmt.Errorf("identidade da instalação ausente: %w", err)
	}
	var id Identity
	if err := json.Unmarshal(data, &id); err != nil {
		return nil, err
	}
	if id.FormatVersion != 1 || id.InstallationID == "" || id.KeyID == "" || id.PublicKey == "" || id.PrivateKey == "" {
		return nil, errors.New("identidade da instalação inválida")
	}
	return &id, nil
}

func (a *Agent) ensureDirs() error {
	if err := os.MkdirAll(a.privateDir(), 0700); err != nil {
		return err
	}
	if err := os.Chmod(a.privateDir(), 0700); err != nil {
		return err
	}
	if err := os.MkdirAll(a.publicDir(), 0755); err != nil {
		return err
	}
	return nil
}
func (a *Agent) privateDir() string             { return filepath.Join(a.cfg.InstanceDir, "private") }
func (a *Agent) publicDir() string              { return filepath.Join(a.cfg.InstanceDir, "public") }
func (a *Agent) privatePath(name string) string { return filepath.Join(a.privateDir(), name) }
func (a *Agent) publicPath(name string) string  { return filepath.Join(a.publicDir(), name) }

func (a *Agent) readEnvelope(path string) (SignedEnvelope, error) {
	var e SignedEnvelope
	d, err := os.ReadFile(path)
	if err != nil {
		return e, err
	}
	err = json.Unmarshal(d, &e)
	return e, err
}
func (a *Agent) loadState() AgentState {
	var s AgentState
	d, err := os.ReadFile(a.publicPath("agent-state.json"))
	if err == nil {
		_ = json.Unmarshal(d, &s)
	}
	return s
}
func (a *Agent) saveState(s AgentState) error {
	return writeJSONAtomic(a.publicPath("agent-state.json"), s, 0644)
}
func (a *Agent) recordError(code string) {
	s := a.loadState()
	s.FormatVersion = 1
	s.LastErrorCode = code
	s.LastErrorAt = time.Now().UTC().Format(time.RFC3339Nano)
	_ = a.saveState(s)
}

func (a *Agent) printStatus(w io.Writer) error {
	id, err := a.loadIdentity()
	if err != nil {
		fmt.Fprintln(w, "status=NOT_ACTIVATED")
		return nil
	}
	fmt.Fprintln(w, "status=ACTIVATED")
	fmt.Fprintln(w, "installationId="+id.InstallationID)
	fmt.Fprintln(w, "keyId="+id.KeyID)
	if lease, err := a.readEnvelope(a.publicPath("lease.json")); err == nil {
		if p, err := a.verifyServerEnvelope(lease, "LICENSE_LEASE"); err == nil {
			fmt.Fprintln(w, "bindingId="+text(p, "bindingId"))
			fmt.Fprintln(w, "commercialStatus="+text(p, "commercialStatus"))
			fmt.Fprintln(w, "leaseValidUntil="+text(p, "leaseValidUntil"))
			fmt.Fprintln(w, "offlineGraceUntil="+text(p, "offlineGraceUntil"))
		}
	}
	state := a.loadState()
	if state.LastSuccessAt != "" {
		fmt.Fprintln(w, "lastSuccessAt="+state.LastSuccessAt)
	}
	if state.LastErrorCode != "" {
		fmt.Fprintln(w, "lastErrorCode="+state.LastErrorCode)
	}
	return nil
}

func collectBinding() (Binding, error) {
	osID, sysUUID, err := machineIDs()
	if err != nil {
		return Binding{}, err
	}
	return Binding{1, bindingHash("OS_ID", osID), bindingHash("SYSTEM_UUID", sysUUID)}, nil
}
func bindingHash(kind, raw string) string {
	normalized := strings.ToLower(strings.TrimSpace(raw))
	sum := sha256.Sum256([]byte(machineBindingDomain + "\x00" + kind + "\x00" + normalized))
	return hex.EncodeToString(sum[:])
}

func machineIDs() (string, string, error) {
	if runtime.GOOS == "linux" {
		a, err := os.ReadFile("/etc/machine-id")
		if err != nil {
			return "", "", fmt.Errorf("ler machine-id: %w", err)
		}
		b, err := os.ReadFile("/sys/class/dmi/id/product_uuid")
		if err != nil {
			return "", "", fmt.Errorf("ler product_uuid: %w", err)
		}
		if strings.TrimSpace(string(a)) == "" || strings.TrimSpace(string(b)) == "" {
			return "", "", errors.New("identificadores de máquina vazios")
		}
		return string(a), string(b), nil
	}
	if runtime.GOOS == "windows" {
		machineGuid, err := commandOutput("reg", "query", `HKLM\SOFTWARE\Microsoft\Cryptography`, "/v", "MachineGuid")
		if err != nil {
			return "", "", fmt.Errorf("ler MachineGuid: %w", err)
		}
		fields := strings.Fields(machineGuid)
		if len(fields) < 3 {
			return "", "", errors.New("MachineGuid não encontrado")
		}
		guid := fields[len(fields)-1]
		uuidOut, err := commandOutput("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "(Get-CimInstance Win32_ComputerSystemProduct).UUID")
		if err != nil {
			return "", "", fmt.Errorf("ler SMBIOS UUID: %w", err)
		}
		uuid := strings.TrimSpace(uuidOut)
		if uuid == "" {
			return "", "", errors.New("SMBIOS UUID vazio")
		}
		return guid, uuid, nil
	}
	return "", "", fmt.Errorf("sistema operacional não suportado: %s", runtime.GOOS)
}
func commandOutput(name string, args ...string) (string, error) {
	cmd := exec.Command(name, args...)
	cmd.Env = os.Environ()
	b, err := cmd.Output()
	return string(b), err
}

func writeJSONAtomic(path string, value any, mode os.FileMode) error {
	data, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return err
	}
	data = append(data, '\n')
	return writeAtomic(path, data, mode)
}
func writeAtomic(path string, data []byte, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return err
	}
	tmp, err := os.CreateTemp(filepath.Dir(path), "."+filepath.Base(path)+".*")
	if err != nil {
		return err
	}
	name := tmp.Name()
	defer os.Remove(name)
	if err := tmp.Chmod(mode); err != nil {
		tmp.Close()
		return err
	}
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Sync(); err != nil {
		tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	if err := os.Rename(name, path); err != nil {
		return err
	}
	return os.Chmod(path, mode)
}
func uuidV4() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
}
func b64(b []byte) string { return base64.RawURLEncoding.EncodeToString(b) }
func b64decode(s string) ([]byte, error) {
	if s == "" || strings.Contains(s, "=") {
		return nil, errors.New("Base64URL não canônico")
	}
	b, err := base64.RawURLEncoding.DecodeString(s)
	if err != nil {
		return nil, err
	}
	if b64(b) != s {
		return nil, errors.New("Base64URL não canônico")
	}
	return b, nil
}
func text(m map[string]any, key string) string {
	v, ok := m[key].(string)
	if !ok {
		return ""
	}
	return v
}
func intNumber(v any) int {
	switch n := v.(type) {
	case float64:
		return int(n)
	case int:
		return n
	case json.Number:
		i, _ := n.Int64()
		return int(i)
	}
	return 0
}
func apiError(status int, body []byte) error {
	var v struct {
		Error struct {
			Code    string `json:"code"`
			Message string `json:"message"`
		} `json:"error"`
	}
	if json.Unmarshal(body, &v) == nil && v.Error.Code != "" {
		return fmt.Errorf("License Server HTTP %d: %s - %s", status, v.Error.Code, v.Error.Message)
	}
	return fmt.Errorf("License Server HTTP %d", status)
}
