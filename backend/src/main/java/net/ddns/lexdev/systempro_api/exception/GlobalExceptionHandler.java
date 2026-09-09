package net.ddns.lexdev.systempro_api.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import net.ddns.lexdev.systempro_api.dto.ApiErrorResponseDto;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Recurso Não Encontrado (404)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponseDto> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Recurso Não Encontrado", ex.getMessage(), request.getRequestURI(), null);
    }

    // 2. Erros de Validação do DTO com @Valid (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorResponseDto.FieldErrorDetails> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorResponseDto.FieldErrorDetails(error.getField(), error.getDefaultMessage()))
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "Erro de Validação", "Um ou mais campos estão inválidos", request.getRequestURI(), fieldErrors);
    }

    // 3. Credenciais Inválidas / Login (401)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponseDto> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Não Autorizado", "Usuário ou senha inválidos", request.getRequestURI(), null);
    }

    // 4. Acesso Negado / Sem Permissão (403)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponseDto> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Acesso Negado", "Você não tem permissão para acessar este recurso", request.getRequestURI(), null);
    }

    // 5. Erro Genérico / Inesperado (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> handleGenericException(Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro Interno do Servidor", "Ocorreu um erro inesperado no sistema", request.getRequestURI(), null);
    }

    // 6. Regras de Negócio / Erros de Requisição (400)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(
            HttpStatus.BAD_REQUEST, 
            "Regra de Negócio", 
            ex.getMessage(), 
            request.getRequestURI(), 
            null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponseDto> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, 
            HttpServletRequest request) {

        String message = "Usuário ou e-mail já cadastrado no sistema.";

        // Identifica se a violação veio do e-mail ou do username
        String detailMessage = ex.getMostSpecificCause().getMessage();
        if (detailMessage != null) {
            if (detailMessage.contains("email")) {
                message = "O e-mail informado já está em uso.";
            } else if (detailMessage.contains("username")) {
                message = "O nome de usuário informado já está em uso.";
            }
        }

        return buildResponse(
            HttpStatus.BAD_REQUEST, // Retorna 400 em vez de 500
            "Conflito de Dados", 
            message, 
            request.getRequestURI(), 
            null
        );
    }

    private ResponseEntity<ApiErrorResponseDto> buildResponse(HttpStatus status, String error, String message, String path, List<ApiErrorResponseDto.FieldErrorDetails> fieldErrors) {
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                Instant.now(),
                status.value(),
                error,
                message,
                path,
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }
}
