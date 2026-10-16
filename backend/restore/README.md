# SystemPro Restore v1

Restore completo e independente do backend/JPA para pacotes `.spbackup` do SystemPro.

## Formato suportado

```text
manifest.json
database/systempro.dump
storage/product-images/**
storage/supplier-documents/**
checksums.sha256
```

O formato v1 aceita campos extras de manifests antigos, inclusive `completedAt`.

## Integração deste projeto

O `docker-compose.yml` contém um serviço `restore` no profile `maintenance`.

Ele usa exatamente os mesmos volumes do backend:

```text
systempro_product_images  -> /data/product-images
systempro_supplier_documents -> /data/supplier-documents
systempro_backups -> /data/backups (somente leitura no restore)
systempro_restore_work -> /restore-work
```

O volume `systempro_restore_work` é exclusivo dos journals e artefatos de rollback e não deve ser limpo antes da validação funcional.

O container de restore recebe `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` e `DB_PASSWORD` pelas mesmas variáveis do Compose. Nenhum secret é gravado no journal.

## Garantias

Antes da mutação, a ferramenta:

1. valida a estrutura ZIP e bloqueia path traversal/symlinks;
2. valida `formatVersion=1`;
3. verifica integralmente `checksums.sha256`;
4. valida o dump com `pg_restore --list`;
5. restaura o dump em banco temporário de prova;
6. exige zero conexões ativas no banco alvo;
7. cria dump do banco atual;
8. cria snapshots dos dois storages.

Depois disso, substitui banco e storages. Se falhar após o início da mutação, tenta rollback automático completo.

O backend roda como UID/GID `100:101`; o restore devolve os storages para esse mesmo ownership.

## Primeira preparação

Na raiz do projeto:

```bash
chmod +x backend/restore/systempro-restore-compose.sh

docker compose --profile maintenance build restore

python3 -m unittest discover -s backend/restore/tests -v
```

## Listar backups

```bash
./backend/restore/systempro-restore-compose.sh list
```

## Inspecionar um backup sem tocar no PostgreSQL

```bash
./backend/restore/systempro-restore-compose.sh \
  inspect systempro-backup-YYYYMMDDTHHMMSSZ.spbackup
```

A saída deve terminar com:

```json
"integrity": "OK"
```

## Restore completo

```bash
./backend/restore/systempro-restore-compose.sh \
  restore systempro-backup-YYYYMMDDTHHMMSSZ.spbackup
```

O runner:

- inspeciona o pacote antes do downtime;
- mostra o `DB_NAME` resolvido pelo Compose;
- exige que o operador digite o nome exato;
- executa `docker compose stop backend`;
- executa o restore;
- mantém o backend parado se houver qualquer falha;
- executa `docker compose up -d backend` somente após `RESTORE=OK`.

O `db`, `frontend`, `proxy` e `clamav` não são parados. Somente o backend acessa o PostgreSQL no compose atual.

## Operações preservadas

```bash
./backend/restore/systempro-restore-compose.sh operations
```

Exemplo:

```text
restore-20261016T030000Z-a1b2c3d4  COMPLETE
```

## Rollback manual posterior

Se o restore terminou, mas a validação funcional apresentou problema:

```bash
./backend/restore/systempro-restore-compose.sh \
  rollback restore-20261016T030000Z-a1b2c3d4
```

O backend será parado, o estado pré-restore será recuperado e o backend só será iniciado de novo se o rollback terminar com sucesso.

## Validação funcional obrigatória

Depois de `RESTORE_RUNNER=OK`, valide antes de limpar qualquer rollback:

- login;
- usuários/perfis/permissões;
- clientes/fornecedores;
- produtos e estoque;
- vendas relevantes;
- imagens de produtos;
- documentos de fornecedores;
- configuração fiscal já existente.

O `.spbackup` comum **não contém** secrets como `FISCAL_ENCRYPTION_KEY`. A preservação desses segredos pertence ao próximo estágio: Recovery Kit `.sprecovery`.
