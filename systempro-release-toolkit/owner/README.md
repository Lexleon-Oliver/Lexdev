# SystemPro Release Toolkit - R2

Gera duas mídias a partir do projeto-fonte mantido somente pelo proprietário:

- `SystemPro-Install-<versão>.iso`: parceiro comercial; runtime, Host Agent compilado e manual.
- `SystemPro-Owner-Maintenance-<versão>.iso`: proprietário/suporte; inclui recovery/restore.

## Pré-requisitos do builder

- Docker + Docker Compose v2
- `sha256sum`
- `xorriso` (ou `genisoimage`/`mkisofs`)
- Go 1.23+ **ou** acesso para o Docker obter `golang:1.23-alpine`
- Root Public descriptor da Lexdev em `~/.lexdev/root/root-public.json`
  - outro caminho: `LEXDEV_ROOT_PUBLIC_FILE=/caminho/root-public.json`

A Root **Private** Key nunca é lida pelo builder e nunca entra em uma ISO.

O backend deve estar com o patch r2 de licenciamento aplicado antes do build.

Exemplo:

```bash
./systempro-release-toolkit/owner/build-release.sh 2026.10.26-r2
```
