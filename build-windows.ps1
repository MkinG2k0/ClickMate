$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$out = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
& $compiler /nologo /target:winexe /platform:x64 /optimize+ "/win32icon:$root\windows\Ladon.ico" "/resource:$root\windows\Ladon.ico,Ladon.ico" "/out:$out\ClickMate-PC.exe" /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Web.Extensions.dll "/reference:$root\third_party\qrcode\QRCoder.dll" "/resource:$root\third_party\qrcode\QRCoder.dll,QRCoder.dll" "/resource:$root\third_party\qrcode\LICENSE.txt,QRCoder.LICENSE.txt" "/resource:$root\web\index.html,ClickMate.Web.index.html" "/resource:$root\web\styles.css,ClickMate.Web.styles.css" "/resource:$root\web\crypto.js,ClickMate.Web.crypto.js" "/resource:$root\web\app.js,ClickMate.Web.app.js" "/resource:$root\web\manifest.webmanifest,ClickMate.Web.manifest.webmanifest" "/resource:$root\web\icon.svg,ClickMate.Web.icon.svg" "/resource:$root\web\sw.js,ClickMate.Web.sw.js" "$root\windows\RemotePc.cs"
if ($LASTEXITCODE -ne 0) { throw 'Windows build failed' }
if (Test-Path -LiteralPath "$out\Ladon-PC.exe") { Remove-Item -LiteralPath "$out\Ladon-PC.exe" -Force }
Write-Output "Ready: $out\ClickMate-PC.exe"
