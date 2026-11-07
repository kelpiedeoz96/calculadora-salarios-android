#!/usr/bin/env fish
set -l raiz (path resolve (dirname (status filename))/..)
set -l vers 1.0.0
if test (count $argv) -ge 1
    set vers $argv[1]
end
if not string match -qr '^\d+\.\d+\.\d+$' -- $vers
    echo "❌ La versión debe tener formato x.y.z (por ejemplo 1.0.0). Recibí: $vers"
    exit 1
end

cd $raiz; or exit 1

set -l faltan
for cmd in dotnet makensis wixl wixl-heat
    command -q $cmd; or set -a faltan $cmd
end
if test (count $faltan) -gt 0
    echo "❌ Faltan: $faltan"
    echo "   Instala con: sudo pacman -S nsis msitools dotnet-sdk"
    exit 1
end

set -l pub $raiz/artifacts/publish-win-x64
set -l out $raiz/dist/windows
rm -rf $pub $out
mkdir -p $out

echo "Publicando la app (win-x64, autocontenida)…"
dotnet publish CalculadoraDeSalarios.App/CalculadoraDeSalarios.App.csproj \
    -c Release -r win-x64 --self-contained true \
    -p:Version=$vers -p:DebugType=none -p:DebugSymbols=false \
    -o $pub
or begin
    echo "❌ Falló dotnet publish (revisa el error de arriba)."
    exit 1
end

echo "→ Creando el instalador .exe (NSIS)…"
makensis -V2 -DVERSION=$vers -DPUBLISHDIR=$pub \
    -DICON=$raiz/packaging/windows/icon.ico \
    -DOUTFILE=$out/NominaApp-Setup-$vers.exe \
    packaging/windows/installer.nsi
or begin
    echo "❌ Falló makensis."
    exit 1
end

echo "Creando el paquete .msi (wixl)…"
set -l archivos $raiz/artifacts/Files.wxs

find artifacts/publish-win-x64 -type f | LC_ALL=C sort | env LANG=C.UTF-8 LC_ALL=C.UTF-8 wixl-heat \
    -p artifacts/publish-win-x64/ \
    --directory-ref INSTALLDIR \
    --component-group ArchivosApp \
    --var var.SourceDir > $archivos

or begin
    echo "❌ Falló wixl-heat."
    exit 1
end
wixl -a x64 \
    -D Version=$vers \
    -D SourceDir=artifacts/publish-win-x64 \
    -D IconPath=packaging/windows/icon.ico \
    -o dist/windows/NominaApp-$vers.msi \
    packaging/windows/Product.wxs \
    artifacts/Files.wxs
or begin
    echo "❌ Falló wixl."
    exit 1
end

echo
echo "✅ Listo. Instaladores en $out:"
ls -lh $out
