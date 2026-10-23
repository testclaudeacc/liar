#!/bin/bash
# Exporta o Mitera Online: APK assinado + Linux + Windows (Java embutido).
# Uso (na raiz do projeto LibgdxTeste): ./exportar.sh [apk|linux|windows|tudo]
# Saida: pasta dist/  (o APK vai como dist/MiteraOnline-<versao>.apk)
set -e
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

ALVO="${1:-tudo}"
VERSAO=$(grep -oP 'CLIENT_VERSION\s*=\s*"v?\K[0-9.]+' core/src/main/java/com/teste/game/rede/ServerConfig.java)
mkdir -p dist

# Lembrete: o client exportado tem que apontar pro servidor publico.
if grep -q 'BASE_URL = "http://192\.168\.' core/src/main/java/com/teste/game/rede/ServerConfig.java; then
    echo "AVISO: BASE_URL ainda aponta pra rede local (192.168...) - jogador de fora nao vai conectar."
fi

if [ "$ALVO" = "apk" ] || [ "$ALVO" = "tudo" ]; then
    [ -f keystore.properties ] || { echo "ERRO: falta keystore.properties (veja keystore.properties.exemplo)"; exit 1; }
    ./gradlew android:assembleRelease
    cp android/build/outputs/apk/release/android-release.apk "dist/MiteraOnline-$VERSAO.apk"
    # mapping.txt traduz crash log do APK embaralhado: guarde (NUNCA publique).
    mkdir -p dist/mapping && cp android/build/outputs/mapping/release/mapping.txt "dist/mapping/mapping-$VERSAO.txt" 2>/dev/null || true
    echo "APK: dist/MiteraOnline-$VERSAO.apk"
fi
if [ "$ALVO" = "linux" ] || [ "$ALVO" = "tudo" ]; then
    ./gradlew desktop:packageLinuxX64
fi
if [ "$ALVO" = "windows" ] || [ "$ALVO" = "tudo" ]; then
    ./gradlew desktop:packageWinX64
fi
# mapping do jogo de PC embaralhado (ProGuard): traduz crash log do .exe/Linux. Guarde, NUNCA publique.
if [ -f desktop/build/proguard/mapping.txt ]; then
    mkdir -p dist/mapping && cp desktop/build/proguard/mapping.txt "dist/mapping/mapping-pc-$VERSAO.txt"
fi
echo "Pronto. Arquivos em dist/:"
ls -lh dist/
