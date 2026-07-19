param([string]$Environment = "production")

$artifact = Join-Path $PSScriptRoot "app.apk"
Write-Host "Deploying $artifact to $Environment"
