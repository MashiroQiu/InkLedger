param([string]$SdkPath="$env:LOCALAPPDATA\Android\Sdk",[string]$JdkPath='C:\Program Files\Android\Android Studio\jbr',[string]$BuildDir="$PSScriptRoot\build-local")
$ErrorActionPreference='Stop'
$tools=Join-Path $SdkPath 'build-tools\35.0.0'
$platform=Join-Path $SdkPath 'platforms\android-35\android.jar'
$env:JAVA_HOME=$JdkPath
$env:PATH="$JdkPath\bin;$env:PATH"
function Run([string]$exe,[string[]]$params){& $exe @params; if($LASTEXITCODE -ne 0){throw "Build failed: $exe"}}
New-Item -ItemType Directory -Force -Path $BuildDir,"$BuildDir\classes","$BuildDir\dex" | Out-Null
$manifestText=[System.IO.File]::ReadAllText("$PSScriptRoot\app\src\main\AndroidManifest.xml").Replace('<manifest xmlns:', '<manifest package="cn.inkledger.app" xmlns:')
[System.IO.File]::WriteAllText("$BuildDir\AndroidManifest.xml",$manifestText,[System.Text.UTF8Encoding]::new($false))
Run "$tools\aapt2.exe" @('compile','--dir',"$PSScriptRoot\app\src\main\res",'-o',"$BuildDir\resources.zip")
Run "$tools\aapt2.exe" @('link','-o',"$BuildDir\unsigned.apk",'--version-code','21','--version-name','0.1.20-webview','--manifest',"$BuildDir\AndroidManifest.xml",'-A',"$PSScriptRoot\app\src\main\assets",'-I',$platform,"$BuildDir\resources.zip")
Run "$JdkPath\bin\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-classpath',$platform,'-d',"$BuildDir\classes","$PSScriptRoot\app\src\main\java\cn\inkledger\app\MainActivity.java")
Run "$JdkPath\bin\jar.exe" @('--create','--file',"$BuildDir\classes.jar",'-C',"$BuildDir\classes",'.')
Run "$tools\d8.bat" @('--min-api','26','--lib',$platform,'--output',"$BuildDir\dex","$BuildDir\classes.jar")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[System.IO.Compression.ZipFile]::Open("$BuildDir\unsigned.apk",[System.IO.Compression.ZipArchiveMode]::Update)
try{[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$BuildDir\dex\classes.dex",'classes.dex') | Out-Null}finally{$zip.Dispose()}
Run "$tools\zipalign.exe" @('-f','4',"$BuildDir\unsigned.apk","$BuildDir\aligned.apk")
if(!(Test-Path -LiteralPath "$BuildDir\debug.jks")){Run "$JdkPath\bin\keytool.exe" @('-genkeypair','-keystore',"$BuildDir\debug.jks",'-alias','androiddebugkey','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=InkLedger Debug,O=Development,C=CN')}
Run "$tools\apksigner.bat" @('sign','--ks',"$BuildDir\debug.jks",'--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',"$BuildDir\墨账-0.1.20-webview-debug.apk","$BuildDir\aligned.apk")
Run "$tools\apksigner.bat" @('verify','--verbose',"$BuildDir\墨账-0.1.20-webview-debug.apk")
Write-Output "APK: $BuildDir\墨账-0.1.20-webview-debug.apk"
