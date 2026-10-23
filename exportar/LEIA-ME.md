# Exportar o Mitera Online (APK + Linux + Windows)

Arquivos daqui vao pro projeto `LibgdxTeste` (substituindo os atuais):

| Daqui | Vai para |
|---|---|
| `core/build.gradle` | `LibgdxTeste/core/build.gradle` |
| `desktop/build.gradle` | `LibgdxTeste/desktop/build.gradle` |
| `android/build.gradle` | `LibgdxTeste/android/build.gradle` |
| `android/proguard-rules.pro` | `LibgdxTeste/android/proguard-rules.pro` |
| `exportar.sh` | `LibgdxTeste/exportar.sh` |
| `keystore.properties.exemplo` | `LibgdxTeste/` (copie como `keystore.properties` e preencha) |
| `gitignore.adicionar` | cole o conteudo no `LibgdxTeste/.gitignore` |

## 1. Keystore (uma vez so')

Se ainda nao tem, crie na raiz do projeto:

    keytool -genkeypair -v -keystore mitera-release.jks -alias mitera -keyalg RSA -keysize 4096 -validity 10000

Guarde o `.jks` e as senhas junto do `.env` (fora do Git). Perdeu o keystore =
os jogadores precisam desinstalar e instalar de novo pra atualizar.

## 2. Antes de exportar

- `ServerConfig.java`: `BASE_URL` com o endereco publico (`https://seudominio.com`).
- Com HTTPS no ar, tire `android:usesCleartextTraffic="true"` do AndroidManifest.
- Opcional, antes do 1o APK publico: em `gradle.properties` coloque
  `appId=com.miteraonline.game` (depois de publicado, nunca mais mude).

## 3. Exportar

Atalhos de duplo clique (abrem uma janela de terminal com o progresso):

- `exportar_tudo.sh`: APK assinado + Linux + Windows em `dist/`
- `exportar_linux.sh`: so' o Linux
- `depurar_celular.sh`: versao de teste instalada no celular pelo USB (igual o antigo
  build_apk.sh). Entra como app separado (".debug"), convive com o APK oficial.

Ou pelo terminal:

    ./exportar.sh          # tudo
    ./exportar.sh apk      # so' o APK
    ./exportar.sh linux    # so' Linux
    ./exportar.sh windows  # so' Windows

Saida em `dist/`. A versao vem sozinha do `CLIENT_VERSION` (v0.50 -> APK versionCode 50).
`dist/mapping/` guarda o mapping.txt de cada APK (traduz crash log do APK
embaralhado): guarde, nunca publique.
