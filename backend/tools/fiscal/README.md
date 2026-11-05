# SystemPro — preparação segura do PL_010g (etapa 1)

Este utilitário permite **importar e auditar localmente** o pacote `PL_010g v1.03` do Portal Nacional da NF-e. Ele **não modifica o layout XML, não habilita emissão fiscal, não altera a versão padrão** e **não substitui o PL_010f**. Sua finalidade é criar uma base reproduzível para a etapa de desenvolvimento das NTs de 2026.

## Fonte e ressalvas

- Portal Nacional da NF-e → Documentos → Esquemas XML: https://www.nfe.fazenda.gov.br/portal/listaConteudo.aspx?tipoConteudo=BMPFMBoln3w%3D
- Referência de publicação: `PL_010g v1.03` (26/10/2026), NTs 2026.006 e 2026.008, versões de publicação 1.00.
- As NTs 2026.006 v1.01, 2026.008 v1.01, 2025.002 v1.53 e 2026.005 v1.00 foram publicadas posteriormente (30/10/2026). **Não presumir** que o PL_010g contém todas as alterações de regras de negócio.
- Um SHA-256 detecta alteração dos bytes, mas **não autentica sozinho a origem**. Baixe o ZIP por link do Portal Nacional e registre a URL, data e hash obtidos; não use mirror como fonte única de verdade.

## Execução no Linux de desenvolvimento

1. Baixe `PL_010g v1.03` usando o **link oficial** do Portal Nacional da NF-e para um diretório fora do repositório, por exemplo `~/Downloads/PL_010g_v1.03.zip`.
2. Examine a origem e registre o hash: `sha256sum ~/Downloads/PL_010g_v1.03.zip`.
3. Na pasta `backend`, execute:

```bash
python3 tools/fiscal/install_official_pl010g.py \
  --zip "$HOME/Downloads/PL_010g_v1.03.zip" \
  --sha256 "HASH_SHA256_DE_64_CARACTERES"
```

O instalador recusa ZIP alterado, entradas inseguras, XSDs duplicados, importações externas, dependências ausentes e pacotes sem os elementos da NT. Ele compila os XSDs com o Java local e grava `SCHEMA_MANIFEST.json` com o hash dos arquivos. Em erro, remove a área de preparação. O diretório de destino **não é sobrescrito**.

Valide os testes independentes da ferramenta:

```bash
python3 -m unittest discover -s tools/fiscal -p 'test_*.py' -v
./mvnw -Dtest=NfceSchemaPackageCompilationTest test
```

**Próximos marcos antes de ativar no ERP:** revisar PDF integral das NTs e restrições por CRT/modelo/data, confirmar a autenticidade dos arquivos importados, escrever testes Spring que assinem e validem XML 65 contra o PL_010g, implementar campos condicionais de valor líquido e totalizadores, conferir catálogo cClassTrib e realizar autorização/consulta/cancelamento reais com emissor credenciado em SEF/MG.

Não altere `FISCAL_SCHEMA_LOCATION` para `PL_010g` nesta etapa. O `application.properties` permanece usando `PL_010f`.
