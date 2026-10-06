package net.ddns.lexdev.systempro_api.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.EntityNotFoundException;

import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.LegalEntity;
import net.ddns.lexdev.systempro_api.domain.Person;
import net.ddns.lexdev.systempro_api.dto.FiscalEstablishmentRequestDto;
import net.ddns.lexdev.systempro_api.dto.FiscalEstablishmentResponseDto;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.PersonRepository;
import net.ddns.lexdev.systempro_api.storage.FileStorageService;

@Service
public class FiscalEstablishmentService {
    private final FiscalEstablishmentRepository repository;
    private final PersonRepository personRepository;
    private final SecretCryptoService crypto;
    private final FileStorageService storage;
    private final FiscalProperties fiscalProperties;
    private final net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository fiscalDocumentRepository;

    public FiscalEstablishmentService(FiscalEstablishmentRepository repository, PersonRepository personRepository,
                                      SecretCryptoService crypto, FileStorageService storage, FiscalProperties fiscalProperties,
                                      net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository fiscalDocumentRepository) {
        this.repository = repository;
        this.personRepository = personRepository;
        this.crypto = crypto;
        this.storage = storage;
        this.fiscalProperties = fiscalProperties;
        this.fiscalDocumentRepository = fiscalDocumentRepository;
    }

    @Transactional(readOnly = true)
    public List<FiscalEstablishmentResponseDto> findAll() {
        return repository.findAll().stream().map(this::response).toList();
    }

    @Transactional
    public FiscalEstablishmentResponseDto create(FiscalEstablishmentRequestDto dto) {
        if (repository.existsByPersonId(dto.personId())) {
            throw new FiscalConfigurationException("A pessoa informada já possui um estabelecimento fiscal.");
        }
        Person person = personRepository.findById(dto.personId()).orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada."));
        validateIssuer(person);
        FiscalEstablishment e = new FiscalEstablishment(person);
        apply(e, dto);
        return response(repository.save(e));
    }

    @Transactional
    public FiscalEstablishmentResponseDto update(Long id, FiscalEstablishmentRequestDto dto) {
        FiscalEstablishment e = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Estabelecimento fiscal não encontrado."));
        Person person = personRepository.findById(dto.personId()).orElseThrow(() -> new EntityNotFoundException("Pessoa não encontrada."));
        validateIssuer(person);
        if (!person.getId().equals(e.getPerson().getId()) && repository.existsByPersonId(dto.personId())) {
            throw new FiscalConfigurationException("A pessoa informada já possui outro estabelecimento fiscal.");
        }
        e.setPerson(person);
        apply(e, dto);
        return response(e);
    }

    @Transactional
    public void uploadCertificate(Long id, MultipartFile file, String password) {
        if (file == null || file.isEmpty()) throw new FiscalConfigurationException("O certificado A1 é obrigatório.");
        if (password == null || password.isBlank()) throw new FiscalConfigurationException("A senha do certificado A1 é obrigatória.");
        FiscalEstablishment e = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Estabelecimento fiscal não encontrado."));
        String key = "fiscal-certificates/establishments/%d.p12".formatted(id);
        java.nio.file.Path temp = null;
        boolean stored = false;
        try {
            temp = java.nio.file.Files.createTempFile("systempro-fiscal-", ".p12");
            java.nio.file.Files.copy(file.getInputStream(), temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            FiscalCertificateValidator.validate(temp, password);
            storage.upload(key, temp, "application/x-pkcs12");
            stored = true;
            e.setCertificateStorageKey(key);
            e.setEncryptedCertificatePassword(crypto.encrypt(password));
        } catch (FiscalConfigurationException ex) {
            if (stored) deleteStoredCertificate(key, ex);
            throw ex;
        } catch (Exception ex) {
            if (stored) deleteStoredCertificate(key, ex);
            throw new FiscalConfigurationException("Não foi possível validar e armazenar o certificado A1.");
        } finally {
            if (temp != null) try { java.nio.file.Files.deleteIfExists(temp); } catch (Exception ignored) {}
        }
    }

    private void deleteStoredCertificate(String key, Exception original) {
        try {
            storage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure);
        }
    }

    @Transactional(readOnly = true)
    public FiscalEstablishment requireDetailed(Long id) {
        return repository.findDetailed(id).orElseThrow(() -> new EntityNotFoundException("Estabelecimento fiscal não encontrado."));
    }

