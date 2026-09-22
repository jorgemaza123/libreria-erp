param(
    [int]$Puerto = 8443,
    [switch]$SoloRedPrivada
)

$ruleName = "Sistema Libreria HTTPS $Puerto"
$profiles = if ($SoloRedPrivada) { @("Private", "Domain") } else { @("Any") }
$profilesText = $profiles -join ","

Write-Host "Configurando Windows Firewall para el puerto TCP $Puerto..."
Write-Host "Regla: $ruleName"
Write-Host "Perfiles: $profilesText"

$existingRule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue

if ($existingRule) {
    Set-NetFirewallRule -DisplayName $ruleName -Enabled True -Action Allow -Profile $profiles
    Set-NetFirewallRule -DisplayName $ruleName -Direction Inbound
    Write-Host "Regla existente actualizada correctamente."
} else {
    New-NetFirewallRule `
        -DisplayName $ruleName `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort $Puerto `
        -Profile $profiles | Out-Null
    Write-Host "Regla nueva creada correctamente."
}

Write-Host ""
Write-Host "Prueba desde esta PC:"
Write-Host "Test-NetConnection 127.0.0.1 -Port $Puerto"
Write-Host ""
Write-Host "Luego pruebe desde el celular con el QR de Conexion Movil."
