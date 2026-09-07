package net.ddns.lexdev.systempro_api.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping
    public Map<String, String> ping() {
        // Retorna um JSON simples para provar que a API está viva
        return Map.of(
                "status", "Sucesso",
                "message", "A API do SystemPro está rodando e o Proxy Nginx roteou perfeitamente!",
                "version", "1.0"
        );
    }
}