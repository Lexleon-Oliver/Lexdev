package net.ddns.lexdev.systempro_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;
import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.dto.ProductFiscalProfileRequestDto;
import net.ddns.lexdev.systempro_api.dto.ProductFiscalProfileResponseDto;
import net.ddns.lexdev.systempro_api.repository.FiscalProductProfileRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.fiscal.validation.IbsCbsClassificationValidator;

@Service
public class ProductFiscalProfileService {
    private final FiscalProductProfileRepository repository;
    private final ProductRepository productRepository;

    public ProductFiscalProfileService(FiscalProductProfileRepository repository, ProductRepository productRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public ProductFiscalProfileResponseDto find(Long productId) {
        return repository.findByProductId(productId).map(ProductFiscalProfileResponseDto::fromEntity)
            .orElseThrow(() -> new EntityNotFoundException("Perfil fiscal não configurado para o produto."));
    }

    @Transactional
    public ProductFiscalProfileResponseDto save(Long productId, ProductFiscalProfileRequestDto dto) {
        IbsCbsClassificationValidator.validate(dto.ibsCbsCst(), dto.cClassTrib());
        Product product = productRepository.findById(productId).orElseThrow(() -> new EntityNotFoundException("Produto não encontrado."));
        FiscalProductProfile profile = repository.findByProductId(productId).orElseGet(() -> new FiscalProductProfile(product));
        profile.setCfop(dto.cfop());
        profile.setIcmsCstCsosn(dto.icmsCstCsosn());
        profile.setPisCst(dto.pisCst());
        profile.setCofinsCst(dto.cofinsCst());
        profile.setIcmsRate(dto.icmsRate());
        profile.setPisRate(dto.pisRate());
        profile.setCofinsRate(dto.cofinsRate());
        profile.setIbsCbsCst(dto.ibsCbsCst());
        profile.setCClassTrib(dto.cClassTrib());
        profile.setIbsRate(dto.ibsRate());
        profile.setCbsRate(dto.cbsRate());
        profile.setAdditionalInformation(dto.additionalInformation());
        profile.setActive(true);
        return ProductFiscalProfileResponseDto.fromEntity(repository.save(profile));
    }
}