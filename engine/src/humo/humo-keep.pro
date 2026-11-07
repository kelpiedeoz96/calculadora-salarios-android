# Reglas extra solo para la prueba de humo (además de las del release que genera AGP).
-keep class co.com.calculadoradesalarios.humo.HumoR8 { public static void main(java.lang.String[]); }
-dontwarn android.**
-dontwarn androidx.**
-dontwarn dalvik.**
