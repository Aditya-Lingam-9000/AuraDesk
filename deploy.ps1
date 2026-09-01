# AuraDesk Fast Direct APK Deploy Script
$adbCmd = if (Get-Command adb -ErrorAction SilentlyContinue) { "adb" } elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") { "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" } else { "adb" }

$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apkPath)) {
    Write-Host "[-] APK not found. Running build..." -ForegroundColor Yellow
    & .\gradlew.bat assembleDebug
}

Write-Host "[+] Installing AuraDesk Phase 6 APK ($apkPath)..." -ForegroundColor Cyan
& $adbCmd install -r $apkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "[+] Launching AuraDesk on device..." -ForegroundColor Green
    & $adbCmd shell am start -n com.auradesk.guard/.MainActivity
    Write-Host "[+] AuraDesk launched successfully!" -ForegroundColor Green
} else {
    Write-Host "[!] Device not detected. Please make sure phone is connected with USB Debugging enabled." -ForegroundColor Yellow
}
