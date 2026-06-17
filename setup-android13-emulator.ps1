$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME = "C:\Android\Sdk"

Write-Host "=== Installing Android 13 (API 33) system image with Play Store ==="
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "system-images;android-33;google_apis_playstore;x86_64"

Write-Host "`n=== Deleting old BillSense_Test AVD ==="
& "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" delete avd -n "BillSense_Test" 2>$null

Write-Host "`n=== Removing old system images to free space ==="
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --uninstall "system-images;android-35;google_apis;x86_64" 2>$null
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --uninstall "system-images;android-34;google_apis_playstore;x86_64" 2>$null

Write-Host "`n=== Creating new BillSense_Test AVD (Android 13, Pixel 6) ==="
echo "no" | & "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd `
    -n "BillSense_Test" `
    -k "system-images;android-33;google_apis_playstore;x86_64" `
    --device "pixel_6" `
    --force

$configPath = "$env:USERPROFILE\.android\avd\BillSense_Test.avd\config.ini"
if (Test-Path $configPath) {
    $config = Get-Content $configPath
    $config = $config -replace "hw.ramSize=.*", "hw.ramSize=2048"
    $config += "`nhw.gpu.enabled=yes"
    $config += "`nhw.gpu.mode=auto"
    $config += "`ndisk.dataPartition.size=4096M"
    $config += "`nvm.heapSize=256"
    Set-Content $configPath $config
    Write-Host "`n=== AVD configured: 2GB RAM, GPU accel, 4GB storage ==="
}

Write-Host "`n=== AVD List ==="
& "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" list avd
Write-Host "`nDone!"
