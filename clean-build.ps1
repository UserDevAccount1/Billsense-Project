$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
$env:ANDROID_HOME = "D:\Software\android studio\Sdk"
Set-Location "D:\Github\Billsense-Project\BillSense"
.\gradlew.bat clean assembleUserDebug --no-daemon
