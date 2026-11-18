# Baut how und installiert es nach ~/.local/share/how (Programm) und ~/.local/bin (Befehl, fuer PowerShell, cmd und Git Bash).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$share = Join-Path $HOME '.local\share\how'
$bin = Join-Path $HOME '.local\bin'
New-Item -ItemType Directory -Force out, $share, $bin | Out-Null

& scala-cli --power package . --assembly -o out\how.jar -f
if ($LASTEXITCODE -ne 0) { throw 'scala-cli package ist fehlgeschlagen.' }
Copy-Item out\how.jar (Join-Path $share 'how.jar') -Force
Copy-Item launcher\how.cmd, launcher\how $bin -Force
Write-Host "Installiert: $(Join-Path $bin 'how.cmd')"
if (-not (($env:PATH -split ';') -contains $bin)) {
    Write-Host "Hinweis: $bin ist nicht im PATH."
}
