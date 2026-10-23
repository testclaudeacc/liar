# R8 do APK de release (embaralha o jogo). Se o APK de release fechar ao abrir
# e o log disser que uma classe sumiu, acrescente um -keep pra ela aqui.

# --- Entrada do app ---
-keep class com.teste.android.AndroidLauncher { *; }

# --- libGDX inteiro fica intacto: o backend/FreeType sao chamados por JNI e o
# scene2d cria Actions por reflexao (Pools). E' biblioteca aberta, nao precisa esconder.
-keep class com.badlogic.gdx.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.badlogic.gdx.backends.android.**
-dontwarn com.badlogic.gdx.jnigen.**
-dontwarn com.badlogic.gdx.utils.GdxBuild
-dontwarn com.badlogic.gdx.physics.box2d.utils.Box2DBuild
-dontwarn com.badlogic.gdx.graphics.g2d.freetype.FreetypeBuild

# --- Java-WebSocket (conexao com o servidor) ---
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**
-dontwarn org.slf4j.**

# --- O resto do jogo (telas, entidades, rede, mapa) e' embaralhado ---
-repackageclasses ''
-allowaccessmodification
