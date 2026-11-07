# Genera los iconos de la app a partir de tu logo (Windows / PowerShell).
# Uso:  .\icono.ps1 C:\ruta\a\mi-logo.png
# Requiere ImageMagick:  winget install ImageMagick.ImageMagick  (y abrir una terminal nueva)
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Logo
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Logo -PathType Leaf)) {
    Write-Host "No existe: $Logo" -ForegroundColor Red
    exit 1
}
$src = (Resolve-Path -LiteralPath $Logo).Path

if (-not (Get-Command magick -ErrorAction SilentlyContinue)) {
    Write-Host "Falta ImageMagick. Instalalo con: winget install ImageMagick.ImageMagick (y abre una terminal nueva)" -ForegroundColor Red
    exit 1
}

Set-Location -LiteralPath $PSScriptRoot

$appAssets = 'CalculadoraDeSalarios.App\Assets'
$pkgLinux  = 'packaging\linux'
$pkgWin    = 'packaging\windows'
foreach ($d in @($appAssets, $pkgLinux, $pkgWin)) {
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("icono_" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$base = Join-Path $tmp 'base.png'

function Invoke-Magick {
    & magick @args
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ImageMagick fallo (codigo $LASTEXITCODE)" -ForegroundColor Red
        Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
        exit 1
    }
}

try {
    # Cuadrado 1024x1024 sin deformar el logo, fondo transparente
    Invoke-Magick "${src}[0]" -background none -gravity center -resize 1024x1024 -extent 1024x1024 $base

    Invoke-Magick $base -resize 512x512 "$appAssets\icon.png"
    Invoke-Magick $base -resize 512x512 "$pkgLinux\icon-512.png"
    Invoke-Magick $base -resize 256x256 "$pkgLinux\icon-256.png"
    Invoke-Magick $base -define 'icon:auto-resize=256,128,64,48,32,16' "$appAssets\icon.ico"
    Copy-Item -Force "$appAssets\icon.ico" "$pkgWin\icon.ico"
}
finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

Write-Host "Iconos generados" -ForegroundColor Green
