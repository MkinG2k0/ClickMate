# Run as Administrator only if Windows blocks the phone connection.
# The rule applies only to Private networks and devices on the local subnet.
$ErrorActionPreference = 'Stop'
$exe = Join-Path $PSScriptRoot 'dist\ClickMate-PC.exe'
if (!(Test-Path -LiteralPath $exe)) { throw 'Build ClickMate-PC.exe first.' }
foreach ($legacy in @('Ladon-Remote-PC','Ladon-Remote-PC-Discovery')) { $rule=Get-NetFirewallRule -Name $legacy -ErrorAction SilentlyContinue;if($rule){Remove-NetFirewallRule -Name $legacy} }
$tcp = Get-NetFirewallRule -Name 'ClickMate-Remote-PC' -ErrorAction SilentlyContinue
if (!$tcp) { New-NetFirewallRule -Name 'ClickMate-Remote-PC' -DisplayName 'ClickMate - phone remote (private LAN)' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 48732 -Program $exe -Profile Private -RemoteAddress LocalSubnet | Out-Null }
$udp = Get-NetFirewallRule -Name 'ClickMate-Remote-PC-Discovery' -ErrorAction SilentlyContinue
if (!$udp) { New-NetFirewallRule -Name 'ClickMate-Remote-PC-Discovery' -DisplayName 'ClickMate - PC discovery (private LAN)' -Direction Inbound -Action Allow -Protocol UDP -LocalPort 48733 -Program $exe -Profile Private -RemoteAddress LocalSubnet | Out-Null }
Write-Output 'Local phone connections and PC discovery are allowed on Private networks.'
