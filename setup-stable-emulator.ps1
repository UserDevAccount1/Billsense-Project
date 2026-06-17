$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME = "C:\Android\Sdk"

Write-Host "=== Installing Android 14 (API 34) system image with Play Store ==="
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "system-images;android-34;google_apis_playstore;x86_64"

Write-Host "`n=== Deleting old BillSense_Test AVD ==="
& "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" delete avd -n "BillSense_Test" 2>$null

Write-Host "`n=== Creating new stable BillSense_Test AVD (Android 14, Pixel 6) ==="
echo "no" | & "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd `
    -n "BillSense_Test" `
    -k "system-images;android-34;google_apis_playstore;x86_64" `
    --device "pixel_6" `
    --force

# Configure AVD for stability
$configPath = "$env:USERPROFILE\.android\avd\BillSense_Test.avd\config.ini"
if (Test-Path $configPath) {
    $config = Get-Content $configPath
    $config = $config -replace "hw.ramSize=.*", "hw.ramSize=2048"
    $config += "`nhw.gpu.enabled=yes"
    $config += "`nhw.gpu.mode=auto"
    $config += "`ndisk.dataPartition.size=4096M"
    $config += "`nvm.heapSize=256"
    Set-Content $configPath $config
    Write-Host "`n=== AVD configured with 2GB RAM, GPU accel, 4GB storage ==="
}

Write-Host "`n=== Listing AVDs ==="
& "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" list avd
Write-Host "`nDone!"
