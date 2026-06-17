$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME = "D:\Software\android studio\Sdk"

Write-Host "Installing API 36 platform and system image..."
& "D:\Software\android studio\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "system-images;android-36;default;x86_64" "build-tools;36.0.0"
Write-Host "Done installing API 36 components."
