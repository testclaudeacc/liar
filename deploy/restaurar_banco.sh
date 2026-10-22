#!/usr/bin/env bash
# Restaura um backup feito pelo backup_banco.sh. PARE O SERVIDOR ANTES
# (sudo systemctl stop mitera) - senao ele salva por cima do que voltou.
# Uso: ./deploy/restaurar_banco.sh backups/mitera_2026-10-22_0300.tar.gz[.gpg]
#      (backup do drive: rclone copy gdrive:MiteraBackups/<arquivo> backups/ antes)
set -euo pipefail
[ $# -eq 1 ] || { echo "uso: $0 <arquivo de backup>"; exit 1; }
PASTA="$(cd "$(dirname "$0")/.." && pwd)"
ARQ="$(realpath "$1")"
cd "$PASTA"
if [ -f .env ]; then set -a; . ./.env; set +a; fi
DB_USER="${DB_USER:-postgres}"; DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}";     DB_NAME="${DB_NAME:-postgres}"

if systemctl is-active --quiet mitera 2>/dev/null; then
    echo "O servidor (mitera) esta' rodando. Pare antes: sudo systemctl stop mitera"; exit 1
fi
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
if [[ "$ARQ" == *.gpg ]]; then
    printf '%s' "${BACKUP_SENHA:?BACKUP_SENHA vazio no .env}" | gpg --batch --yes --pinentry-mode loopback \
        --passphrase-fd 0 -d -o "$TMP/b.tar.gz" "$ARQ"
    tar -xzf "$TMP/b.tar.gz" -C "$TMP"
else
    tar -xzf "$ARQ" -C "$TMP"
fi

# Seguranca: antes de apagar, guarda o estado atual (vai que o backup era o errado).
mkdir -p backups
PGPASSWORD="${DB_PASS:-postgres}" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -Fc \
    -f "backups/antes_de_restaurar_$(date +%Y-%m-%d_%H%M).dump" "$DB_NAME" || true

read -r -p "Isso SUBSTITUI o banco '$DB_NAME' pelo backup. Digite RESTAURAR pra continuar: " ok
[ "$ok" = "RESTAURAR" ] || { echo "cancelado"; exit 1; }
PGPASSWORD="${DB_PASS:-postgres}" pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" \
    -d "$DB_NAME" --clean --if-exists --no-owner "$TMP/banco.dump"
for f in conteudo_mapas.json mapas_colisao.json chat_mutes.json; do
    [ -f "$TMP/$f" ] && cp "$TMP/$f" "$PASTA/"
done
echo "Restaurado. Suba o servidor: sudo systemctl start mitera"
