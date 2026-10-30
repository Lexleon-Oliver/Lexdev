package net.ddns.lexdev.systempro_api.licensing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;

@Service
public class LicenseStateService {

    private static final String ROOT_RESOURCE = "licensing/root-public.json";
    private static final String CERT_PURPOSE = "SERVER_KEY_CERTIFICATE";
    private static final String LEASE_PURPOSE = "LICENSE_LEASE";
    private static final String STATUS_PURPOSE = "LICENSE_STATUS";
    private static final String SIGNED_OBJECT_DOMAIN = "SYSTEMPRO-SIGNED-OBJECT-V1";
    private static final byte[] ED25519_X509_PREFIX = new byte[] {
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
        0x70, 0x03, 0x21, 0x00
    };

    private final JsonMapper objectMapper;
    private final boolean enforcementEnabled;
    private final Path instanceDir;
    private final long cacheMillis;
    private final JsonNode rootDescriptorOverride;

    private volatile CachedDecision cached;

    @Autowired
    public LicenseStateService(
        JsonMapper objectMapper,
        @Value("${systempro.license.enforcement-enabled:false}") boolean enforcementEnabled,
        @Value("${systempro.license.instance-dir:/data/instance}") String instanceDir,
        @Value("${systempro.license.cache-millis:5000}") long cacheMillis
    ) {
        this(objectMapper, enforcementEnabled, Path.of(instanceDir), cacheMillis, null);
    }

    LicenseStateService(
        JsonMapper objectMapper,
        boolean enforcementEnabled,
        Path instanceDir,
        long cacheMillis,
        JsonNode rootDescriptorOverride
    ) {
        this.objectMapper = objectMapper;
        this.enforcementEnabled = enforcementEnabled;
        this.instanceDir = instanceDir.toAbsolutePath().normalize();
        this.cacheMillis = Math.max(0L, cacheMillis);
        this.rootDescriptorOverride = rootDescriptorOverride;
    }

    public LicenseDecision currentDecision() {
        if (!enforcementEnabled) {
            return new LicenseDecision(
                LicenseMode.DISABLED,
                "LICENSE_ENFORCEMENT_DISABLED",
                "Validação comercial desabilitada neste ambiente.",
                null,
                null,
                null,
                null
            );
        }

        long nowMillis = System.currentTimeMillis();
        CachedDecision current = cached;
        if (current != null && nowMillis - current.loadedAtMillis() <= cacheMillis) {
            return current.decision();
        }

        LicenseDecision loaded = evaluate(Instant.now());
        cached = new CachedDecision(nowMillis, loaded);
        return loaded;
    }

    LicenseDecision evaluate(Instant now) {
        try {
            JsonNode rootDescriptor = readRootDescriptor();
            if (rootDescriptor.path("formatVersion").asInt(-1) != 1) {
                throw new IllegalArgumentException("Root public descriptor version inválida.");
            }
            String rootKeyId = requiredText(rootDescriptor, "keyId");
            String rootPublicKey = requiredText(rootDescriptor, "publicKey");
            requireAlgorithm(requiredText(rootDescriptor, "algorithm"));

            JsonNode instance = readJson(instanceDir.resolve("instance.json"));
            String installationId = requiredText(instance, "installationId");

            JsonNode certificateEnvelope = readJson(instanceDir.resolve("server-key-certificate.json"));
            VerifiedPayload certificate = verifyEnvelope(
                certificateEnvelope,
                CERT_PURPOSE,
                rootKeyId,
                rootPublicKey
            );

            String serverKeyId = requiredText(certificate.payload(), "keyId");
            String serverPublicKey = requiredText(certificate.payload(), "publicKey");
            requireAlgorithm(certificate.payload().path("algorithm").asText("Ed25519"));
            validateCertificateWindow(certificate.payload(), now);

            Path statusPath = instanceDir.resolve("license-status.json");
            if (Files.isRegularFile(statusPath)) {
                JsonNode statusEnvelope = readJson(statusPath);
                VerifiedPayload status = verifyEnvelope(
                    statusEnvelope,
                    STATUS_PURPOSE,
                    serverKeyId,
                    serverPublicKey
                );
                LicenseDecision statusDecision = evaluateStatus(status.payload(), installationId, now);
                if (statusDecision != null) {
                    return statusDecision;
                }
            }

            JsonNode leaseEnvelope = readJson(instanceDir.resolve("lease.json"));
            VerifiedPayload lease = verifyEnvelope(
                leaseEnvelope,
                LEASE_PURPOSE,
                serverKeyId,
                serverPublicKey
            );

            return evaluateLease(lease.payload(), installationId, now);
        } catch (Exception exception) {
            return restricted(
                "LICENSE_INVALID_OR_MISSING",
                "Licença ausente, inválida ou não verificável.",
                null,
                null,
                null,
                null
            );
        }
    }

