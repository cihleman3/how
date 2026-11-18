# Installiert how fuer alle Benutzer dieses Rechners nach C:\Program Files\how und nimmt den Ordner in den
# System-PATH auf. Muss in einer PowerShell mit Administratorrechten laufen.
#
# Vorher als normaler Benutzer bauen (scala-cli ist meist nur fuer diesen Benutzer installiert):
#   scala-cli --power package . --assembly -o out\how.jar -f
#
# Mit -KeyFile wird der OpenRouter-Schluessel aus der Datei rechnerweit gesetzt. Dann teilen ihn alle Benutzer:
# Alle verbrauchen dasselbe Guthaben, und jeder Benutzer kann den Schluessel lesen.
#
# Voraussetzung fuer jeden Benutzer: Java 17 oder neuer, ueber JAVA_HOME oder im PATH.

param(
    [string]$KeyFile,
    [string]$Target = (Join-Path $env:ProgramFiles 'how')
)

$ErrorActionPreference = 'Stop'

$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Bitte in einer PowerShell mit Administratorrechten ausfuehren ("Als Administrator ausfuehren").'
}

$jar = Join-Path $PSScriptRoot 'out\how.jar'
if (-not (Test-Path $jar)) {
    throw "$jar fehlt. Erst als normaler Benutzer bauen: scala-cli --power package . --assembly -o out\how.jar -f"
}

# Programm und Startskripte
New-Item -ItemType Directory -Force $Target | Out-Null
Copy-Item $jar, (Join-Path $PSScriptRoot 'launcher\how.cmd'), (Join-Path $PSScriptRoot 'launcher\how') $Target -Force
Write-Host "Installiert: $Target"

# System-PATH. Direkt in der Registry, damit Eintraege wie %SystemRoot% unaufgeloest (REG_EXPAND_SZ) bleiben.
$environment = [Microsoft.Win32.Registry]::LocalMachine.OpenSubKey(
    'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', $true)
$path = $environment.GetValue('Path', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
if (($path -split ';') -notcontains $Target) {
    $environment.SetValue('Path', ($path.TrimEnd(';') + ';' + $Target), [Microsoft.Win32.RegistryValueKind]::ExpandString)
    Write-Host "In den System-PATH aufgenommen: $Target"
} else {
    Write-Host "Schon im System-PATH: $Target"
}
$environment.Close()

# Schluessel fuer alle Benutzer
if ($KeyFile) {
    $key = (Get-Content $KeyFile -Raw).Trim()
    if (-not $key.StartsWith('sk-or-')) { throw "$KeyFile enthaelt keinen OpenRouter-Schluessel (sk-or-...)." }
    [Environment]::SetEnvironmentVariable('OPENROUTER_API_KEY', $key, 'Machine')
    Write-Host 'OPENROUTER_API_KEY fuer alle Benutzer gesetzt.'
}

# Laufenden Programmen (Explorer, Startmenue) die geaenderte Umgebung melden, damit neue Fenster sie sehen.
Add-Type -Namespace HowInstall -Name Native -MemberDefinition @'
[DllImport("user32.dll", CharSet = CharSet.Unicode)]
public static extern System.IntPtr SendMessageTimeout(System.IntPtr hWnd, uint msg, System.UIntPtr wParam,
    string lParam, uint flags, uint timeout, out System.UIntPtr result);
'@
$result = [UIntPtr]::Zero
[HowInstall.Native]::SendMessageTimeout([IntPtr]0xffff, 0x1A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]$result) | Out-Null

Write-Host 'Fertig. Neue Terminalfenster finden how; bereits offene nicht.'
