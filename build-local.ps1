param(
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$JdkPath = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$BuildDir = "$PSScriptRoot\build-native",
    [string]$GradleUserHome = ''
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JdkPath
$env:ANDROID_HOME = $SdkPath
$gradleArgs = @('-p', $PSScriptRoot, ':app:assembleRelease', ':app:testDebugUnitTest', ':app:lintDebug', '--console=plain')
if ($GradleUserHome) { $gradleArgs = @('-g', $GradleUserHome) + $gradleArgs }
& "$PSScriptRoot\gradlew.bat" @gradleArgs
if ($LASTEXITCODE -ne 0) { throw 'Native build or verification failed' }
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
$apk = Join-Path $PSScriptRoot 'native-app\build\outputs\apk\release\app-release.apk'
$target = Join-Path $BuildDir '墨账-0.1.14-native.apk'
Copy-Item -LiteralPath $apk -Destination $target
Write-Output "APK: $target"
