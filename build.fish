#!/usr/bin/env fish
cd (status dirname)
set sln CalculadoraDeSalarios.sln
set app CalculadoraDeSalarios.App
set tst CalculadoraDeSalarios.Tests
set so (uname -s)
set arq (uname -m)
set -g vers ""
function paso
  set_color cyan
  echo "==> $argv"
  set_color normal
end
function falla
  set_color red
  echo "Error: $argv" >&2
  set_color normal
  return 1
end
function verificar
  command -q dotnet; or begin
    falla "Falta el SDK de .NET 10: https://dotnet.microsoft.com/download"
    return 1
  end
  test (dotnet --version | string split -f1 .) -ge 10; or begin
    falla "Se necesita el SDK de .NET 10 o superior"
    return 1
  end
  test -f CalculadoraDeSalarios.App/Assets/icon.ico -a -f CalculadoraDeSalarios.App/Assets/icon.png; or begin
    falla "Faltan los íconos. Ejecuta primero: ./icono.fish ~/ruta/a/tu-logo.png"
    return 1
  end
end
function version_ok
  string match -qr '^\d+\.\d+\.\d+$' -- $argv[1]
end
function pedir_version
  read -l -P "Versión (x.y.z) [1.0.0]: " v
  test -n "$v"; or set v 1.0.0
  version_ok $v; or begin
    falla "La versión debe tener formato x.y.z (por ejemplo 1.0.0)"
    return 1
  end
  set -g vers $v
end
function rid
  switch $so
    case Linux
      test $arq = aarch64; and echo linux-arm64; or echo linux-x64
    case Darwin
      test $arq = arm64; and echo osx-arm64; or echo osx-x64
    case '*'
      echo win-x64
  end
end
function compilar
  verificar; or return 1
  paso Compilando
  dotnet build $sln -c Release -p:UseAppHost=false
end
function probar
  verificar; or return 1
  paso Probando
  dotnet test $tst -c Release -p:UseAppHost=false
end
function ejecutar
  verificar; or return 1
  dotnet run --project $app -c Release -p:UseAppHost=false
end
function portable
  verificar; or return 1
  set r (rid)
  paso "Publicando carpeta portable ($r)"
  dotnet publish $app -c Release -r $r --self-contained true -o dist/portable-$r
  and echo "Listo: dist/portable-$r"
end
function empaquetar
  verificar; or return 1
  if test $so != Linux
    echo "Los instaladores se generan solo desde Linux. Se crea la carpeta portable."
    portable
    return
  end
  echo "1) Linux (deb, rpm, zst)"
  echo "2) Windows (exe, msi)"
  echo "3) Ambos"
  echo "4) Carpeta portable"
  read -P "Opción: " o
  switch $o
    case 1 2 3
      test -n "$vers"; or pedir_version; or return 1
  end
  switch $o
    case 1
      fish packaging/build-linux.fish $vers
    case 2
      fish packaging/build-windows.fish $vers
    case 3
      fish packaging/build-linux.fish $vers; and fish packaging/build-windows.fish $vers
    case 4
      portable
    case '*'
      falla "Opción inválida"
  end
end
function todo
  compilar; and probar; and empaquetar
end
function menu
  while true
    echo "1) Compilar"
    echo "2) Probar"
    echo "3) Ejecutar"
    echo "4) Empaquetar"
    echo "5) Todo"
    echo "q) Salir"
    read -P "Opción: " o
    switch $o
      case 1
        compilar
      case 2
        probar
      case 3
        ejecutar
      case 4
        empaquetar
      case 5
        todo
      case q
        return
    end
  end
end
if test (count $argv) -gt 1
  version_ok $argv[2]; or begin
    falla "La versión debe tener formato x.y.z (por ejemplo 1.0.0)"
    exit 1
  end
  set -g vers $argv[2]
end
if test (count $argv) -gt 0
  switch $argv[1]
    case compilar probar ejecutar empaquetar portable todo
      $argv[1]
    case '*'
      falla "Uso: ./build.fish [compilar|probar|ejecutar|empaquetar|portable|todo] [x.y.z]"
  end
else
  menu
end
