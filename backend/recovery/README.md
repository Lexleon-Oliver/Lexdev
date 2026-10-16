# SystemPro Recovery Kit v1

O `.sprecovery` é o pacote de recuperação criptográfica do SystemPro.

Ele é separado do `.spbackup`:

```text
.spbackup   = PostgreSQL + product-images + supplier-documents
.sprecovery = segredos críticos + identidade técnica da instalação
```

O backup operacional comum nunca precisa conhecer a senha do Recovery Kit.

## Materiais v1

Obrigatório:

```text
FISCAL_ENCRYPTION_KEY
```

Opcional e automático:

```text
/data/systempro-instance/**
```

O volume `systempro_instance` já fica reservado no Compose. Enquanto o Host Agent ainda não tiver gravado a identidade da instalação, o Recovery Kit é válido e informa `installationFiles=0`. Quando a integração de licenciamento passar a usar esse volume, os arquivos regulares serão incluídos automaticamente, sem alterar o formato.

## Criptografia

Formato v1:

```text
KDF:    Argon2id
memory: 64 MiB
time:   3
parallelism: 2

Cipher: AES-256-GCM
salt:   16 bytes aleatórios
nonce:  12 bytes aleatórios
```

O cabeçalho é autenticado como AAD. Todo o payload, nomes dos materiais, identidade da instalação e segredos ficam dentro do ciphertext.

A senha de suporte:

- possui mínimo de 16 caracteres;
- é pedida via terminal;
- não é passada na linha de comando;
- não é variável de ambiente;
- não é gravada no kit;
- não é gravada pelo runner.

## Preparação

Na raiz do projeto:

```bash
chmod +x backend/recovery/systempro-recovery-compose.sh

docker compose --profile maintenance build recovery
```

## Conferir material atual sem exibir segredos

```bash
./backend/recovery/systempro-recovery-compose.sh current
```

Exemplo:

```text
CURRENT_RECOVERY_MATERIAL=OK
fiscalKeyFingerprint=4f81...
installationFiles=0
```

## Criar

Por padrão:

```bash
./backend/recovery/systempro-recovery-compose.sh create
```

O arquivo será criado em:

```text
./recovery-kits/systempro-recovery-YYYYMMDDTHHMMSSZ.sprecovery
```

O runner cria `recovery-kits/.gitignore` para impedir inclusão acidental dos kits no Git.

Também é possível gerar diretamente em mídia externa:

```bash
./backend/recovery/systempro-recovery-compose.sh \
  create /media/SEU_DISCO/SystemPro-Recovery
```

Depois de criado, copie o `.sprecovery` para um local externo ao servidor. O Recovery Kit não deve depender do mesmo disco do ERP.

## Verificar

```bash
./backend/recovery/systempro-recovery-compose.sh \
  verify recovery-kits/systempro-recovery-....sprecovery
```

Será pedida a senha. O comando descriptografa e valida:

- tag AES-GCM;
- cabeçalho;
- versão;
- kitId;
- SHA-256 de cada material;
- presença da `FISCAL_ENCRYPTION_KEY`.

Nenhum segredo é exibido.

## Teste de recuperação

Para provar que o kit realmente recupera o material:

```bash
mkdir -p /tmp/systempro-recovery-test

./backend/recovery/systempro-recovery-compose.sh \
  extract recovery-kits/systempro-recovery-....sprecovery \
  /tmp/systempro-recovery-test
```

O diretório conterá:

```text
environment/FISCAL_ENCRYPTION_KEY
systempro-instance/...          # quando existir
recovery-manifest.json
```

Todos os arquivos sensíveis são gravados com `0600`.

Ao terminar o teste:

```bash
rm -rf /tmp/systempro-recovery-test
```

## Procedimento em desastre

A ordem conceitual é:

```text
1. instalar Docker / projeto SystemPro
2. restaurar FISCAL_ENCRYPTION_KEY e identidade do .sprecovery
3. disponibilizar o .spbackup
4. executar Restore v1
5. iniciar backend
6. validar login, fiscal, arquivos e licenciamento
```

Não gere uma nova `FISCAL_ENCRYPTION_KEY` quando houver dados fiscais criptografados. Em desastre, recupere exatamente a chave presente no `.sprecovery`.
