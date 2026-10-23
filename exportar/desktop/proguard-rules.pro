# ProGuard do .jar/.exe/Linux: embaralha so' o codigo do jogo (com.teste.**).
# Se o jogo de PC exportado fechar ao abrir, o log diz a classe: ponha um -keep pra ela aqui.

# Bibliotecas (libGDX, LWJGL, WebSocket...) ficam intactas.
-keep class !com.teste.** { *; }
-keep interface !com.teste.** { *; }

# Entrada do jogo no PC.
-keep class com.teste.desktop.DesktopGameLauncher { public static void main(java.lang.String[]); }

# Enums usam values()/valueOf() por reflexao.
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Traduz crash log do jogo de PC embaralhado (guarde, NUNCA publique).
-printmapping build/proguard/mapping.txt

# So' renomeia (nao corta nem "otimiza" codigo: mais seguro, nada some).
-dontshrink
-dontoptimize
-repackageclasses ''
-allowaccessmodification

# Avisos de classes opcionais das bibliotecas (slf4j, Android...) nao importam no PC.
-dontwarn **
-dontnote **
-ignorewarnings
