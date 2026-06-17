$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME = "C:\Android\Sdk"

Write-Host "Installing emulator and system image..."
& "C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "emulator" "system-images;android-35;google_apis;x86_64"
Write-Host "Done installing emulator components."

Write-Host "Creating AVD 'BillSense_Test'..."
echo "no" | & "C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n "BillSense_Test" -k "system-images;android-35;google_apis;x86_64" --device "pixel_6" --force
Write-Host "AVD created."
