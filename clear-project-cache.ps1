<# 
 Script para borrar la caché local del proyecto Temi Deamon DB.
 - Elimina:
   - .gradle, .kotlin y build del proyecto
   - app\build (por si existe)
   - C:\Users\<usuario>\TemiDeamonDBBuild\app (buildDir del módulo app)

 Uso:
   Desde cualquier PowerShell:
     .\clear-project-cache.ps1
#>

$ErrorActionPreference = "Stop"

# Ir a la carpeta del proyecto (donde está este script)
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Borrando caché local del proyecto" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$paths = @(
    ".gradle",
    ".kotlin",
    "build",
    "app\build"
)

# buildDir externo configurado en app/build.gradle.kts
$externalAppBuild = Join-Path $env:USERPROFILE "TemiDeamonDBBuild\app"
$paths += $externalAppBuild

foreach ($p in $paths) {
    $fullPath = if ([System.IO.Path]::IsPathRooted($p)) { $p } else { Join-Path $ProjectRoot $p }

    if (Test-Path $fullPath) {
        Write-Host "Borrando: $fullPath" -ForegroundColor Yellow
        Remove-Item $fullPath -Recurse -Force
    } else {
        Write-Host "No existe: $fullPath (ok)" -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "✅ Caché local del proyecto eliminada." -ForegroundColor Green
Write-Host ""
Write-Host "Nota: si necesitas limpiar la caché global de Gradle," -ForegroundColor DarkYellow
Write-Host "      puedes borrar manualmente: $env:USERPROFILE\.gradle\caches (afecta a todos los proyectos)." -ForegroundColor DarkYellow
Write-Host ""

