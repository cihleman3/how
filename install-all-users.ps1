# Installs how for all users of this computer into C:\Program Files\how and adds the folder to the system PATH.
# Must run in a PowerShell with administrator rights.
#
# Build it first as a normal user (scala-cli is usually installed for that user only):
#   scala-cli --power package . --assembly -o out\how.jar -f
#
# With -KeyFile, the OpenRouter key from the file is set machine-wide. All users then share it:
# everyone spends the same credit, and every user can read the key.
#
# Every user needs Java 17 or newer, via JAVA_HOME or on the PATH.

param(
    [string]$KeyFile,
    [string]$Target = (Join-Path $env:ProgramFiles 'how')
)

$ErrorActionPreference = 'Stop'

$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Please run this in a PowerShell with administrator rights ("Run as administrator").'
}

$jar = Join-Path $PSScriptRoot 'out\how.jar'
if (-not (Test-Path $jar)) {
    throw "$jar is missing. Build it first as a normal user: scala-cli --power package . --assembly -o out\how.jar -f"
}

# Program and launchers
New-Item -ItemType Directory -Force $Target | Out-Null
Copy-Item $jar, (Join-Path $PSScriptRoot 'launcher\how.cmd'), (Join-Path $PSScriptRoot 'launcher\how') $Target -Force
Write-Host "Installed: $Target"

# System PATH. Written directly to the registry so that entries like %SystemRoot% stay unexpanded (REG_EXPAND_SZ).
$environment = [Microsoft.Win32.Registry]::LocalMachine.OpenSubKey(
    'SYSTEM\CurrentControlSet\Control\Session Manager\Environment', $true)
$path = $environment.GetValue('Path', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames)
if (($path -split ';') -notcontains $Target) {
    $environment.SetValue('Path', ($path.TrimEnd(';') + ';' + $Target), [Microsoft.Win32.RegistryValueKind]::ExpandString)
    Write-Host "Added to the system PATH: $Target"
} else {
    Write-Host "Already on the system PATH: $Target"
}
$environment.Close()

# Key for all users
if ($KeyFile) {
    $key = (Get-Content $KeyFile -Raw).Trim()
    if (-not $key.StartsWith('sk-or-')) { throw "$KeyFile does not contain an OpenRouter key (sk-or-...)." }
    [Environment]::SetEnvironmentVariable('OPENROUTER_API_KEY', $key, 'Machine')
    Write-Host 'OPENROUTER_API_KEY set for all users.'
}

# Tell running programs (Explorer, Start menu) about the changed environment, so that new windows see it.
Add-Type -Namespace HowInstall -Name Native -MemberDefinition @'
[DllImport("user32.dll", CharSet = CharSet.Unicode)]
public static extern System.IntPtr SendMessageTimeout(System.IntPtr hWnd, uint msg, System.UIntPtr wParam,
    string lParam, uint flags, uint timeout, out System.UIntPtr result);
'@
$result = [UIntPtr]::Zero
[HowInstall.Native]::SendMessageTimeout([IntPtr]0xffff, 0x1A, [UIntPtr]::Zero, 'Environment', 2, 5000, [ref]$result) | Out-Null

Write-Host 'Done. New terminal windows will find how; windows that are already open will not.'
