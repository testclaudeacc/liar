#!/bin/bash
# Duplo clique: exporta APK assinado + Linux + Windows pra dist/.
# Aberto sem terminal (duplo clique)? Reabre numa janela de terminal pra ver o progresso.
if [ ! -t 1 ]; then exec gnome-terminal -- bash "$0"; fi
cd "$(dirname "$0")"
./exportar.sh tudo && notify-send "Mitera Online" "Export pronto: APK + Linux + Windows em dist/" 2>/dev/null
read -p "Enter pra fechar..."
