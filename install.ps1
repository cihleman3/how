# Installs how into ~/.local/share/how (program) and ~/.local/bin (command, for PowerShell, cmd and Git Bash).
# Uses how.jar next to this script (release download); otherwise builds it with scala-cli (git clone).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (Test-Path how.jar) {
    $jar = 'how.jar'
} else {
    New-Item -ItemType Directory -Force out | Out-Null
    & scala-cli --power package . --assembly -o out\how.jar -f
    if ($LASTEXITCODE -ne 0) { throw 'scala-cli package failed.' }
    $jar = 'out\how.jar'
}
$launchers = if (Test-Path launcher -PathType Container) { 'launcher' } else { '.' }

$share = Join-Path $HOME '.local\share\how'
$bin = Join-Path $HOME '.local\bin'
New-Item -ItemType Directory -Force $share, $bin | Out-Null
Copy-Item $jar (Join-Path $share 'how.jar') -Force
Copy-Item (Join-Path $launchers 'how.cmd'), (Join-Path $launchers 'how') $bin -Force
Write-Host "Installed: $(Join-Path $bin 'how.cmd')"
if (-not (($env:PATH -split ';') -contains $bin)) {
    Write-Host "Note: $bin is not on the PATH."
}
