# Compilar, probar, ejecutar y generar la carpeta portable (Windows / PowerShell).
# Uso:
#   .\build.ps1              -> menu
#   .\build.ps1 compilar
#   .\build.ps1 probar
#   .\build.ps1 ejecutar
#   .\build.ps1 portable
#   .\build.ps1 todo         -> compilar + probar + portable
# Los instaladores .exe y .msi solo se generan desde Linux (ver README).
param(
    [Parameter(Position = 0)]
    [ValidateSet('', 'compilar', 'probar', 'ejecutar', 'portable', 'todo')]
    [string]$Accion = ''
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$sln = 'CalculadoraDeSalarios.sln'
$app = 'CalculadoraDeSalarios.App'
$tst = 'CalculadoraDeSalarios.Tests'
$rid = 'win-x64'

function Paso($texto) { Write-Host "==> $texto" -ForegroundColor Cyan }
function Falla($texto) { Write-Host "Error: $texto" -ForegroundColor Red }

# Ejecuta un comando nativo mostrando su salida y devuelve $true si termino bien.
function Ejecutar-Dotnet {
    & dotnet @args | Out-Host
    return ($LASTEXITCODE -eq 0)
}

function Verificar {
    if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
        Falla "Falta el SDK de .NET 10: winget install Microsoft.DotNet.SDK.10 (y abre una terminal nueva)"
        return $false
    }
    $mayor = [int]((& dotnet --version).Split('.')[0])
    if ($mayor -lt 10) {
        Falla "Se necesita el SDK de .NET 10 o superior (tienes $mayor)"
        return $false
    }
    if (-not ((Test-Path "$app\Assets\icon.ico") -and (Test-Path "$app\Assets\icon.png"))) {
        Falla "Faltan los iconos. Ejecuta primero: .\icono.ps1 C:\ruta\a\tu-logo.png"
        return $false
    }
    return $true
}

function Compilar {
    if (-not (Verificar)) { return $false }
    Paso 'Compilando'
    return (Ejecutar-Dotnet build $sln -c Release)
}

function Probar {
    if (-not (Verificar)) { return $false }
    Paso 'Probando'
    return (Ejecutar-Dotnet test $tst -c Release)
}

function Ejecutar {
    if (-not (Verificar)) { return $false }
    return (Ejecutar-Dotnet run --project $app -c Release)
}

function Portable {
    if (-not (Verificar)) { return $false }
    Paso "Publicando carpeta portable ($rid)"
    $ok = Ejecutar-Dotnet publish $app -c Release -r $rid --self-contained true -o "dist\portable-$rid"
    if ($ok) { Write-Host "Listo: dist\portable-$rid" -ForegroundColor Green }
    return $ok
}

function Todo {
    return ((Compilar) -and (Probar) -and (Portable))
}

function Menu {
    while ($true) {
        Write-Host ''
        Write-Host '1) Compilar'
        Write-Host '2) Probar'
        Write-Host '3) Ejecutar'
        Write-Host '4) Carpeta portable'
        Write-Host '5) Todo (compilar + probar + portable)'
        Write-Host 'q) Salir'
        $o = Read-Host 'Opcion'
        switch ($o) {
            '1' { [void](Compilar) }
            '2' { [void](Probar) }
            '3' { [void](Ejecutar) }
            '4' { [void](Portable) }
            '5' { [void](Todo) }
            'q' { return }
        }
    }
}

switch ($Accion) {
    'compilar' { if (-not (Compilar)) { exit 1 } }
    'probar'   { if (-not (Probar))   { exit 1 } }
    'ejecutar' { if (-not (Ejecutar)) { exit 1 } }
    'portable' { if (-not (Portable)) { exit 1 } }
    'todo'     { if (-not (Todo))     { exit 1 } }
    default    { Menu }
}