    @Transactional(readOnly = true)
    public FiscalEstablishment requireForUpdate(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new EntityNotFoundException("Estabelecimento fiscal não encontrado."));
    }

    public String decryptCertificatePassword(FiscalEstablishment establishment) {
        return crypto.decrypt(establishment.getEncryptedCertificatePassword());
    }

    public String decryptCsc(FiscalEstablishment establishment) {
        return crypto.decrypt(establishment.getEncryptedCsc());
    }

    public void assertReadyForEmission(FiscalEstablishment e) {
        if (!e.isActive()) throw new FiscalConfigurationException("O estabelecimento fiscal está inativo.");
        if (e.getEnvironment() == net.ddns.lexdev.systempro_api.enums.FiscalEnvironment.PRODUCAO
                && !fiscalProperties.productionReady()) {
            throw new FiscalConfigurationException(
                "Emissão fiscal em PRODUÇÃO está bloqueada até a validação formal do emissor NFC-e e seus artefatos técnicos."
            );
        }
        if (e.getEnvironment() == null) throw new FiscalConfigurationException("Ambiente fiscal não configurado.");
        if (e.getPerson() == null || e.getPerson().getTipoPessoa() != TipoPessoa.PJ) throw new FiscalConfigurationException("O emitente fiscal precisa ser uma pessoa jurídica.");
        LegalEntity legal = e.getPerson().getLegalEntity();
        if (legal == null || blank(legal.getInscricaoEstadual())) throw new FiscalConfigurationException("A Inscrição Estadual do emitente precisa estar cadastrada.");
        if (blank(e.getMunicipalityIbgeCode())) throw new FiscalConfigurationException("O código IBGE do município do estabelecimento fiscal é obrigatório.");
        if (e.getCertificateStorageKey() == null || e.getEncryptedCertificatePassword() == null) throw new FiscalConfigurationException("O certificado A1 ainda não foi configurado.");
        if (e.getCscId() == null || blank(e.getEncryptedCsc())) throw new FiscalConfigurationException("O CSC ainda não foi configurado.");
    }

    private void validateIssuer(Person person) {
        if (person.getTipoPessoa() != TipoPessoa.PJ) throw new FiscalConfigurationException("O estabelecimento emissor precisa estar vinculado a uma pessoa jurídica.");
        if (person.getCpfCnpj() == null || !person.getCpfCnpj().matches("\\d{14}")) throw new FiscalConfigurationException("O CNPJ do emitente deve possuir 14 dígitos.");
    }

    private void apply(FiscalEstablishment e, FiscalEstablishmentRequestDto dto) {
        e.setMunicipalityIbgeCode(dto.municipalityIbgeCode());
        e.setTaxRegime(dto.taxRegime());
        e.setEnvironment(dto.environment());
        int series = dto.series();
        long nextNumber = dto.nextNumber();
        if (e.getId() != null) {
            long lastIssued = fiscalDocumentRepository.findMaxNumber(e.getId(), series);
            if (nextNumber <= lastIssued) {
                throw new FiscalConfigurationException(
                    "O próximo número da série " + series + " deve ser maior que o último número fiscal já utilizado (" + lastIssued + ")."
                );
            }
        }
        e.setSeries(series);
        e.setNextNumber(nextNumber);
        if (dto.cscId() != null) e.setCscId(dto.cscId());
        if (dto.csc() != null && !dto.csc().isBlank()) e.setEncryptedCsc(crypto.encrypt(dto.csc()));
        if (dto.certificatePassword() != null && !dto.certificatePassword().isBlank()) e.setEncryptedCertificatePassword(crypto.encrypt(dto.certificatePassword()));
    }

    private FiscalEstablishmentResponseDto response(FiscalEstablishment e) {
        return FiscalEstablishmentResponseDto.fromEntity(e);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    static class FiscalCertificateValidator {
        static void validate(java.nio.file.Path path, String password) {
            try (java.io.InputStream input = java.nio.file.Files.newInputStream(path)) {
                java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
                ks.load(input, password.toCharArray());
                String alias = ks.aliases().nextElement();
                if (!ks.isKeyEntry(alias)) throw new FiscalConfigurationException("O arquivo informado não contém uma chave privada válida.");
                var cert = ks.getCertificate(alias);
                if (!(cert instanceof java.security.cert.X509Certificate x509)) {
                    throw new FiscalConfigurationException("O certificado A1 informado não é um certificado X.509.");
                }
                x509.checkValidity();
            } catch (FiscalConfigurationException ex) { throw ex; }
            catch (Exception ex) { throw new FiscalConfigurationException("O certificado A1 não pôde ser aberto com a senha informada."); }
        }
    }
}