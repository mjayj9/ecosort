# Hidden input -> temporary process environment -> Firebase Secret. Never writes a key file.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$secureKey = Read-Host 'NVIDIA API key (hidden; Firebase Secret only)' -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
try { $env:NVIDIA_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer); $secureKey.Dispose() }
try {
    node .\functions\scripts\set-nvidia-secret.js
    if ($LASTEXITCODE -ne 0) { throw 'Firebase Secret setup failed.' }
} finally { Remove-Item Env:NVIDIA_API_KEY -ErrorAction SilentlyContinue }
