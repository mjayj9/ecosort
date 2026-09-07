param([Parameter(Mandatory=$true)][string]$Source)
$ErrorActionPreference = 'Stop'
$json = Get-Content -LiteralPath $Source -Raw | ConvertFrom-Json
if ($json.project_info.project_id -ne 'focused-rig-vcf5x') { throw 'Expected the current EcoSort Firebase project configuration.' }
if (-not ($json.client | Where-Object { $_.client_info.android_client_info.package_name -eq 'com.aistudio.ecosort.kxmpzq' })) { throw 'EcoSort Android application is missing.' }
$destination = Join-Path $PSScriptRoot 'app\google-services.json'
[IO.File]::WriteAllText($destination, (Get-Content -LiteralPath $Source -Raw), [Text.UTF8Encoding]::new($false))
Write-Output 'Local Firebase configuration installed. Do not commit this file.'
