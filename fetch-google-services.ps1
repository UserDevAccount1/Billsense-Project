$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
Set-Location "D:\Github\Billsense-Project\BillSense"
npx -y firebase-tools@latest apps:sdkconfig android --project bill-sense-aec6b --out app/google-services.json