    private LicenseDecision evaluateStatus(JsonNode payload, String installationId, Instant now) {
        String statusInstallationId = requiredText(payload, "installationId");
        if (!Objects.equals(installationId, statusInstallationId)) {
            throw new IllegalArgumentException("Status pertence a outra instalação.");
        }

        String commercialStatus = payload.path("commercialStatus").asText("");
        String authorizationStatus = payload.path("authorizationStatus").asText("");
        String bindingId = nullableText(payload, "bindingId");

        if (!"ACTIVE".equals(commercialStatus)) {
            return restricted(
                "LICENSE_" + safeCode(commercialStatus, "NOT_ACTIVE"),
                "A licença comercial não está ativa.",
                installationId,
                bindingId,
                nullableInstant(payload, "validUntil"),
                null
            );
        }

        if (!authorizationStatus.isBlank()
            && !"AUTHORIZED".equals(authorizationStatus)
            && !"MATCH".equals(authorizationStatus)) {
            return restricted(
                safeCode(authorizationStatus, "LICENSE_RESTRICTED"),
                "A instalação requer regularização do licenciamento.",
                installationId,
                bindingId,
                nullableInstant(payload, "validUntil"),
                null
            );
        }

        Instant validUntil = nullableInstant(payload, "validUntil");
        if (validUntil != null && now.isAfter(validUntil)) {
            return restricted(
                "LICENSE_STATUS_EXPIRED",
                "O status assinado de licenciamento expirou.",
                installationId,
                bindingId,
                validUntil,
                null
            );
        }

        return null;
    }

    private LicenseDecision evaluateLease(JsonNode payload, String installationId, Instant now) {
        String leaseInstallationId = requiredText(payload, "installationId");
        if (!Objects.equals(installationId, leaseInstallationId)) {
            throw new IllegalArgumentException("Lease pertence a outra instalação.");
        }

        String bindingId = requiredText(payload, "bindingId");
        String commercialStatus = requiredText(payload, "commercialStatus");
        if (!"ACTIVE".equals(commercialStatus)) {
            return restricted(
                "LICENSE_" + safeCode(commercialStatus, "NOT_ACTIVE"),
                "A licença comercial não está ativa.",
                installationId,
                bindingId,
                null,
                null
            );
        }

        Instant leaseValidUntil = requiredInstant(payload, "leaseValidUntil");
        Instant offlineGraceUntil = requiredInstant(payload, "offlineGraceUntil");
        if (offlineGraceUntil.isBefore(leaseValidUntil)) {
            throw new IllegalArgumentException("Janela offline inválida.");
        }

        if (!now.isAfter(leaseValidUntil)) {
            return new LicenseDecision(
                LicenseMode.FULL,
                "LICENSE_FULL",
                "Licença válida.",
                installationId,
                bindingId,
                leaseValidUntil,
                offlineGraceUntil
            );
        }

        if (!now.isAfter(offlineGraceUntil)) {
            return new LicenseDecision(
                LicenseMode.GRACE,
                "LICENSE_GRACE",
                "Licença em período de tolerância offline.",
                installationId,
                bindingId,
                leaseValidUntil,
                offlineGraceUntil
            );
        }

        return restricted(
            "LICENSE_OFFLINE_GRACE_EXPIRED",
            "A tolerância offline terminou. Regularize a comunicação com o License Server.",
            installationId,
            bindingId,
            leaseValidUntil,
            offlineGraceUntil
        );
    }

    private JsonNode readRootDescriptor() throws IOException {
        if (rootDescriptorOverride != null) {
            return rootDescriptorOverride;
        }
        ClassPathResource resource = new ClassPathResource(ROOT_RESOURCE);
        if (!resource.exists()) {
            throw new IOException("Root public descriptor ausente.");
        }
        try (var input = resource.getInputStream()) {
            return objectMapper.readTree(input);
        }
    }

    private JsonNode readJson(Path path) throws IOException {
        if (!path.startsWith(instanceDir) || !Files.isRegularFile(path) || Files.isSymbolicLink(path)) {
            throw new IOException("Arquivo de licenciamento ausente ou inseguro: " + path.getFileName());
        }
        if (Files.size(path) > 1024 * 1024) {
            throw new IOException("Arquivo de licenciamento excede o limite.");
        }
        return objectMapper.readTree(Files.readAllBytes(path));
    }

