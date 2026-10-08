# Banco de dados: comandos uteis

## Como abrir o banco (PostgreSQL)

No terminal da maquina do servidor:

```bash
psql -U postgres -h localhost -d inverted_realms
```

O banco do jogo e' o **`inverted_realms`** (os outros da lista do `\l` -
`postgres`, `template0`, `template1` - sao do proprio PostgreSQL, NAO apague).
Se ja' estiver dentro do `psql` em outro banco: `\c inverted_realms`.
Pra conferir: `\dt` tem que mostrar `characters`, `users`, `friendships`.

Dentro do `psql`, cada comando termina com `;`. Pra sair: `\q`.

## Personagens apagados (soft delete)

Apagar um personagem no jogo NAO tira ele do banco: o nome vira
`<nome antigo>#del#<letras aleatorias>` e a coluna `deleted_at` recebe a hora.
Ele some da tela de personagens e do ranking, e o nome fica livre.

A coluna `deleted_at` e' criada pelo servidor novo quando ele inicia. Se der
"column deleted_at does not exist", reinicie o servidor (ou rode
`ALTER TABLE characters ADD COLUMN IF NOT EXISTS deleted_at DOUBLE PRECISION;`).
So' quem foi apagado DEPOIS dessa atualizacao aparece aqui (antes apagava de verdade).

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

## Outros personagens de teste (fTest, Debug Hero, SwA/SwB + numeros)

Conferir primeiro:

```sql
SELECT name FROM characters
WHERE name ILIKE 'fTest%' OR name ILIKE 'Debug%Hero%' OR name ~* '^Sw[AB] ?[0-9]';
```

Se a lista estiver certa:

```sql
DELETE FROM characters
WHERE name ILIKE 'fTest%' OR name ILIKE 'Debug%Hero%' OR name ~* '^Sw[AB] ?[0-9]';
```

(O ranking ja' ignora esses nomes mesmo sem apagar.)
