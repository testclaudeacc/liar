# Banco de dados: comandos uteis

## Como abrir o banco (PostgreSQL)

No terminal da maquina do servidor:

```bash
psql -U postgres -h localhost -d postgres
```

(usuario/host/banco sao os do `.env`: `DB_USER`, `DB_HOST`, `DB_NAME`; a senha e' a `DB_PASS`.)
Dentro do `psql`, cada comando termina com `;`. Pra sair: `\q`.

## Personagens apagados (soft delete)

Apagar um personagem no jogo NAO tira ele do banco: o nome vira
`<nome antigo>#del#<letras aleatorias>` e a coluna `deleted_at` recebe a hora.
Ele some da tela de personagens e do ranking, e o nome fica livre.

Ver os apagados (mais recentes primeiro):

```sql
SELECT id, user_id, name, to_timestamp(deleted_at) AS apagado_em
FROM characters WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC;
```

Recuperar (troque o `id` e o nome; o nome nao pode estar em uso e a conta
precisa ter menos de 4 personagens ativos):

```sql
UPDATE characters SET name = 'NomeAntigo', deleted_at = NULL WHERE id = 123;
```

O ranking guarda a lista por 1 hora; reinicie o servidor pra ele aparecer na hora.

## Apagar de vez os personagens de teste (PTMember/PTLeader/PTSolo)

Primeiro confira quem seria apagado:

```sql
SELECT name FROM characters WHERE name ILIKE 'PTMember%' OR name ILIKE 'PTLeader%' OR name ILIKE 'PTSolo%';
```

Se a lista estiver certa:

```sql
DELETE FROM characters WHERE name ILIKE 'PTMember%' OR name ILIKE 'PTLeader%' OR name ILIKE 'PTSolo%';
```
