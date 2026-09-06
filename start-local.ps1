# Run from a PowerShell terminal. Secret input is hidden and never written to a file.
param(
    [switch]$WithoutKey,
    [ValidateSet("moonshotai/kimi-k3", "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning")]
    [string]$Model = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning"
)
$ErrorActionPreference = 'Stop'
$env:NVIDIA_MODEL = $Model
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
if ($WithoutKey) { Remove-Item Env:NVIDIA_API_KEY -ErrorAction SilentlyContinue }
if (-not $WithoutKey -and -not $env:NVIDIA_API_KEY) {
    $secureKey = Read-Host 'NVIDIA API key (hidden, server process only)' -AsSecureString
    $keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
    try { $env:NVIDIA_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer); $secureKey.Dispose() }
}
try {
    $firebaseCommand = Join-Path $env:APPDATA 'npm\firebase.cmd'
    if (-not (Test-Path -LiteralPath $firebaseCommand)) { $firebaseCommand = 'firebase.cmd' }
    & $firebaseCommand emulators:start --only auth,functions,firestore --project demo-ecosort --config firebase.local.json
} finally {
    Remove-Item Env:NVIDIA_API_KEY -ErrorAction SilentlyContinue
    Remove-Item Env:NVIDIA_MODEL -ErrorAction SilentlyContinue
}
