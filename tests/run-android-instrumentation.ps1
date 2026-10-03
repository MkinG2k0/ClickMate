$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$java="$env:LOCALAPPDATA\Programs\Android Studio\jbr"
$sdk="$env:LOCALAPPDATA\Android\Sdk"
$bt="$sdk\build-tools\35.0.0"
$platform="$sdk\platforms\android-35\android.jar"
$temp="$root\build\android-tests"
$env:JAVA_HOME=$java
New-Item -ItemType Directory -Force -Path "$temp\classes","$temp\dex" | Out-Null
function Run([string]$exe,[string[]]$arguments){ & $exe @arguments; if($LASTEXITCODE -ne 0){throw "Failed: $exe"} }
Run "$java\bin\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',"$bt\core-lambda-stubs.jar;$platform",'-d',"$temp\classes","$root\tests\android\UiTests.java")
Run "$java\bin\jar.exe" @('cf',"$temp\tests.jar",'-C',"$temp\classes",'.')
Run "$bt\d8.bat" @('--lib',$platform,'--min-api','26','--output',"$temp\dex","$temp\tests.jar")
Run "$bt\aapt.exe" @('package','-f','-M',"$root\tests\android\AndroidManifest.xml",'-I',$platform,'-F',"$temp\unsigned.apk")
Push-Location "$temp\dex"
try { Run "$bt\aapt.exe" @('add',"$temp\unsigned.apk",'classes.dex') } finally { Pop-Location }
Run "$bt\zipalign.exe" @('-f','4',"$temp\unsigned.apk","$temp\aligned.apk")
Run "$bt\apksigner.bat" @('sign','--ks',"$root\build\debug.keystore",'--ks-pass','pass:android','--key-pass','pass:android','--out',"$temp\tests.apk","$temp\aligned.apk")
Run "$sdk\platform-tools\adb.exe" @('-s','emulator-5580','install','--no-incremental','-r',"$root\dist\ClickMate-Android.apk")
Run "$sdk\platform-tools\adb.exe" @('-s','emulator-5580','install','--no-incremental','-r',"$temp\tests.apk")
Run "$sdk\platform-tools\adb.exe" @('-s','emulator-5580','shell','pm','clear','ru.ladon.remote')
Run "$sdk\platform-tools\adb.exe" @('-s','emulator-5580','shell','pm','grant','ru.ladon.remote','android.permission.CAMERA')
$result = & "$sdk\platform-tools\adb.exe" -s emulator-5580 shell am instrument -w ru.ladon.remote.tests/ru.ladon.remote.tests.UiTests
$result | Write-Output
if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'ALL UI CHECKS PASSED') { throw 'Android UI checks failed' }
