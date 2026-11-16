# Capture one real-device test session: device facts + logcat + timed screenshots.
# Usage (Windows, from any folder that contains this script):
#   pwsh -File car-test-session.ps1 -Serial 192.168.1.213:5555 -Shots 20 -IntervalSec 30
#   pwsh -File car-test-session.ps1 -Serial <car-serial> -Shots 0
# Kill the capture afterwards:  Get-Process adb | Stop-Process   (or press Ctrl+C on the adb logcat window)
# ASCII only on purpose: avoids encoding surprises on Windows PowerShell 5.1.

param(
  [string]$Serial = "192.168.1.213:5555",
  [string]$Adb = "",
  [int]$IntervalSec = 30,
  [int]$Shots = 0,
  [string]$OutDir = "car-test"
)

$ErrorActionPreference = "Stop"

function Resolve-Adb([string]$hint) {
  if ($hint -and (Test-Path -LiteralPath $hint)) { return $hint }
  $candidates = @(
    "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    "C:\Program Files\Netease\MuMu\nx_main\adb.exe",
    "C:\Program Files\Netease\MuMuPlayer-12.0\shell\adb.exe"
  )
  foreach ($c in $candidates) { if (Test-Path -LiteralPath $c) { return $c } }
  $cmd = Get-Command adb -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  throw "adb.exe not found. Pass -Adb <full path>."
}

$adbExe = Resolve-Adb $Adb
$Adb = $adbExe

if ($Serial -match ":") { & $adbExe connect $Serial | Out-Null; Start-Sleep -Seconds 1 }
else { & $adbExe -s $Serial wait-for-device | Out-Null }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$dir = Join-Path $OutDir "$stamp-$($Serial -replace ':','_')"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $dir "shots") | Out-Null

$info = Join-Path $dir "session-info.txt"
$lines = @()
$lines += "session  $stamp"
$lines += "serial   $Serial"
$lines += "adb      $Adb"
$lines += ""
$lines += "== device =="
$lines += (& $adbExe -s $Serial shell getprop ro.product.model)
$lines += (& $adbExe -s $Serial shell getprop ro.build.version.release)
$lines += (& $adbExe -s $Serial shell getprop ro.build.version.sdk)
$lines += ""
$lines += "== wm size / density =="
$lines += (& $adbExe -s $Serial shell wm size)
$lines += (& $adbExe -s $Serial shell wm density)
$lines += ""
$lines += "== network =="
$lines += (& $adbExe -s $Serial shell ip -f inet addr show 2>$null)
$lines += (& $adbExe -s $Serial shell dumpsys wifi 2>$null | Select-String -Pattern "mWifiInfo|SSID|Frequency" | Select-Object -First 6)
$lines += ""
$lines += "== bluetooth =="
$lines += "bluetooth_on=" + (& $adbExe -s $Serial shell settings get global bluetooth_on)
$lines += ""
$lines += "== usb =="
$lines += (& $adbExe -s $Serial shell dumpsys usb 2>$null | Select-String -Pattern "host|accessory|port" | Select-Object -First 8)
$lines += ""
$lines += "== projection apps on this device =="
$lines += (& $adbExe -s $Serial shell pm list packages 2>$null | Select-String -Pattern "carlife|baidu|huawei|hicar|icarplay|androidauto|diplay")
$lines += ""
$lines += "== icarplay granted permissions =="
$lines += (& $adbExe -s $Serial shell dumpsys package com.rockjesus.icarplay 2>$null | Select-String -Pattern "granted=true" | Select-Object -First 20)

$lines | Set-Content -LiteralPath $info -Encoding utf8
Write-Output "session dir: $dir"

& $adbExe -s $Serial logcat -c | Out-Null
$log = Join-Path $dir "logcat.txt"
Start-Process -FilePath $Adb -ArgumentList @("-s", $Serial, "logcat", "-v", "time") -RedirectStandardOutput $log -WindowStyle Hidden | Out-Null
Write-Output "logcat running -> $log"

if ($Shots -gt 0) {
  for ($i = 1; $i -le $Shots; $i++) {
    $name = Join-Path $dir ("shots\shot-{0:d3}.png" -f $i)
    & $adbExe -s $Serial exec-out screencap -p > $name
    Write-Output ("shot {0}/{1} -> {2}" -f $i, $Shots, $name)
    if ($i -lt $Shots) { Start-Sleep -Seconds $IntervalSec }
  }
}

Write-Output "done"
