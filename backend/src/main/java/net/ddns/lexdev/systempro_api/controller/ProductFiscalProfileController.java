package net.ddns.lexdev.systempro_api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.ProductFiscalProfileRequestDto;
import net.ddns.lexdev.systempro_api.dto.ProductFiscalProfileResponseDto;
import net.ddns.lexdev.systempro_api.service.ProductFiscalProfileService;

@RestController
@RequestMapping("/products/{productId}/fiscal-profile")
public class ProductFiscalProfileController {
    private final ProductFiscalProfileService service;
    public ProductFiscalProfileController(ProductFiscalProfileService service){this.service=service;}
    @GetMapping public ProductFiscalProfileResponseDto find(@PathVariable Long productId){return service.find(productId);}
    @PutMapping public ProductFiscalProfileResponseDto save(@PathVariable Long productId,@Valid @RequestBody ProductFiscalProfileRequestDto dto){return service.save(productId,dto);}
}
