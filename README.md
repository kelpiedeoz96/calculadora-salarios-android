# Calculadora de Salarios Colombia (Android)

App nativa de Android (Kotlin + Jetpack Compose) para liquidar nómina por horas en Colombia:
recargos nocturnos, horas extra, dominicales y festivos (con el cambio al 100 % desde el 1 de julio
de 2027), auxilio de transporte y descuentos de salud y pensión. Exporta comprobantes en PDF y la
nómina en Excel.

Es la versión para celular de la app de escritorio (C# / Avalonia): **mismo motor, mismas cifras y
mismo formato de datos**. Un `data.json` o un respaldo del escritorio se abre en el celular, y al revés.

- **Sin internet.** La app no pide el permiso `INTERNET`, no tiene analítica, anuncios ni nube.
- **Datos privados.** Todo se guarda en el almacenamiento privado de la app y queda fuera de la copia
  en la nube de Android (`allowBackup="false"`). Los respaldos `.zip` son locales y, si quieres, se copian
  a una carpeta que elijas (Drive, tarjeta SD, USB…).
- **Datos genéricos.** La app arranca con "Mi Empresa" y un empleado de ejemplo; todo se edita en
  Ajustes y Empleados.

## Pantallas

- **Panel:** horas, recargos, horas extra, horas por categoría, mejores empleados y avisos.
- **Liquidación:** registro de jornada por día (turnos rápidos, selector de hora), medidor semanal
  contra el tope de 42 h, auditoría por categoría y liquidación quincenal. PDF y Excel del empleado.
- **Empleados:** resumen del periodo, búsqueda, ficha con foto, deslizar para borrar (con deshacer).
  PDF y Excel de todos.
- **Calendario:** el mes con horas, dominicales, festivos y días con extra.
- **Historial:** las últimas 25 copias de los datos, respaldo JSON para intercambiar con el escritorio.
- **Ajustes:** empresa, NIT, logo, porcentajes, salario mínimo, auxilio, turnos, festivos, tema
  (claro, oscuro o sistema) y respaldos.

## Requisitos

- **JDK 21.** Gradle y las pruebas se ejecutan con Java 21. `build.fish` lo busca solo; si usas
  `./gradlew` directamente y tu Java es otro, indica el 21:
  - fish: `set -x JAVA_HOME /usr/lib/jvm/java-21-openjdk`
  - bash/zsh: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk`
- **Android SDK** con `platforms;android-36` y `build-tools;36.0.0`. Su ruta va en `local.properties`
  (`sdk.dir=/ruta/al/Sdk`), que no se versiona.
- **fish** para `build.fish`. No hace falta instalar Gradle: se usa `./gradlew` (Gradle 9.3.1).
- `gradle.properties` limita la memoria (Gradle 1,5 GB, Kotlin 1 GB) para equipos con poca RAM.

## Compilar, probar e instalar

```fish
./build.fish probar     # pruebas del motor y de la app (JVM, sin emulador)
./build.fish debug      # APK de depuración: app/build/outputs/apk/debug/app-debug.apk
./build.fish instalar   # adb install -r en el teléfono o emulador conectado
./build.fish release    # APK y AAB con R8 + prueba de humo de R8 + revisión de permisos
./build.fish todo       # probar, debug y release
./build.fish capturas   # capturas de cada pantalla en claro y oscuro (app/build/capturas/)
```

## Firma del release

La llave de firma **nunca** va al repositorio: `*.jks`, `*.keystore` y `keystore.properties` están en
`.gitignore`, y ninguna contraseña está escrita en el proyecto.

1. Crea la llave (una sola vez). `keytool` te pide las contraseñas y tus datos:
   ```fish
   ./build.fish llave
   ```
   Queda en `~/.android/calculadora-salarios-release.jks` (fuera del repositorio) y se crea
   `keystore.properties` con solo la ruta y el alias. **Guarda una copia del `.jks` y de su contraseña:**
   sin ellos no podrás publicar actualizaciones de la app.
2. Antes de compilar el release, pon las contraseñas en la sesión (no quedan en ningún archivo):
   ```fish
   read -s -P 'Contraseña del keystore: ' -gx CALC_STORE_PASSWORD
   ./build.fish release
   ```
   Si la contraseña de la llave es distinta, define también `CALC_KEY_PASSWORD`.

Sin `keystore.properties`, el release se compila igual pero sin firmar (sirve para probar R8).

## Pasar los datos del escritorio al celular (y al revés)

- **Respaldo JSON:** en el escritorio, Historial → "Descargar respaldo JSON". En el celular,
  Historial → "Cargar respaldo" y elige el archivo. También acepta el `data.json` completo del
  escritorio. Antes de cargar se guarda una copia de lo que había. Al revés: Historial → "Descargar
  respaldo JSON" en el celular y "Cargar respaldo" en el escritorio.
- **Respaldo .zip completo (con fotos y logo):** Ajustes → Respaldos → "Restaurar desde un respaldo…".

## Exportación (PDF y Excel)

- El **PDF** lo escribe la propia app en Kotlin, sin librerías: el mismo comprobante del escritorio,
  en hoja carta horizontal, con la fuente Lato incrustada (subconjunto TrueType con ToUnicode, para
  que se pueda copiar y buscar el texto con tildes). Una página o más por empleado.
- El **Excel** (.xlsx) tiene las hojas Resumen, Detalle diario y Parámetros, iguales a las del escritorio.
- Se comparten (`ACTION_SEND`) o se guardan en la carpeta que elijas.
- Verificación contra el escritorio (opcional, en el computador):
  - `herramientas/comparar_cifras_pdf.fish referencia.pdf generado.pdf` compara todas las cifras.
  - `python -I herramientas/comparar_xlsx.py referencia.xlsx generado.xlsx` compara celda por celda
    (necesita `openpyxl`).

## Pruebas

- **Motor** (`./gradlew :engine:test`): las 116 pruebas portadas del escritorio, el oráculo
  `salida_esperada.json` (5 escenarios, 6 empleados, día por día), los casos límite de la
  especificación, el intercambio de `data.json` con el escritorio y la exportación (PDF y Excel
  comparados con los del escritorio).
- **App** (`./gradlew :app:testDebugUnitTest`): ViewModels con almacenamiento falso (sin emulador),
  cifras de cada pantalla contra el oráculo, almacenamiento y respaldos con Robolectric, y capturas de
  pantalla con Roborazzi.
- **R8** (`./gradlew :engine:humoR8Comparar`): el motor pasa por el mismo R8 del release y debe producir
  los mismos PDF, Excel y `data.json`, byte a byte.

## Estructura

```
engine/      Motor de cálculo en Kotlin puro (sin Android): Engine, FestivosCalc, JornadaHelper,
             PanelCalc, CalendarioCalc, HistorialCalc, PeriodoCalc, JsonDataStore, RespaldoAutomatico
             y exportar/ (PDF, fuentes TrueType, Excel). Mismos nombres que el Core de C#.
app/         Interfaz (Compose, Material 3), repositorio, almacenamiento, respaldos y exportación Android.
herramientas/  Comparadores de PDF y Excel contra el escritorio.
build.fish   Compilar, probar, instalar y firmar.
referencia/  (no se versiona) Código del escritorio en C# y especificación del motor.
```

## Licencias

- Código: **MIT** (ver `LICENSE`).
- Licencias de terceros (fuente Lato con SIL OFL 1.1, AndroidX, Kotlin…): ver `NOTICE`.
