package net.ddns.lexdev.systempro_api.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import net.ddns.lexdev.systempro_api.service.ProductImageService;
import net.ddns.lexdev.systempro_api.service.ProductService;

class ProductControllerTest {

    private ProductService productService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        ProductImageService imageService = mock(ProductImageService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ProductController(productService, imageService))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void deveEncaminharPesquisaEPaginacaoAoService() throws Exception {
        when(productService.findAll(any(Pageable.class), eq("motor")))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 0));

        mockMvc.perform(get("/products")
                .param("q", "motor")
                .param("page", "2")
                .param("size", "10"))
            .andExpect(status().isOk());

        verify(productService).findAll(any(Pageable.class),eq("motor"),isNull());
    }

    @Test
    void deveUsarPesquisaVaziaPorPadrao() throws Exception {
        when(productService.findAll(any(Pageable.class), eq("")))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/products"))
            .andExpect(status().isOk());

        verify(productService).findAll(any(Pageable.class),eq(""),isNull());
    }
}