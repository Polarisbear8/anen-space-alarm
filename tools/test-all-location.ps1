# One-shot runner for the location/geofence tests.
# Run Test A and Test B first. If A fails, do not move on to geofence.

Write-Host "=== ANEN LOCATION TEST ==="

Write-Host ""
Write-Host "[1] Foreground refresh"
& "$PSScriptRoot\test-location-refresh.ps1"

Write-Host ""
Write-Host "[2] Background -> foreground"
& "$PSScriptRoot\test-background-refresh.ps1"

Write-Host ""
Write-Host "[3] Geofence"
Write-Host "Geofence test requires an armed reminder."
Write-Host "Create one manually, then run test-geofence.ps1."

Write-Host ""
Write-Host "=== TEST FINISHED ==="
