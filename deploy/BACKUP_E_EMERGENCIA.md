# Backup e emergencia (VPS)

## Backup automatico pro seu drive

1. No VPS: `sudo apt install postgresql-client gnupg rclone`
2. `rclone config` -> new remote -> nome `gdrive` -> tipo `drive` (Google Drive).
   Num VPS sem navegador, o rclone mostra um comando `rclone authorize "drive"`
   pra rodar no SEU PC; cole o resultado de volta no VPS.
3. No `.env` do servidor:
   ```
   BACKUP_REMOTO=gdrive:MiteraBackups
   BACKUP_SENHA=<uma senha longa; guarde num gerenciador de senhas, sem ela nao restaura>
   BACKUP_DIAS_LOCAL=14
   BACKUP_DIAS_REMOTO=60
   ```
4. Teste na mao: `./deploy/backup_banco.sh` (tem que aparecer "enviado pra gdrive:...").
5. Cron a cada 6h (`crontab -e` com o usuario que roda o servidor):
   ```
   17 */6 * * * /caminho/do/servidor/deploy/backup_banco.sh >> /caminho/do/servidor/backups/backup.log 2>&1
   ```
6. Antes de atualizar o servidor, rode um backup na mao.

## Restaurar

```
sudo systemctl stop mitera
rclone copy gdrive:MiteraBackups/mitera_AAAA-MM-DD_HHMM.tar.gz.gpg backups/   # se veio do drive
./deploy/restaurar_banco.sh backups/mitera_AAAA-MM-DD_HHMM.tar.gz.gpg
sudo systemctl start mitera
```
O script guarda o banco atual em `backups/antes_de_restaurar_*.dump` antes de trocar.
Teste uma restauracao de verdade pelo menos uma vez (backup que nunca foi
restaurado nao e' backup).

## Emergencia (ataque passou do provedor)

Modos, sem reiniciar (vale em ate 2s):

| Como liga | Efeito |
|---|---|
| `echo login > emergencia.txt` ou `/emergency login` no chat | ninguem novo entra (cadastro, criar personagem, entrar); quem esta jogando continua |
| `echo total > emergencia.txt` ou `/emergency total` | igual + salva e tira todo mundo menos o admin; com `ADMIN_IPS` no .env so esses IPs conectam |
| `rm emergencia.txt` ou `/emergency off` | volta ao normal |

`.env` opcional: `MAX_PLAYERS=600` (servidor cheio), `CONEXOES_MAX_TOTAL=2000`,
`ADMIN_IPS=<seu ip>`, `ADMIN_USER_IDS=<id da sua conta>` (obrigatorio pra criar o
personagem admin num banco novo e pra ninguem usar o nome dele).

Rede (fora do jogo):
- Postgres so em localhost (`listen_addresses = 'localhost'`), porta 5432 nunca aberta.
- `SERVER_HOST=127.0.0.1` atras do Cloudflare Tunnel: a porta 3000 nem fica exposta.
- `ufw default deny incoming; ufw allow OpenSSH; ufw enable` (SSH so com chave).
- Ataque pesado: no Cloudflare use regras de Rate Limiting/WAF no host do jogo
  ("Under Attack Mode" desafia com JavaScript e trava o client do jogo; so pro site).
