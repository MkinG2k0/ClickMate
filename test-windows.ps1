$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
New-Item -ItemType Directory -Force -Path "$root\build" | Out-Null
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $compiler /nologo /target:exe /platform:x64 /main:ProtocolTests "/out:$root\build\ProtocolTests.exe" /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Web.Extensions.dll "/reference:$root\third_party\qrcode\QRCoder.dll" "/resource:$root\third_party\qrcode\QRCoder.dll,QRCoder.dll" "/resource:$root\web\index.html,ClickMate.Web.index.html" "/resource:$root\web\styles.css,ClickMate.Web.styles.css" "/resource:$root\web\crypto.js,ClickMate.Web.crypto.js" "/resource:$root\web\app.js,ClickMate.Web.app.js" "/resource:$root\web\manifest.webmanifest,ClickMate.Web.manifest.webmanifest" "/resource:$root\web\icon.svg,ClickMate.Web.icon.svg" "/resource:$root\web\sw.js,ClickMate.Web.sw.js" "$root\windows\RemotePc.cs" "$root\tests\ProtocolTests.cs"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$root\build\ProtocolTests.exe"
if ($LASTEXITCODE -ne 0) { throw 'Protocol tests failed' }
