# Test C - real background geofence.
#
# Preconditions (do these by hand first):
#   - User is OUTSIDE, e.g. 113.373500, 23.067762.
#   - In ANEN create a reminder at target 113.376960, 23.067762, radius 200 m.
#   - Confirm the app shows distance > 200 m and the reminder is armed.
#   - Go back to HOME. Do NOT reopen or force-stop the app.
#
# Then this script moves the emulator into the fence and waits for the
# GEOFENCE EVENT / ALERT timestamps in logcat.

. "$PSScriptRoot\emulator-location.ps1"

$adb = Get-AnenAdb
$device = Get-AnenDevice
$timeoutSeconds = 120

Write-Host "=== Test C: background geofence ==="
Write-Host "Make sure an armed reminder exists (target 113.376960, 23.067762, r=200m)."
Clear-AnenLog

Write-Host "Go HOME (background)."
& $adb -s $device shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 2

Write-Host "Move to fence center 113.376960, 23.067762"
Set-EmulatorLocation -Longitude 113.376960 -Latitude 23.067762 -WaitSeconds 0

Write-Host "Waiting up to $timeoutSeconds s for GEOFENCE EVENT ..."
$elapsed = 0
$found = $false
while (-not $found -and $elapsed -lt $timeoutSeconds) {
    Start-Sleep -Seconds 5
    $elapsed += 5
    $log = Get-AnenLocationLog
    if ($log | Select-String "GEOFENCE EVENT") { $found = $true }
    else { Write-Host "  ... ${elapsed}/${timeoutSeconds} s" }
}

Write-Host ""
if ($found) {
    Write-Host "GEOFENCE EVENT received. Compare its timestamp with ALERT START / ALERT DELIVERED."
} else {
    Write-Host "No GEOFENCE EVENT within $timeoutSeconds s."
    Write-Host "=> Investigate GeofencingClient / PendingIntent / permissions / GMS before touching HomeScreen."
}
Write-Host "--- geofence / alert log ---"
Show-AnenLocationLog
