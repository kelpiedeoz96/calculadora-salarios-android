#!/usr/bin/env fish
# Calculadora de Salarios Colombia (Android): compilar, probar, instalar y firmar.
# Uso: ./build.fish [probar|debug|instalar|release|todo|capturas|llave|ayuda]

cd (status dirname)

function paso
    set_color cyan; echo "==> $argv"; set_color normal
end

function falla
    set_color red; echo "Error: $argv" >&2; set_color normal
    exit 1
end

# Gradle y las pruebas necesitan JDK 21 (el Java del sistema puede ser otro).
function usar_jdk21
    if set -q JAVA_HOME; and test -x $JAVA_HOME/bin/java
        if $JAVA_HOME/bin/java -version 2>&1 | string match -q -r 'version "21[".]'
            return
        end
    end
    for candidato in /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/java-21-openjdk-amd64 /usr/lib/jvm/temurin-21-jdk
        if test -x $candidato/bin/java
            set -gx JAVA_HOME $candidato
            return
        end
    end
    falla "No encontré un JDK 21. Instálalo (Arch: sudo pacman -S jdk21-openjdk) o define JAVA_HOME."
end

# Ruta del Android SDK: local.properties (sdk.dir) o ANDROID_HOME.
function sdk_dir
    if test -f local.properties
        set -l d (string match -r '^sdk\.dir=(.*)$' < local.properties)[2]
        test -n "$d"; and echo $d; and return
    end
    set -q ANDROID_HOME; and echo $ANDROID_HOME; and return
    falla "No encontré el Android SDK: crea local.properties con sdk.dir=/ruta/al/Sdk."
end

function herramienta -a nombre
    set -l sdk (sdk_dir)
    if test "$nombre" = adb
        set -l a $sdk/platform-tools/adb
        test -x $a; and echo $a; and return
        command -q adb; and command -s adb; and return
        falla "Falta adb: instala platform-tools en el SDK."
    end
    # Comodines de fish y no "ls": ls puede ser un alias con colores.
    set -l versiones (path sort $sdk/build-tools/*)
    set -l bt $versiones[-1]
    test -x $bt/$nombre; or falla "Falta $nombre en $sdk/build-tools."
    echo $bt/$nombre
end

function probar
    paso "Pruebas del motor y de la app (JVM, sin emulador)"
    ./gradlew :engine:test :app:testDebugUnitTest; or falla "Hay pruebas en rojo."
end

function debug
    paso "APK de depuración"
    ./gradlew :app:assembleDebug; or falla "No compiló."
    echo "  app/build/outputs/apk/debug/app-debug.apk"
end

function instalar
    set -l adb (herramienta adb)
    set -l apk app/build/outputs/apk/debug/app-debug.apk
    test -f $apk; or debug
    if not $adb get-state >/dev/null 2>&1
        falla "No hay teléfono ni emulador conectado (revisa: $adb devices)."
    end
    paso "Instalando en el dispositivo"
    $adb install -r $apk; or falla "No se pudo instalar."
end

function release
    paso "Release con R8 (APK y AAB)"
    if not test -f keystore.properties
        set_color yellow
        echo "  Sin keystore.properties: el APK sale SIN FIRMAR (sirve para probar R8, no para instalar ni publicar)."
        echo "  Para firmar: ./build.fish llave"
        set_color normal
    end
    ./gradlew :app:assembleRelease :app:bundleRelease; or falla "No compiló el release."
    paso "Prueba de humo de R8: PDF, Excel y data.json deben salir idénticos"
    ./gradlew :engine:humoR8Comparar; or falla "R8 cambió el resultado."
    set -l apks app/build/outputs/apk/release/*.apk
    set -l apk $apks[1]
    paso "Permisos del APK de release"
    set -l aapt (herramienta aapt)
    set -l permisos ($aapt dump permissions $apk)
    printf '  %s\n' $permisos
    if string match -q '*android.permission.INTERNET*' $permisos
        falla "El release pide INTERNET."
    end
    echo "  $apk"
    echo "  app/build/outputs/bundle/release/app-release.aab"
end

function capturas
    paso "Capturas de pantalla (Roborazzi) en app/build/capturas/"
    ./gradlew :app:recordRoborazziDebug; or falla "No se generaron las capturas."
end

# Crea el keystore de firma FUERA del repositorio. keytool pide las contraseñas y los datos;
# ninguna contraseña queda escrita en el proyecto.
function llave
    set -l destino ~/.android/calculadora-salarios-release.jks
    if test -f $destino
        falla "Ya existe $destino. Si lo pierdes no podrás actualizar la app publicada: guárdalo con copia."
    end
    mkdir -p (dirname $destino)
    paso "Creando $destino (RSA 4096, válido ~27 años)"
    $JAVA_HOME/bin/keytool -genkeypair -v -keystore $destino -alias calculadora -keyalg RSA -keysize 4096 -validity 10000; or falla "keytool falló."
    printf 'storeFile=%s\nkeyAlias=calculadora\n' $destino > keystore.properties
    chmod 600 $destino keystore.properties
    paso "Listo. keystore.properties (ignorado por git) apunta al keystore."
    echo "  Antes de ./build.fish release define las contraseñas en la sesión, por ejemplo:"
    echo "    read -s -P 'Contraseña del keystore: ' -gx CALC_STORE_PASSWORD"
end

function ayuda
    echo "Uso: ./build.fish ACCIÓN"
    echo "  probar    pruebas del motor y de la app"
    echo "  debug     APK de depuración"
    echo "  instalar  instala el APK de depuración en el teléfono o emulador (adb install -r)"
    echo "  release   APK y AAB con R8 (firmados si hay keystore.properties) + prueba de humo + permisos"
    echo "  todo      probar, debug y release"
    echo "  capturas  capturas de pantalla en claro y oscuro"
    echo "  llave     crea el keystore de firma fuera del repositorio"
end

usar_jdk21
switch "$argv[1]"
    case probar
        probar
    case debug
        debug
    case instalar
        instalar
    case release
        release
    case todo
        probar; and debug; and release
    case capturas
        capturas
    case llave
        llave
    case '' ayuda -h --help
        ayuda
    case '*'
        ayuda
        falla "Acción desconocida: $argv[1]"
end
