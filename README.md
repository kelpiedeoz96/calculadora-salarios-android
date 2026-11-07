# Calculadora de Salarios Colombia (Android)

Versión nativa de Android (Kotlin + Jetpack Compose) de la calculadora de nómina por horas de
escritorio: recargos nocturnos, horas extra, dominicales y festivos, auxilio de transporte y
descuentos de salud y pensión. Funciona sin internet; los datos se quedan en el teléfono.

> Documento en construcción: se completa en la fase 6.

## Requisitos

- **JDK 21.** Gradle y las pruebas se ejecutan con Java 21. Si el Java del sistema es otro
  (por ejemplo 25 o 27), indica el 21 con `JAVA_HOME`:
  - fish: `set -x JAVA_HOME /usr/lib/jvm/java-21-openjdk`
  - bash/zsh: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk`
- **Android SDK** con `platforms;android-36` y `build-tools;36.0.0`. Su ruta va en
  `local.properties` (`sdk.dir=/ruta/al/Sdk`), que no se versiona.
- No hace falta instalar Gradle: se usa `./gradlew` (Gradle 9.3.1).

## Probar y compilar

```fish
./gradlew :engine:test :app:testDebugUnitTest   # pruebas del motor y de la app (JVM, sin emulador)
./gradlew :app:recordRoborazziDebug             # capturas de pantalla en app/build/capturas/
./gradlew :app:assembleDebug                    # APK de depuración
```

## Estructura

- `engine/`: motor de cálculo en Kotlin puro (sin Android), con las pruebas portadas del
  escritorio y el oráculo `salida_esperada.json`.
- `app/`: interfaz (Compose, Material 3), almacenamiento y respaldos.
- `referencia/` (no se versiona): código del escritorio en C# y especificación del motor.

## Licencia

MIT (ver `LICENSE`).
