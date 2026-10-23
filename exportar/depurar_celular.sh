#!/bin/bash
# Duplo clique: compila a versao de DEPURACAO e instala no celular via USB
# (igual o build_apk.sh antigo). Instala como app separado ("com.<id>.debug"),
# entao convive com o APK oficial assinado no mesmo celular.
if [ ! -t 1 ]; then exec gnome-terminal -- bash "$0"; fi
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

if ! curl -s -o /dev/null --max-time 2 http://192.168.18.124:3000/ping; then
    echo "Servidor nao esta rodando, subindo ele primeiro..."
    (cd ../WorkspaceJogo/server && source meu_ambiente/bin/activate && MAPA_TMX="$PWD/../../LibgdxTeste/core/assets/maps/World.tmx" setsid python3 servidor.py < /dev/null > /tmp/servidor_inverted_realms.log 2>&1 &)
    sleep 3
fi

echo "Compilando e instalando o Mitera Online (debug) no celular..."
if ./gradlew android:installDebug; then
    notify-send "Mitera Online" "Jogo (debug) instalado no celular!" 2>/dev/null
else
    echo "Falhou. O celular esta' conectado no USB com a depuracao USB ligada?"
fi
read -p "Enter pra fechar..."
