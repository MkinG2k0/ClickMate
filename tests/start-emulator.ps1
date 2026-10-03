$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$avdHome = Join-Path $root 'build\avd'
$avd = Join-Path $avdHome 'LadonTest.avd'
New-Item -ItemType Directory -Force -Path $avd | Out-Null
@"
avd.ini.encoding=UTF-8
path=$avd
target=android-36
"@ | Set-Content -Encoding utf8 (Join-Path $avdHome 'LadonTest.ini')
@"
AvdId=LadonTest
avd.ini.displayname=Ladon Test
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.audioInput=no
hw.audioOutput=no
hw.battery=yes
hw.mainKeys=no
disk.dataPartition.size=2G
image.sysdir.1=$sdk\system-images\android-36.1\google_apis_playstore\x86_64\
tag.id=google_apis_playstore
tag.display=Google Play
PlayStore.enabled=true
"@ | Set-Content -Encoding utf8 (Join-Path $avd 'config.ini')
$env:ANDROID_AVD_HOME = $avdHome
$env:ANDROID_USER_HOME = Join-Path $root 'build\android-user'
$env:ANDROID_EMULATOR_HOME = $env:ANDROID_USER_HOME
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList @('-avd','LadonTest','-no-window','-no-audio','-no-snapshot','-no-boot-anim','-gpu','swiftshader_indirect','-port','5580') -WindowStyle Hidden -RedirectStandardOutput "$root\build\emulator.log" -RedirectStandardError "$root\build\emulator-error.log"
