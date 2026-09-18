-- ============================================================
-- V5 - Migrar dados existentes para a nova estrutura de Person
-- ============================================================

-- ============================================================
-- PESSOA FÍSICA
-- tb_person.rg_ie -> tb_individual_person.rg
-- ============================================================

INSERT INTO public.tb_individual_person (
    person_id,
    rg
)
SELECT
    p.id,
    NULLIF(
        regexp_replace(TRIM(p.rg_ie), '\D', '', 'g'),
        ''
    )
FROM public.tb_person p
WHERE p.tipo_pessoa = 'PF'
  AND p.rg_ie IS NOT NULL
  AND TRIM(p.rg_ie) <> '';

-- ============================================================
-- PESSOA JURÍDICA
-- tb_person.nome_fantasia -> tb_legal_entity.nome_fantasia
-- tb_person.rg_ie        -> tb_legal_entity.inscricao_estadual
-- ============================================================

INSERT INTO public.tb_legal_entity (
    person_id,
    nome_fantasia,
    inscricao_estadual
)
SELECT
    p.id,
    NULLIF(TRIM(p.nome_fantasia), ''),
    NULLIF(
        regexp_replace(TRIM(p.rg_ie), '\D', '', 'g'),
        ''
    )
FROM public.tb_person p
WHERE p.tipo_pessoa = 'PJ';

-- ============================================================
-- E-MAIL
-- tb_person.email -> tb_person_contact
-- ============================================================

INSERT INTO public.tb_person_contact (
    person_id,
    type,
    value,
    principal,
    description
)
SELECT
    p.id,
    'EMAIL',
    LOWER(TRIM(p.email)),
    TRUE,
    'E-mail principal migrado do cadastro anterior'
FROM public.tb_person p
WHERE p.email IS NOT NULL
  AND TRIM(p.email) <> '';

-- ============================================================
-- TELEFONE
-- tb_person.phone -> tb_person_contact
-- ============================================================

INSERT INTO public.tb_person_contact (
    person_id,
    type,
    value,
    principal,
    description
)
SELECT
    p.id,
    'TELEFONE',
    regexp_replace(TRIM(p.phone), '\D', '', 'g'),
    TRUE,
    'Telefone principal migrado do cadastro anterior'
FROM public.tb_person p
WHERE p.phone IS NOT NULL
  AND TRIM(p.phone) <> ''
  AND regexp_replace(TRIM(p.phone), '\D', '', 'g') <> '';

-- ============================================================
-- ENDEREÇOS
--
-- PF -> RESIDENCIAL
-- PJ -> COMERCIAL
--
-- Só cria endereço quando existir pelo menos uma informação.
-- ============================================================

INSERT INTO public.tb_person_address (
    person_id,
    type,
    cep,
    logradouro,
    numero,
    complemento,
    bairro,
    cidade,
    uf,
    principal
)
SELECT
    p.id,

    CASE
        WHEN p.tipo_pessoa = 'PF' THEN 'RESIDENCIAL'
        WHEN p.tipo_pessoa = 'PJ' THEN 'COMERCIAL'
    END,

    NULLIF(
        regexp_replace(TRIM(p.cep), '\D', '', 'g'),
        ''
    ),

    NULLIF(TRIM(p.logradouro), ''),
    NULLIF(TRIM(p.numero), ''),
    NULLIF(TRIM(p.complemento), ''),
    NULLIF(TRIM(p.bairro), ''),
    NULLIF(TRIM(p.cidade), ''),

    CASE
        WHEN p.uf IS NULL OR TRIM(p.uf) = ''
            THEN NULL
        ELSE UPPER(TRIM(p.uf))
    END,

    TRUE

FROM public.tb_person p

WHERE
    NULLIF(TRIM(p.cep), '') IS NOT NULL
    OR NULLIF(TRIM(p.logradouro), '') IS NOT NULL
    OR NULLIF(TRIM(p.numero), '') IS NOT NULL
    OR NULLIF(TRIM(p.complemento), '') IS NOT NULL
    OR NULLIF(TRIM(p.bairro), '') IS NOT NULL
    OR NULLIF(TRIM(p.cidade), '') IS NOT NULL
    OR NULLIF(TRIM(p.uf), '') IS NOT NULL;