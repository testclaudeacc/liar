#!/bin/bash
# Abre o jogo de verdade (login -> personagem -> mundo). Uso: ./jogar.sh
# Equivalente a "abrir a engine" no Godot, mas aqui e' um script porque
# libGDX nao tem uma engine/editor pra abrir - so o cliente compilado.
#
# Se o jogo fechar com erro (exception, crash da JVM, falha de compilacao),
# gera um crash log em crash_logs/crash_<data>_<hora>.log com:
#   - a saida inteira do gradle/jogo (stack trace incluso)
#   - o arquivo hs_err da JVM, se ela mesma tiver caido
#   - o final do log do servidor
#   - informacoes do sistema (Java, SO, git)
set -o pipefail
cd "$(dirname "$0")"

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

PASTA_LOGS="crash_logs"
LOG_SERVIDOR="/tmp/servidor_inverted_realms.log"
mkdir -p "$PASTA_LOGS"
SAIDA_JOGO="$(mktemp /tmp/jogo_saida.XXXXXX)"
INICIO="$(date +%s)"

if ! curl -s -o /dev/null --max-time 2 http://192.168.18.124:3000/ping; then
    echo "Servidor nao esta rodando, subindo ele primeiro..."
    (cd ../WorkspaceJogo/server && source meu_ambiente/bin/activate && setsid python3 servidor.py < /dev/null > "$LOG_SERVIDOR" 2>&1 &)
    sleep 3
fi

# Saida aparece no terminal normalmente E fica guardada pro crash log.
./gradlew :desktop:runGame 2>&1 | tee "$SAIDA_JOGO"
CODIGO=$?

# Erro = codigo de saida diferente de 0 OU uma exception na saida (as vezes o
# gradle termina com 0 mesmo o jogo tendo morrido numa thread).
if [ "$CODIGO" -ne 0 ] || grep -qE "Exception in thread|FATAL|BUILD FAILED|A fatal error has been detected" "$SAIDA_JOGO"; then
    CRASH="$PASTA_LOGS/crash_$(date +%Y-%m-%d_%H-%M-%S).log"
    {
        echo "=============================================="
        echo " CRASH LOG - $(date '+%d/%m/%Y %H:%M:%S')"
        echo "=============================================="
        echo "Codigo de saida: $CODIGO"
        echo "Tempo rodando: $(( $(date +%s) - INICIO ))s"
        echo

        echo "------------- RESUMO DO ERRO -------------"
        # Primeira exception + as linhas do stack trace logo abaixo dela.
        grep -nE -A25 "Exception|Error:|FATAL|BUILD FAILED" "$SAIDA_JOGO" | head -80
        echo

        echo "------------- SAIDA COMPLETA DO JOGO -------------"
        cat "$SAIDA_JOGO"
        echo

        # Crash da propria JVM (driver de video, memoria...) gera um hs_err_pid*.log.
        HS_ERR="$(find . desktop /tmp -maxdepth 1 -name 'hs_err_pid*.log' -newermt "@$INICIO" 2>/dev/null | head -1)"
        if [ -n "$HS_ERR" ]; then
            echo "------------- CRASH DA JVM ($HS_ERR) -------------"
            cat "$HS_ERR"
            echo
        fi

        if [ -f "$LOG_SERVIDOR" ]; then
            echo "------------- ULTIMAS 100 LINHAS DO SERVIDOR -------------"
            tail -n 100 "$LOG_SERVIDOR"
            echo
        fi

        echo "------------- SISTEMA -------------"
        echo "SO: $(uname -srmo)"
        "$JAVA_HOME/bin/java" -version 2>&1
        echo "Commit: $(git rev-parse --short HEAD 2>/dev/null || echo '?') ($(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?'))"
        git status --short 2>/dev/null | head -20
    } > "$CRASH"

    echo
    echo "=============================================="
    echo " O jogo fechou com erro. Crash log salvo em:"
    echo "   $(pwd)/$CRASH"
    echo "=============================================="
fi

rm -f "$SAIDA_JOGO"
exit "$CODIGO"
