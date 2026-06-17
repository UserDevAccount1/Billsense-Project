$currentPath = [System.Environment]::GetEnvironmentVariable('Path', 'User')
$sdkBase = "D:\Software\android studio\Sdk"
$newEntries = "$sdkBase\platform-tools;$sdkBase\emulator;$sdkBase\cmdline-tools\latest\bin"

if (-not $currentPath.Contains($sdkBase)) {
    [System.Environment]::SetEnvironmentVariable('Path', "$newEntries;$currentPath", 'User')
    Write-Host "PATH updated with new SDK location"
} else {
    Write-Host "PATH already contains new SDK"
}

# Set ANDROID_HOME
[System.Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdkBase, 'User')
[System.Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $sdkBase, 'User')
Write-Host "ANDROID_HOME set to $sdkBase"