    private VerifiedPayload verifyEnvelope(
        JsonNode envelope,
        String expectedPurpose,
        String expectedKeyId,
        String publicKeyBase64Url
    ) throws Exception {
        if (envelope.path("envelopeVersion").asInt(-1) != 1) {
            throw new IllegalArgumentException("Envelope version inválida.");
        }
        requireAlgorithm(requiredText(envelope, "algorithm"));
        String keyId = requiredText(envelope, "keyId");
        if (!Objects.equals(expectedKeyId, keyId)) {
            throw new IllegalArgumentException("Envelope assinado por chave inesperada.");
        }

        byte[] payloadBytes = decodeCanonicalBase64Url(requiredText(envelope, "payload"));
        byte[] signatureBytes = decodeCanonicalBase64Url(requiredText(envelope, "signature"));
        byte[] message = signedObjectMessage(expectedPurpose, payloadBytes);
        if (!verifyEd25519(publicKeyBase64Url, message, signatureBytes)) {
            throw new IllegalArgumentException("Assinatura inválida.");
        }

        JsonNode payload = objectMapper.readTree(payloadBytes);
        if (payload.path("protocolVersion").asInt(-1) != 1) {
            throw new IllegalArgumentException("Protocol version inválida.");
        }
        if (!expectedPurpose.equals(requiredText(payload, "purpose"))) {
            throw new IllegalArgumentException("Purpose inválido.");
        }
        return new VerifiedPayload(keyId, payload);
    }

    private static byte[] signedObjectMessage(String purpose, byte[] payloadBytes) {
        byte[] domain = SIGNED_OBJECT_DOMAIN.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] purposeBytes = purpose.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(domain.length + 1 + purposeBytes.length + 1 + payloadBytes.length);
        buffer.put(domain).put((byte) 0).put(purposeBytes).put((byte) 0).put(payloadBytes);
        return buffer.array();
    }

    private static boolean verifyEd25519(String publicKeyBase64Url, byte[] message, byte[] signatureBytes) throws Exception {
        byte[] rawKey = decodeCanonicalBase64Url(publicKeyBase64Url);
        if (rawKey.length != 32 || signatureBytes.length != 64) {
            return false;
        }
        byte[] x509 = new byte[ED25519_X509_PREFIX.length + rawKey.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, x509, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(rawKey, 0, x509, ED25519_X509_PREFIX.length, rawKey.length);
        PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(message);
        return verifier.verify(signatureBytes);
    }

    private static byte[] decodeCanonicalBase64Url(String value) {
        if (value == null || value.isBlank() || value.contains("=")) {
            throw new IllegalArgumentException("Base64URL não canônico.");
        }
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
        if (!encoded.equals(value)) {
            throw new IllegalArgumentException("Base64URL não canônico.");
        }
        return decoded;
    }

    private static void validateCertificateWindow(JsonNode payload, Instant now) {
        Instant notBefore = requiredInstant(payload, "notBefore");
        Instant notAfter = requiredInstant(payload, "notAfter");
        if (notAfter.isBefore(notBefore) || now.isBefore(notBefore) || now.isAfter(notAfter)) {
            throw new IllegalArgumentException("Server signing certificate fora da validade.");
        }
    }

    private static void requireAlgorithm(String algorithm) {
        if (!"Ed25519".equals(algorithm) && !"ED25519".equals(algorithm)) {
            throw new IllegalArgumentException("Algoritmo de assinatura não suportado.");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente: " + field);
        }
        return value;
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText("");
        return text.isBlank() ? null : text;
    }

    private static Instant requiredInstant(JsonNode node, String field) {
        Instant value = nullableInstant(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Timestamp obrigatório ausente: " + field);
        }
        return value;
    }

    private static Instant nullableInstant(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Timestamp inválido: " + field, exception);
        }
    }

    private static String safeCode(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replaceAll("[^A-Za-z0-9_]+", "_").toUpperCase(java.util.Locale.ROOT);
    }

    private static LicenseDecision restricted(
        String code,
        String message,
        String installationId,
        String bindingId,
        Instant leaseValidUntil,
        Instant offlineGraceUntil
    ) {
        return new LicenseDecision(
            LicenseMode.RESTRICTED,
            code,
            message,
            installationId,
            bindingId,
            leaseValidUntil,
            offlineGraceUntil
        );
    }

    private record CachedDecision(long loadedAtMillis, LicenseDecision decision) {
    }

    private record VerifiedPayload(String keyId, JsonNode payload) {
    }
}
