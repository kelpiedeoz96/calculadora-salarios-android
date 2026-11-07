#!/usr/bin/env fish
cd (status dirname)
if test (count $argv) -lt 1
  echo "Uso: ./icono.fish ruta/al/logo.png"
  exit 1
end
set src $argv[1]
test -f $src; or begin
  echo "No existe: $src"
  exit 1
end
set im magick
command -q magick; or set im convert
command -q $im; or begin
  echo "Falta ImageMagick"
  exit 1
end
set tmp (mktemp -d)
$im "$src"'[0]' -background none -gravity center -resize 1024x1024 -extent 1024x1024 $tmp/base.png; or exit 1
$im $tmp/base.png -resize 512x512 CalculadoraDeSalarios.App/Assets/icon.png
$im $tmp/base.png -resize 512x512 packaging/linux/icon-512.png
$im $tmp/base.png -resize 256x256 packaging/linux/icon-256.png
$im $tmp/base.png -define icon:auto-resize=256,128,64,48,32,16 CalculadoraDeSalarios.App/Assets/icon.ico
cp CalculadoraDeSalarios.App/Assets/icon.ico packaging/windows/icon.ico
rm -rf $tmp
echo "Íconos generados"
