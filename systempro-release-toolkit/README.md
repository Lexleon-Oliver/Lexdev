# SystemPro Release Toolkit - R2

Toolkit de distribuição sem projeto-fonte, com licenciamento online/offline por lease assinado.

- ISO comercial: runtime Docker + Host Agent compilado + Root Public Key + manual.
- ISO do proprietário: tudo da comercial + Recovery/Restore.
- Código-fonte do backend/frontend/Host Agent permanece somente com o proprietário.
- `.sprecovery` permanece fora das ISOs.

A r2 exige o patch de backend de licenciamento e o Root Public descriptor da Lexdev no build owner-side.
