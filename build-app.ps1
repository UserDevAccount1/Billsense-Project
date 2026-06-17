$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME = "C:\Android\Sdk"
Set-Location "D:\Github\Billsense-Project\BillSense"
.\gradlew.bat assembleUserDebug --no-daemon
