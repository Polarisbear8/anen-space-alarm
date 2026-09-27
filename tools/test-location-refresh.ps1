# Test A - foreground location refresh.
#
# Preconditions:
#   - ANEN is in the foreground on Home.
#   - Developer Mode is ON and the Location Debug overlay is visible.
#
# Expected: after the emulator moves from A to B, the overlay changes on its
# own. No app restart, no clearing the task.

. "$PSScriptRoot\emulator-location.ps1"

Write-Host "=== Test A: foreground location refresh ==="
Clear-AnenLog

Write-Host "[A] OUTSIDE 113.373500, 23.067762"
Set-EmulatorLocation -Longitude 113.373500 -Latitude 23.067762 -WaitSeconds 8

Write-Host "[B] INSIDE 113.376960, 23.067762"
Set-EmulatorLocation -Longitude 113.376960 -Latitude 23.067762 -WaitSeconds 8

Write-Host ""
Write-Host "Expected: Location Debug moved from A to B without restart / clear-task."
Show-AnenLocationLog
