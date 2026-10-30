package main

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/json"
	"testing"
)

func TestBindingHashIsStableAndSeparated(t *testing.T) {
	a := bindingHash("OS_ID", " ABC-123 \n")
	b := bindingHash("OS_ID", "abc-123")
	c := bindingHash("SYSTEM_UUID", "abc-123")
	if a != b {
		t.Fatalf("normalização deve ser estável: %s != %s", a, b)
	}
	if a == c {
		t.Fatal("domain separation entre sinais deve produzir hashes diferentes")
	}
	if len(a) != 64 {
		t.Fatalf("SHA-256 hex deve ter 64 chars, recebeu %d", len(a))
	}
}

func TestVerifyEnvelopeEd25519(t *testing.T) {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	payload := map[string]any{
		"protocolVersion": float64(1),
		"purpose":         "LICENSE_LEASE",
		"installationId":  "11111111-2222-4333-8444-555555555555",
	}
	payloadBytes, _ := json.Marshal(payload)
	message := append([]byte(signedObjectDomain+"\x00LICENSE_LEASE\x00"), payloadBytes...)
	env := SignedEnvelope{
		EnvelopeVersion: 1,
		KeyID:           "server-test",
		Algorithm:       "Ed25519",
		Payload:         b64(payloadBytes),
		Signature:       b64(ed25519.Sign(priv, message)),
	}
	verified, err := verifyEnvelope(env, "LICENSE_LEASE", "server-test", b64(pub))
	if err != nil {
		t.Fatal(err)
	}
	if text(verified, "installationId") == "" {
		t.Fatal("payload esperado não retornado")
	}

	env.Signature = env.Signature[:len(env.Signature)-2] + "AA"
	if _, err := verifyEnvelope(env, "LICENSE_LEASE", "server-test", b64(pub)); err == nil {
		t.Fatal("assinatura adulterada deveria falhar")
	}
}

func TestStableJitterWithinBounds(t *testing.T) {
	for _, purpose := range []string{"heartbeat", "renewal"} {
		got := stableJitterSeconds("11111111-2222-4333-8444-555555555555", purpose, 900)
		if got < -900 || got > 900 {
			t.Fatalf("jitter fora do limite: %d", got)
		}
	}
}
