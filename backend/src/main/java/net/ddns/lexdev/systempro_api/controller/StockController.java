package net.ddns.lexdev.systempro_api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import net.ddns.lexdev.systempro_api.dto.stock.StockBalanceResponseDto;
import net.ddns.lexdev.systempro_api.dto.stock.StockMovementResponseDto;
import net.ddns.lexdev.systempro_api.dto.stock.StockOperationRequestDto;
import net.ddns.lexdev.systempro_api.service.StockService;

@RestController
@RequestMapping("/stock/products/{productId}")
public class StockController {

    private final StockService service;

    public StockController(StockService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<StockBalanceResponseDto> balance(@PathVariable Long productId) {
        return ResponseEntity.ok(service.stockBalance(productId));
    }

    @GetMapping("/movements")
    public ResponseEntity<Page<StockMovementResponseDto>> movements(
        @PathVariable Long productId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(service.movements(productId, pageable));
    }

    @PostMapping("/initial-balance")
    public ResponseEntity<StockMovementResponseDto> initializeBalance(
        @PathVariable Long productId,
        @Valid @RequestBody StockOperationRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            service.initializeBalanceResponse(
                productId, request.quantity(), request.reason(), request.operationReference()
            )
        );
    }

    @PostMapping("/adjustments/positive")
    public ResponseEntity<StockMovementResponseDto> adjustPositive(
        @PathVariable Long productId,
        @Valid @RequestBody StockOperationRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            service.adjustPositiveResponse(
                productId, request.quantity(), request.reason(), request.operationReference()
            )
        );
    }

    @PostMapping("/adjustments/negative")
    public ResponseEntity<StockMovementResponseDto> adjustNegative(
        @PathVariable Long productId,
        @Valid @RequestBody StockOperationRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            service.adjustNegativeResponse(
                productId, request.quantity(), request.reason(), request.operationReference()
            )
        );
    }
}