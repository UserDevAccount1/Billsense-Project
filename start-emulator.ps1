$env:ANDROID_HOME = "C:\Android\Sdk"
Start-Process -FilePath "C:\Android\Sdk\emulator\emulator.exe" -ArgumentList "-avd", "BillSense_Test", "-gpu", "auto" -PassThru
Write-Host "Emulator starting..."
