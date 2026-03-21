# BillSense - Full Build & Run Pipeline
# Starts emulator (if not running), builds debug APK, installs, and launches the app
# Usage: powershell -ExecutionPolicy Bypass -File scripts/build-and-run.ps1
# Options: -Variant main|admin  -SkipBuild  -SkipEmulator

param(
    [string]$Variant = "main",
    [switch]$SkipBuild,
    [switch]$SkipEmulator
)

$ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$ADB = "$ANDROID_HOME\platform-tools\adb.exe"
$EMULATOR = "$ANDROID_HOME\emulator\emulator.exe"
$AVD_NAME = "Medium_Phone_API_36.1"

# Set JAVA_HOME for Gradle (requires JDK 17+)
$JAVA_HOME_PATH = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
if (Test-Path $JAVA_HOME_PATH) {
    $env:JAVA_HOME = $JAVA_HOME_PATH
    $env:PATH = "$JAVA_HOME_PATH\bin;$env:PATH"
    Write-Host "Using Java: $JAVA_HOME_PATH" -ForegroundColor DarkGray
} else {
    Write-Host "WARNING: JDK 17 not found at $JAVA_HOME_PATH" -ForegroundColor Yellow
    Write-Host "Build may fail if JAVA_HOME is not set to JDK 11+." -ForegroundColor Yellow
}

# Package and activity for each variant
$packages = @{
    "main"  = "com.app.billsense/.activities.MainActivity"
    "admin" = "com.admin.billsense/.activities.MainActivity"
}

$component = $packages[$Variant]
if (-not $component) {
    Write-Host "Unknown variant: $Variant. Use 'main' or 'admin'." -ForegroundColor Red
    exit 1
}

$packageName = $component.Split("/")[0]

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  BillSense Build & Run Pipeline" -ForegroundColor Cyan
Write-Host "  Variant: $Variant | AVD: $AVD_NAME" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check/Start Emulator
if (-not $SkipEmulator) {
    Write-Host "[1/5] Checking emulator status..." -ForegroundColor Yellow

    $devices = & $ADB devices 2>$null | Select-String "emulator"
    if ($devices) {
        Write-Host "  Emulator already running." -ForegroundColor Green
    } else {
        Write-Host "  Starting emulator: $AVD_NAME..." -ForegroundColor Yellow
        Start-Process -FilePath $EMULATOR -ArgumentList "-avd", $AVD_NAME, "-gpu", "host" -WindowStyle Minimized

        Write-Host "  Waiting for emulator to boot..." -ForegroundColor Yellow
        & $ADB wait-for-device 2>$null

        # Wait for boot_completed
        $timeout = 120
        $elapsed = 0
        do {
            Start-Sleep -Seconds 2
            $elapsed += 2
            $bootComplete = & $ADB shell getprop sys.boot_completed 2>$null
            if ($elapsed % 10 -eq 0) {
                Write-Host "  Still booting... ($elapsed seconds)" -ForegroundColor DarkYellow
            }
        } while ($bootComplete.Trim() -ne "1" -and $elapsed -lt $timeout)

        if ($bootComplete.Trim() -eq "1") {
            Write-Host "  Emulator booted successfully! ($elapsed seconds)" -ForegroundColor Green
        } else {
            Write-Host "  Emulator boot timeout after $timeout seconds." -ForegroundColor Red
            exit 1
        }
    }
} else {
    Write-Host "[1/5] Skipping emulator start (--SkipEmulator)" -ForegroundColor DarkGray
}

# Step 2: Build
if (-not $SkipBuild) {
    Write-Host ""
    Write-Host "[2/5] Building debug APK..." -ForegroundColor Yellow

    $buildStart = Get-Date
    & ./gradlew assembleDebug --console=plain 2>&1 | ForEach-Object {
        if ($_ -match "BUILD SUCCESSFUL") {
            Write-Host "  $_" -ForegroundColor Green
        } elseif ($_ -match "BUILD FAILED|FAILURE") {
            Write-Host "  $_" -ForegroundColor Red
        } elseif ($_ -match "> Task") {
            Write-Host "  $_" -ForegroundColor DarkGray
        }
    }

    $buildTime = [math]::Round(((Get-Date) - $buildStart).TotalSeconds, 1)

    if ($LASTEXITCODE -ne 0) {
        Write-Host "  Build failed! (${buildTime}s)" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Build complete! (${buildTime}s)" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "[2/5] Skipping build (--SkipBuild)" -ForegroundColor DarkGray
}

# Step 3: Install
Write-Host ""
Write-Host "[3/5] Installing APK on emulator..." -ForegroundColor Yellow

& ./gradlew installDebug --console=plain 2>&1 | ForEach-Object {
    if ($_ -match "BUILD SUCCESSFUL|Installed") {
        Write-Host "  $_" -ForegroundColor Green
    } elseif ($_ -match "BUILD FAILED|FAILURE") {
        Write-Host "  $_" -ForegroundColor Red
    }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "  Install failed!" -ForegroundColor Red
    exit 1
}
Write-Host "  APK installed successfully." -ForegroundColor Green

# Step 4: Launch App
Write-Host ""
Write-Host "[4/5] Launching $Variant app..." -ForegroundColor Yellow

& $ADB shell am start -n $component 2>&1 | ForEach-Object {
    Write-Host "  $_" -ForegroundColor DarkGray
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "  App launched: $component" -ForegroundColor Green
} else {
    Write-Host "  Failed to launch app." -ForegroundColor Red
    exit 1
}

# Step 5: Summary
Write-Host ""
Write-Host "[5/5] Done!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  App running on $AVD_NAME" -ForegroundColor Cyan
Write-Host "  Package: $packageName" -ForegroundColor Cyan
Write-Host "  Variant: $Variant | Type: debug" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Tip: Run 'adb logcat -s MainActivity:* RealTimeScanManager:*' for logs" -ForegroundColor DarkGray
