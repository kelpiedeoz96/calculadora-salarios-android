# Calculadora de Salarios

Aplicación de escritorio hecha con Avalonia y .NET 10 para liquidar nómina por horas: recargos nocturnos, horas extra, dominicales y festivos, auxilio de transporte y descuentos de salud y pensión. Exporta a PDF y Excel. La lógica está pensada para la nómina colombiana.

> **Pensado para Linux.** Los scripts de compilación, pruebas y empaquetado están escritos en [Fish](https://fishshell.com/) y se probaron en Linux (CachyOS). En Windows hay scripts equivalentes en PowerShell (`build.ps1` e `icono.ps1`, sin probar) y también puedes usar los comandos de `dotnet` a mano (sección Windows). En macOS no se ha probado.

| Sistema | Estado | Cómo |
|---|---|---|
| Linux | Probado | Scripts `build.fish` e `icono.fish` |
| Windows | Compila y corre | Scripts `build.ps1` e `icono.ps1` (sin probar) o comandos manuales con `dotnet` |
| macOS | Sin probar | Comandos manuales con `dotnet` |

## Requisitos

- SDK de .NET 10 (el SDK, no solo el runtime): https://dotnet.microsoft.com/download
- Solo Linux: `fish` para los scripts e `imagemagick` para cambiar el ícono

Arch y derivadas:

```fish
sudo pacman -S dotnet-sdk fish imagemagick
```

En otras distribuciones instala los mismos paquetes con tu gestor.

## Linux

Clona el repo y entra a la carpeta:

```fish
git clone https://github.com/kelpiedeoz96/calculadora-salarios-colombia.git
cd calculadora-salarios-colombia
chmod +x build.fish icono.fish
```

Antes de compilar por primera vez, genera los íconos con tu logo (PNG, JPG o WebP de al menos 512×512 px):

```fish
./icono.fish ~/Imágenes/mi-logo.png
```

### Compilar, probar y ejecutar

```fish
./build.fish
```

Sin argumentos abre un menú: compilar, probar, ejecutar, empaquetar o todo. También puedes llamar la acción directa:

```fish
./build.fish compilar
./build.fish probar
./build.fish ejecutar
./build.fish empaquetar
./build.fish portable
./build.fish todo
```

`todo` compila, prueba y empaqueta, y se detiene si algo falla.

### Generar paquetes

`./build.fish empaquetar` pregunta qué generar. Los archivos quedan en `dist/`:

- **Linux:** `.deb`, `.rpm` y `.pkg.tar.zst` (Arch). No hace falta instalar nada más: el script descarga `nfpm` solo.
- **Windows:** `.exe` y `.msi`, armados desde Linux. Requieren `nsis` y `msitools`:

```fish
sudo pacman -S nsis msitools
```

- **Carpeta portable:** una carpeta autocontenida en `dist/portable-<sistema>`, sin instalar .NET en el equipo destino.

`./build.fish empaquetar` pregunta la versión (formato `x.y.z`, por defecto `1.0.0`). También se puede pasar directo:

```fish
./build.fish empaquetar 1.2.0
```

Para instalar un paquete generado:

```fish
sudo pacman -U dist/linux/archivo.pkg.tar.zst
sudo apt install ./dist/linux/archivo.deb
sudo dnf install ./dist/linux/archivo.rpm
```

### Cambiar el ícono

El repo **no incluye íconos** a propósito, para que cada quien ponga el suyo. **Este paso es obligatorio antes del primer `./build.fish`**: sin los archivos de ícono la compilación falla.

Ten a la mano un logo de al menos 512×512 px (PNG, JPG o WebP):

```fish
./icono.fish ~/Imágenes/mi-logo.png
```

El script lo centra en un cuadrado sin deformarlo y regenera los cinco archivos de ícono: `icon.png` e `icon.ico` de la app, `icon-256.png` e `icon-512.png` de Linux y `icon.ico` de Windows. Después ejecuta `./build.fish empaquetar` para que los paquetes lo incluyan.

Este ícono es el de la ventana, el ejecutable y los instaladores. El logo de los PDF y Excel se elige dentro de la app, en **Ajustes → Elegir logo**.

### Probar con datos vacíos sin tocar los tuyos

```fish
env XDG_CONFIG_HOME=/tmp/cfg-prueba ./build.fish ejecutar
```

## Windows

Los scripts `.fish` no funcionan en Windows. Usa los `.ps1` o los comandos de `dotnet`, siempre desde PowerShell y dentro de la carpeta del proyecto (donde está el `.sln`).

Para poder ejecutar los `.ps1`, habilita los scripts solo en esa ventana:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

**1. Instala el SDK de .NET 10** y abre una terminal nueva:

```powershell
winget install Microsoft.DotNet.SDK.10
dotnet --version
```

Debe mostrar `10.x.x`.

**2. Genera los íconos.** El repo no los incluye y sin ellos el build falla. Instala ImageMagick (`winget install ImageMagick.ImageMagick`, y abre una terminal nueva) y ejecuta con tu logo (PNG, JPG o WebP de al menos 512×512 px):

```powershell
.\icono.ps1 C:\ruta\a\mi-logo.png
```

Si prefieres hacerlo a mano, mira el paso 5.

**3. Compila, prueba y ejecuta.** Con el menú de `build.ps1`:

```powershell
.\build.ps1
```

O directo, con `.\build.ps1 compilar`, `probar`, `ejecutar`, `portable` o `todo`. O con los comandos de `dotnet`:

```powershell
dotnet build CalculadoraDeSalarios.sln -c Release
dotnet test CalculadoraDeSalarios.Tests -c Release
dotnet run --project CalculadoraDeSalarios.App -c Release
```

**4. Genera una carpeta portable** (se puede copiar a otro equipo sin instalar .NET):

```powershell
dotnet publish CalculadoraDeSalarios.App -c Release -r win-x64 --self-contained true -o dist\portable-win-x64
.\dist\portable-win-x64\CalculadoraDeSalarios.App.exe
```

Comprime esa carpeta en un `.zip` para compartirla. Windows puede mostrar una advertencia de SmartScreen porque el ejecutable no está firmado.

**5. Cambia el ícono a mano** (alternativa a `icono.ps1`). En Windows solo importan estos dos archivos:

- `CalculadoraDeSalarios.App\Assets\icon.png`: ícono de la ventana (512×512 px recomendado).
- `CalculadoraDeSalarios.App\Assets\icon.ico`: ícono del ejecutable (debe traer varios tamaños, de 16 a 256 px).

Con ImageMagick (`winget install ImageMagick.ImageMagick`) se generan así, cambiando `logo.png` por tu archivo:

```powershell
magick logo.png -background none -gravity center -resize 1024x1024 -extent 1024x1024 base.png
magick base.png -resize 512x512 CalculadoraDeSalarios.App\Assets\icon.png
magick base.png -define icon:auto-resize=256,128,64,48,32,16 CalculadoraDeSalarios.App\Assets\icon.ico
```

Sin ImageMagick, haz el PNG cuadrado con cualquier editor y conviértelo a `.ico` con un conversor en línea. Luego vuelve a publicar.

**6. Instaladores `.exe` y `.msi`.** No se pueden generar desde Windows nativo. Hay dos caminos:

- Usar la carpeta portable del paso 4.
- Usar WSL2 con una distro Linux, donde instalas `fish`, `nsis`, `msitools` y el SDK de .NET 10, y sigues la sección Linux.

## macOS

No se ha probado. En teoría funcionan los mismos comandos manuales de la sección Windows, cambiando el `-r win-x64` por `osx-arm64` (Apple Silicon) o `osx-x64` (Intel). El ícono personalizado se ve en la ventana, pero no en el Dock: para eso hace falta armar un paquete `.app` con un `.icns`, y eso no está hecho. Si lo pruebas, abre un issue contando cómo te fue.

## Primer arranque

La app inicia vacía, con la empresa "Mi Empresa" y un empleado de ejemplo. En **Ajustes** puedes poner:

- Razón social y NIT.
- Logo para los reportes.
- SMMLV, auxilio de transporte, porcentajes de recargo, salud y pensión.
- Turnos rápidos y festivos.

Los empleados se agregan desde la pantalla **Empleados**.

**Actualiza cada año** el SMMLV, el auxilio de transporte y los festivos. Los valores iniciales son los de 2026.

## Dónde se guardan los datos

| Sistema | Carpeta |
|---|---|
| Linux | `~/.config/NominaApp` |
| Windows | `%APPDATA%\NominaApp` |

Ahí están `data.json`, el logo, las fotos de los empleados y una carpeta `respaldos` con copias automáticas. Para empezar de cero, borra esa carpeta. Perderás todos los datos, así que haz una copia antes.

## Estructura

```
CalculadoraDeSalarios.Core     lógica de cálculo, guardado y exportación (sin pantallas)
CalculadoraDeSalarios.App      interfaz Avalonia (vistas, viewmodels, controles, temas)
CalculadoraDeSalarios.Tests    pruebas automáticas (xUnit) y datos de prueba
packaging                      scripts e insumos para generar los instaladores
build.fish                     compilar, probar, ejecutar y empaquetar (Linux)
icono.fish                     genera los íconos desde tu logo (Linux)
build.ps1                      compilar, probar, ejecutar y portable (Windows, sin probar)
icono.ps1                      genera los íconos desde tu logo (Windows, sin probar)
```

## Problemas comunes

- **`NU1101 ... Microsoft.NETCore.App.Host.arch-x64`** (Arch y derivadas): agrega `-p:UseAppHost=false` al comando de `dotnet`. `build.fish` ya lo hace.
- **Versión del SDK:** el proyecto apunta a `net10.0`. Con un SDK anterior no compila.
- **Los paquetes `.exe` y `.msi` fallan:** revisa que estén instalados `nsis` y `msitools`.
- **El selector de logo solo muestra PNG y JPG:** son los formatos admitidos.

## Aviso

Esta herramienta ayuda a calcular y no reemplaza la asesoría de un contador o abogado laboralista. Verifica los resultados con la normativa vigente antes de pagar una nómina.

## Licencia

MIT. Mira el archivo [LICENSE](LICENSE).

Los PDF se generan con [QuestPDF](https://www.questpdf.com/) bajo su licencia Community, que no es MIT: es gratis para personas y empresas pequeñas, pero si tu organización supera el umbral de ingresos que fija QuestPDF necesitas una licencia comercial. Revisa sus condiciones antes de usarlo en una empresa.
