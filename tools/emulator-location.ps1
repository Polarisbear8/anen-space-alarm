# ANEN spatial alarm - shared ADB location test library.
#
# Usage:
#   Dot-source it from another script:  . "$PSScriptRoot\emulator-location.ps1"
#   Or run it directly to move the emulator once:
#     .\tools\emulator-location.ps1 -Longitude 113.376960 -Latitude 23.067762
#
# All commands are pinned to a running emulator serial so a connected
# physical device is never touched by mistake.

param(
    [double]$Longitude,
    [double]$Latitude,
    [int]$WaitSeconds = 5
)

$AnenPackage = "com.anen.spacealarm"
$AnenActivity = "$AnenPackage/.MainActivity"

function Get-AnenAdb {
    $candidates = @()
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") }
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe") }
    $candidates += "D:\ProgramData\Android\Sdk\platform-tools\adb.exe"
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "adb.exe not found. Set ANDROID_HOME / ANDROID_SDK_ROOT or add platform-tools to PATH."
}

function Get-AnenDevice {
    $adb = Get-AnenAdb
    $line = (& $adb devices) | Select-String "^emulator-\d+\s+device" | Select-Object -First 1
    if (-not $line) { throw "No running Android emulator found (adb devices)." }
    return $line.ToString().Split()[0]
}

# adb emu geo fix takes LONGITUDE first, then LATITUDE. Do not swap.
function Set-EmulatorLocation {
    param(
        [Parameter(Mandatory = $true)][double]$Longitude,
        [Parameter(Mandatory = $true)][double]$Latitude,
        [int]$WaitSeconds = 5
    )
    $adb = Get-AnenAdb
    $device = Get-AnenDevice
    Write-Host "GPS -> lon=$Longitude lat=$Latitude (device=$device)"
    & $adb -s $device emu geo fix $Longitude $Latitude
    if ($LASTEXITCODE -ne 0) { throw "adb emu geo fix failed." }
    if ($WaitSeconds -gt 0) { Start-Sleep -Seconds $WaitSeconds }
}

function Clear-AnenLog {
    $adb = Get-AnenAdb
    $device = Get-AnenDevice
    & $adb -s $device logcat -c
    Write-Host "logcat cleared."
}

function Get-AnenLocationLog {
    $adb = Get-AnenAdb
    $device = Get-AnenDevice
    $pattern = "GEOFENCE EVENT|ALERT START|ALERT DELIVERED|location update|availability|AnenLocation|AnenGeofence|AnenAlert|AnenAlarmService"
    return (& $adb -s $device logcat -d -v time | Select-String $pattern)
}

function Show-AnenLocationLog {
    $lines = Get-AnenLocationLog
    if (-not $lines) {
        Write-Host "(no matching log lines yet)"
        return
    }
    $lines | ForEach-Object { Write-Host $_ }
}

if ($MyInvocation.InvocationName -ne '.') {
    Write-Host "emulator-location.ps1 is a shared library; test-*.ps1 dot-source it."
    if ($PSBoundParameters.ContainsKey('Longitude') -and $PSBoundParameters.ContainsKey('Latitude')) {
        Set-EmulatorLocation -Longitude $Longitude -Latitude $Latitude -WaitSeconds $WaitSeconds
    }
}
