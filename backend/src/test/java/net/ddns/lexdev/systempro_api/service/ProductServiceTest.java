package net.ddns.lexdev.systempro_api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SupplierRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock SupplierRepository supplierRepository;
    @InjectMocks ProductService service;

    @Test
    void deveListarPaginaSemPesquisa() {
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findAllProducts(pageable))
            .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.findAll(pageable, "   ");

        verify(productRepository).findAllProducts(pageable);
        verify(productRepository, never()).search(any(), any());
    }

    @Test
    void devePesquisarNoBancoComTermoNormalizado() {
        Pageable pageable = PageRequest.of(2, 10);
        when(productRepository.search(eq("motor"), eq(pageable)))
            .thenReturn(new PageImpl<Product>(List.of(), pageable, 0));

        service.findAll(pageable, "  motor  ");

        verify(productRepository).search("motor", pageable);
        verify(productRepository, never()).findAllProducts(any());
    }
}