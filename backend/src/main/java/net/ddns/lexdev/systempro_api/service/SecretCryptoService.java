package net.ddns.lexdev.systempro_api.service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;

@Service
public class SecretCryptoService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private final FiscalProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SecretCryptoService(FiscalProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new FiscalConfigurationException("Não foi possível proteger um segredo fiscal.");
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new FiscalConfigurationException("Não foi possível recuperar a configuração fiscal protegida.");
        }
    }

    private SecretKeySpec key() {
        String secret = properties.encryptionKey();
        if (secret == null || secret.isBlank()) {
            throw new FiscalConfigurationException("FISCAL_ENCRYPTION_KEY deve ser configurada para utilizar dados fiscais sensíveis.");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new FiscalConfigurationException("Não foi possível inicializar a proteção dos segredos fiscais.");
        }
    }
}
