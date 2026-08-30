# AuraDesk 1-Click Build, Install & Launch Script for Windows PowerShell
Write-Host "⚡ Building AuraDesk Debug APK..." -ForegroundColor Cyan
.\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "📲 Installing to connected Android device..." -ForegroundColor Green
    adb install -r app\build\outputs\apk\debug\app-debug.apk
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "🚀 Launching AuraDesk..." -ForegroundColor Green
        adb shell am start -n com.auradesk.guard/.MainActivity
    } else {
        Write-Host "⚠️ Install failed. Check device connection and ensure screen is unlocked." -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ Build Failed. Check Gradle logs above." -ForegroundColor Red
}
