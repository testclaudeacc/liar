#!/bin/bash
# Duplo clique: exporta so' o Linux (com Java embutido) pra dist/.
if [ ! -t 1 ]; then exec gnome-terminal -- bash "$0"; fi
cd "$(dirname "$0")"
./exportar.sh linux && notify-send "Mitera Online" "Export Linux pronto em dist/" 2>/dev/null
read -p "Enter pra fechar..."
