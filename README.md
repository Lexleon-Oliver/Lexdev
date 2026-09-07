# SystemPro Workspace

Arquitetura completa com Angular, Spring Boot, PostgreSQL e Nginx (Proxy/Load Balancer).

## Pré-requisitos
- Docker e Docker Compose
- Node.js 24+ e Angular CLI 22+ (para desenvolvimento local)
- Java 26 e Maven 3.9+ (para desenvolvimento local)

## Como executar
1. Crie uma cópia do arquivo `.env.example` e renomeie para `.env`.
2. Preencha as variáveis de ambiente necessárias.
3. Execute o comando:
   ```bash
   docker-compose up --build -d
