#!/usr/bin/env fish
set -l raiz (path resolve (dirname (status filename))/..)
set -l vers 1.0.0
if test (count $argv) -ge 1
    set vers $argv[1]
end
if not string match -qr '^\d+\.\d+\.\d+$' -- $vers
    echo "La versión debe tener formato x.y.z (por ejemplo 1.0.0). Recibí: $vers"
    exit 1
end

cd $raiz; or exit 1

if not command -q dotnet
    echo "Falta dotnet. Instálalo con: sudo pacman -S dotnet-sdk"
    exit 1
end

set -l nfpm nfpm
if not command -q nfpm
    set nfpm $raiz/artifacts/tools/nfpm
    if not test -x $nfpm
        echo "→ nfpm no está instalado; descargando una copia local (v2.41.3)…"
        mkdir -p artifacts/tools
        curl -fsSL -o artifacts/tools/nfpm.tar.gz https://github.com/goreleaser/nfpm/releases/download/v2.41.3/nfpm_2.41.3_Linux_x86_64.tar.gz; or begin
            echo "❌ No pude bajar nfpm. Instálalo con: paru -S nfpm   (o yay -S nfpm)"
            exit 1
        end
        tar -xzf artifacts/tools/nfpm.tar.gz -C artifacts/tools nfpm; or exit 1
    end
end

set -l pub artifacts/publish-linux-x64
rm -rf $pub dist/linux
mkdir -p dist/linux

echo "Publicando la app (linux-x64, autocontenida)…"
dotnet publish CalculadoraDeSalarios.App/CalculadoraDeSalarios.App.csproj \
    -c Release -r linux-x64 --self-contained true \
    -p:Version=$vers -p:DebugType=none -p:DebugSymbols=false \
    -o $pub
or begin
    echo "Falló dotnet publish (revisa el error de arriba)."
    exit 1
end
chmod +x $pub/CalculadoraDeSalarios.App

set -lx VERSION $vers
for formato in deb rpm archlinux
    echo "→ Empaquetando $formato…"
    $nfpm package -f packaging/nfpm.yaml -p $formato -t dist/linux/; or exit 1
end

echo
echo "Listo. Paquetes en $raiz/dist/linux:"
ls -lh dist/linux
echo
echo "Instalar en CachyOS/Arch:   sudo pacman -U dist/linux/nomina-app-$vers-1-x86_64.pkg.tar.zst"
echo "Instalar en Debian/Ubuntu:  sudo apt install ./dist/linux/nomina-app_"$vers"_amd64.deb"
echo "Instalar en Fedora/openSUSE: sudo dnf install ./dist/linux/nomina-app-$vers-1.x86_64.rpm"
