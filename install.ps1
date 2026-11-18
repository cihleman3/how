# Builds how and installs it into ~/.local/share/how (program) and ~/.local/bin (command, for PowerShell, cmd and Git Bash).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$share = Join-Path $HOME '.local\share\how'
$bin = Join-Path $HOME '.local\bin'
New-Item -ItemType Directory -Force out, $share, $bin | Out-Null

& scala-cli --power package . --assembly -o out\how.jar -f
if ($LASTEXITCODE -ne 0) { throw 'scala-cli package failed.' }
Copy-Item out\how.jar (Join-Path $share 'how.jar') -Force
Copy-Item launcher\how.cmd, launcher\how $bin -Force
Write-Host "Installed: $(Join-Path $bin 'how.cmd')"
if (-not (($env:PATH -split ';') -contains $bin)) {
    Write-Host "Note: $bin is not on the PATH."
}
