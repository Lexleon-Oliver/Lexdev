-- ============================================================
-- V3 - Insere usuário administrador inicial
-- ============================================================

INSERT INTO tb_users (
    active,
    email,
    name,
    password,
    role,
    username
)
SELECT
    true,
    'suporte@lexdev.net.br',
    'Suporte do Sistema',
    '$2a$10$34/JktJ6gFZ7yVR3csgdJOp7mGis7Njqc..zrDqWkcDnB5Qt6/3oy',
    'ROLE_SUPPORT',
    '@support'
WHERE NOT EXISTS (
    SELECT 1
    FROM tb_users
    WHERE username = '@support'
);