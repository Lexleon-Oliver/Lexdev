package net.ddns.lexdev.systempro_api.licensing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseStateServiceTest {

    private static final String DOMAIN = "SYSTEMPRO-SIGNED-OBJECT-V1";
    private final JsonMapper mapper = JsonMapper.builder().build();

    @TempDir
    Path tempDir;

    @Test
    void validSignedLeaseIsFull() throws Exception {
        Fixture fixture = writeFixture(
            Instant.now().plus(2, ChronoUnit.DAYS),
            Instant.now().plus(9, ChronoUnit.DAYS)
        );
        LicenseStateService service = new LicenseStateService(
            mapper, true, tempDir, 0, fixture.rootDescriptor()
        );

        LicenseDecision decision = service.evaluate(Instant.now());

        assertThat(decision.mode()).isEqualTo(LicenseMode.FULL);
        assertThat(decision.installationId()).isEqualTo(fixture.installationId());
        assertThat(decision.bindingId()).isEqualTo(fixture.bindingId());
    }

    @Test
    void expiredLeaseInsideOfflineWindowIsGrace() throws Exception {
        Fixture fixture = writeFixture(
            Instant.now().minus(1, ChronoUnit.HOURS),
            Instant.now().plus(2, ChronoUnit.DAYS)
        );
        LicenseStateService service = new LicenseStateService(
            mapper, true, tempDir, 0, fixture.rootDescriptor()
        );

        assertThat(service.evaluate(Instant.now()).mode()).isEqualTo(LicenseMode.GRACE);
    }

    @Test
    void tamperedLeaseFailsClosed() throws Exception {
        Fixture fixture = writeFixture(
            Instant.now().plus(2, ChronoUnit.DAYS),
            Instant.now().plus(9, ChronoUnit.DAYS)
        );
        ObjectNode lease = (ObjectNode) mapper.readTree(Files.readAllBytes(tempDir.resolve("lease.json")));
        lease.put("signature", lease.get("signature").asText().substring(0, 80) + "AA");
        Files.write(tempDir.resolve("lease.json"), mapper.writeValueAsBytes(lease));

        LicenseStateService service = new LicenseStateService(
            mapper, true, tempDir, 0, fixture.rootDescriptor()
        );

        assertThat(service.evaluate(Instant.now()).mode()).isEqualTo(LicenseMode.RESTRICTED);
    }

    private Fixture writeFixture(Instant leaseUntil, Instant graceUntil) throws Exception {
        KeyPair root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair server = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String rootKeyId = "lexdev-root-test";
        String serverKeyId = "server-test";
        String installationId = "11111111-2222-4333-8444-555555555555";
        String bindingId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee";

        ObjectNode rootDescriptor = mapper.createObjectNode();
        rootDescriptor.put("formatVersion", 1);
        rootDescriptor.put("keyId", rootKeyId);
        rootDescriptor.put("algorithm", "Ed25519");
        rootDescriptor.put("publicKey", rawPublic(root));

        ObjectNode instance = mapper.createObjectNode();
        instance.put("formatVersion", 1);
        instance.put("installationId", installationId);
        instance.put("keyId", "installation-test");
        Files.write(tempDir.resolve("instance.json"), mapper.writeValueAsBytes(instance));

        ObjectNode certPayload = mapper.createObjectNode();
        certPayload.put("algorithm", "Ed25519");
        certPayload.put("keyId", serverKeyId);
        certPayload.put("notAfter", Instant.now().plus(30, ChronoUnit.DAYS).toString());
        certPayload.put("notBefore", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        certPayload.put("protocolVersion", 1);
        certPayload.put("publicKey", rawPublic(server));
        certPayload.put("purpose", "SERVER_KEY_CERTIFICATE");
        Files.write(
            tempDir.resolve("server-key-certificate.json"),
            mapper.writeValueAsBytes(signEnvelope(rootKeyId, root, "SERVER_KEY_CERTIFICATE", certPayload))
        );

        ObjectNode leasePayload = mapper.createObjectNode();
        leasePayload.put("bindingId", bindingId);
        leasePayload.put("commercialStatus", "ACTIVE");
        leasePayload.put("installationId", installationId);
        leasePayload.put("issuedAt", Instant.now().minus(1, ChronoUnit.MINUTES).toString());
        leasePayload.put("leaseId", "bbbbbbbb-cccc-4ddd-8eee-ffffffffffff");
        leasePayload.put("leaseValidUntil", leaseUntil.toString());
        leasePayload.put("offlineGraceUntil", graceUntil.toString());
        leasePayload.put("protocolVersion", 1);
        leasePayload.put("purpose", "LICENSE_LEASE");
        Files.write(
            tempDir.resolve("lease.json"),
            mapper.writeValueAsBytes(signEnvelope(serverKeyId, server, "LICENSE_LEASE", leasePayload))
        );

        return new Fixture(rootDescriptor, installationId, bindingId);
    }

    private ObjectNode signEnvelope(String keyId, KeyPair pair, String purpose, JsonNode payload) throws Exception {
        byte[] payloadBytes = mapper.writeValueAsBytes(payload);
        byte[] domain = DOMAIN.getBytes(StandardCharsets.UTF_8);
        byte[] purposeBytes = purpose.getBytes(StandardCharsets.UTF_8);
        ByteBuffer message = ByteBuffer.allocate(domain.length + 1 + purposeBytes.length + 1 + payloadBytes.length);
        message.put(domain).put((byte) 0).put(purposeBytes).put((byte) 0).put(payloadBytes);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(message.array());

        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("envelopeVersion", 1);
        envelope.put("keyId", keyId);
        envelope.put("algorithm", "Ed25519");
        envelope.put("payload", b64(payloadBytes));
        envelope.put("signature", b64(signer.sign()));
        return envelope;
    }

    private static String rawPublic(KeyPair pair) {
        byte[] encoded = pair.getPublic().getEncoded();
        byte[] raw = java.util.Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
        return b64(raw);
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Fixture(JsonNode rootDescriptor, String installationId, String bindingId) {
    }
}
