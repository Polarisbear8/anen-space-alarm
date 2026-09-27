# Test B - background -> foreground recovery. This is the core bug test.
#
# Preconditions:
#   - ANEN is on Home, foreground, Developer Mode ON.
#
# Flow: A -> HOME -> move emulator to B -> reopen ANEN.
# Expected: on resume the app immediately rebuilds the location request and a
# fresh fix shows B. Must NOT require force-stop / clear-task / reinstall.

. "$PSScriptRoot\emulator-location.ps1"

$adb = Get-AnenAdb
$device = Get-AnenDevice

Write-Host "=== Test B: background -> foreground ==="
Clear-AnenLog

Write-Host "[A] OUTSIDE 113.373500, 23.067762"
Set-EmulatorLocation -Longitude 113.373500 -Latitude 23.067762 -WaitSeconds 5

Write-Host "Go HOME (normal background)."
& $adb -s $device shell input keyevent KEYCODE_HOME
Start-Sleep -Seconds 2

Write-Host "[B] INSIDE 113.376960, 23.067762 (while backgrounded)"
Set-EmulatorLocation -Longitude 113.376960 -Latitude 23.067762 -WaitSeconds 5

Write-Host "Reopen ANEN without force-stop / clearing the task."
& $adb -s $device shell am start -n $AnenActivity
Start-Sleep -Seconds 10

Write-Host ""
Write-Host "Expected: fresh location B after resume, no clear-task needed."
Show-AnenLocationLog
