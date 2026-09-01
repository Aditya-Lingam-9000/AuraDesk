$ErrorActionPreference = "Stop"

$workspaceDir = "d:\Desktop\AndroidStudio-Projects\AuraDesk"
$adbPath = "C:\Users\veerababu\AppData\Local\Android\Sdk\platform-tools\adb.exe"

Write-Host "[*] Building AuraDesk Production Release APK..."
Set-Location -Path $workspaceDir

& .\gradlew.bat assembleRelease

$releaseApk = "d:\Desktop\AndroidStudio-Projects\AuraDesk\app\build\outputs\apk\release\app-release.apk"

if (Test-Path $releaseApk) {
    $apkItem = Get-Item $releaseApk
    $apkSizeMB = [math]::Round($apkItem.Length / 1MB, 2)
    Write-Host "[+] Release APK Generated Successfully: $releaseApk ($apkSizeMB MB)"
    Write-Host "[+] Installing Release APK to connected device..."
    & $adbPath install -r $releaseApk
    Write-Host "[+] Launching AuraDesk Release on device..."
    & $adbPath shell am start -n com.auradesk.guard/.MainActivity
    Write-Host "[✓] Phase 10 Production Release Completed Successfully!"
} else {
    Write-Host "[-] Release APK not found"
    exit 1
}
