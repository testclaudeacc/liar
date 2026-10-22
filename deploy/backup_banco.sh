#!/usr/bin/env bash
# Backup do banco do Mitera (PostgreSQL) + arquivos de estado do servidor.
#   - pg_dump compactado com data no nome, em ./backups (pasta do servidor)
#   - guarda BACKUP_DIAS_LOCAL dias no VPS e BACKUP_DIAS_REMOTO no drive
#   - manda pro seu drive com rclone (BACKUP_REMOTO, ex: "gdrive:MiteraBackups")
#   - com BACKUP_SENHA no .env o arquivo vai criptografado (gpg): o dump tem
#     emails e hashes de senha, entao e' bom ligar.
# Uso: ./deploy/backup_banco.sh        (o cron chama a cada 6h, ver BACKUP_E_EMERGENCIA.md)
set -euo pipefail

PASTA="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PASTA"
# Mesmas variaveis do servidor (DB_USER, DB_PASS, DB_HOST, DB_PORT, DB_NAME, BACKUP_*).
if [ -f .env ]; then set -a; . ./.env; set +a; fi

DB_USER="${DB_USER:-postgres}"; DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}";     DB_NAME="${DB_NAME:-postgres}"
DIAS_LOCAL="${BACKUP_DIAS_LOCAL:-14}"
DIAS_REMOTO="${BACKUP_DIAS_REMOTO:-60}"
REMOTO="${BACKUP_REMOTO:-}"

mkdir -p backups
chmod 700 backups
DATA="$(date +%Y-%m-%d_%H%M)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 1) Banco (formato custom: compactado, restaura com pg_restore).
PGPASSWORD="${DB_PASS:-postgres}" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" \
    -Fc -Z 6 -f "$TMP/banco.dump" "$DB_NAME"
# Confere que o dump abre (dump corrompido = backup inutil).
pg_restore -l "$TMP/banco.dump" > /dev/null

# 2) Estado do servidor que nao fica no banco (mapa registrado, mutes...).
for f in conteudo_mapas.json mapas_colisao.json chat_mutes.json; do
    [ -f "$f" ] && cp "$f" "$TMP/"
done

ARQ="backups/mitera_${DATA}.tar.gz"
tar -czf "$ARQ" -C "$TMP" .
if [ -n "${BACKUP_SENHA:-}" ]; then
    printf '%s' "$BACKUP_SENHA" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
        --symmetric --cipher-algo AES256 -o "$ARQ.gpg" "$ARQ"
    rm -f "$ARQ"; ARQ="$ARQ.gpg"
fi
chmod 600 "$ARQ"
echo "[backup] $ARQ ($(du -h "$ARQ" | cut -f1))"

# 3) Fora do VPS.
if [ -n "$REMOTO" ]; then
    rclone copy "$ARQ" "$REMOTO" --retries 5
    rclone delete "$REMOTO" --min-age "${DIAS_REMOTO}d" --include 'mitera_*' || true
    echo "[backup] enviado pra $REMOTO"
else
    echo "[backup] AVISO: BACKUP_REMOTO vazio - backup ficou SO' no VPS"
fi

# 4) Limpa os antigos do VPS.
find backups -name 'mitera_*' -mtime +"$DIAS_LOCAL" -delete
