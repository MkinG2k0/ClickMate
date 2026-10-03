param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Java = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$out = Join-Path $root 'dist'
$temp = Join-Path $root 'build\android'
$bt = Join-Path $Sdk 'build-tools\35.0.0'
$platform = Join-Path $Sdk 'platforms\android-35\android.jar'
$env:JAVA_HOME = $Java
foreach ($item in @("$Java\bin\javac.exe", "$bt\aapt.exe", $platform)) {
    if (!(Test-Path -LiteralPath $item)) { throw "Missing dependency: $item. Pass -Sdk and -Java or build in Android Studio." }
}
$classes = [System.IO.Path]::GetFullPath("$temp\classes")
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $root 'build')) + [System.IO.Path]::DirectorySeparatorChar
if (!$classes.StartsWith($buildRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Classes directory is outside the build folder.' }
if (Test-Path -LiteralPath $classes) { Remove-Item -LiteralPath $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out,$classes,"$temp\dex" | Out-Null
function Invoke-BuildStep([string]$program, [string[]]$arguments) {
    & $program @arguments
    if ($LASTEXITCODE -ne 0) { throw "Build step failed: $program" }
}
$zxing = "$root\third_party\zxing\core-3.5.3.jar"
$sources = @(Get-ChildItem "$root\android\src" -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
Invoke-BuildStep "$Java\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',"$bt\core-lambda-stubs.jar;$platform",'-classpath',$zxing,'-d',"$temp\classes") + $sources)
Invoke-BuildStep "$Java\bin\jar.exe" @('cf',"$temp\classes.jar",'-C',"$temp\classes",'.')
Invoke-BuildStep "$bt\d8.bat" @('--lib',$platform,'--min-api','26','--output',"$temp\dex","$temp\classes.jar",$zxing)
Invoke-BuildStep "$bt\aapt.exe" @('package','-f','-M',"$root\android\AndroidManifest.xml",'-S',"$root\android\res",'-A',"$root\android\assets",'-I',$platform,'-F',"$temp\unsigned.apk")
Push-Location "$temp\dex"
try { Invoke-BuildStep "$bt\aapt.exe" @('add',"$temp\unsigned.apk",'classes.dex') } finally { Pop-Location }
Invoke-BuildStep "$bt\zipalign.exe" @('-f','4',"$temp\unsigned.apk","$temp\aligned.apk")
$key = Join-Path $root 'build\debug.keystore'
if (!(Test-Path -LiteralPath $key)) {
    Invoke-BuildStep "$Java\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=ClickMate Development','-keyalg','RSA','-keysize','2048','-validity','10000')
}
Invoke-BuildStep "$bt\apksigner.bat" @('sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',"$out\ClickMate-Android.apk","$temp\aligned.apk")
Invoke-BuildStep "$bt\apksigner.bat" @('verify','--verbose',"$out\ClickMate-Android.apk")
foreach ($legacy in @("$out\Ladon-Android.apk","$out\Ladon-Android.apk.idsig")) { if (Test-Path -LiteralPath $legacy) { Remove-Item -LiteralPath $legacy -Force } }
Write-Output "Ready: $out\ClickMate-Android.apk"
