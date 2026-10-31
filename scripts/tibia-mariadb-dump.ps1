param(
    [string] $ConfigLua = (Join-Path $env:USERPROFILE 'tibia-local\canary\config.lua'),
    [string] $Bin = (Join-Path $env:USERPROFILE 'scoop\apps\mariadb\current\bin'),
    [string] $OutDir = (Join-Path $env:LOCALAPPDATA 'tibia-mariadb-dumps'),
    [int] $Keep = 14
)
$ErrorActionPreference = 'Stop'
# A consistent (--single-transaction, InnoDB) logical backup of the canary DB. Safe on a live server;
# a file-by-file copy of the datadir while mariadbd runs is NOT a consistent restore point, this is.
$cfg = Get-Content -LiteralPath $ConfigLua -Raw
function Val($n) { if ($cfg -match "(?m)^\s*$n\s*=\s*`"?([^`"\r\n]*)`"?") { $matches[1].Trim() } }
$db = Val 'mysqlDatabase'; $user = Val 'mysqlUser'; $port = Val 'mysqlPort'; $pass = Val 'mysqlPass'
if (-not $db) { throw "tibia-mariadb-dump: no mysqlDatabase in $ConfigLua" }
New-Item -ItemType Directory -Force $OutDir | Out-Null
$out = Join-Path $OutDir ("{0}-{1:yyyyMMdd-HHmmss}.sql" -f $db, (Get-Date))
if ($pass) { $env:MYSQL_PWD = $pass }
try {
    & "$Bin\mariadb-dump.exe" -u $user -h 127.0.0.1 -P $port --single-transaction --routines --triggers --events --databases $db |
        Out-File -LiteralPath $out -Encoding utf8
} finally { $env:MYSQL_PWD = $null }
$tail = Get-Content -LiteralPath $out -Tail 1
$tables = (Select-String -LiteralPath $out -Pattern '^CREATE TABLE').Count
if ($tail -notmatch 'Dump completed' -or $tables -lt 1) {
    Remove-Item -LiteralPath $out -Force
    throw "tibia-mariadb-dump: incomplete dump (tables=$tables, tail='$tail'); removed"
}
Get-ChildItem $OutDir -Filter "$db-*.sql" | Sort-Object Name -Descending | Select-Object -Skip $Keep | Remove-Item -Force
"tibia-mariadb-dump $(Get-Date -Format 'yyyy-MM-dd HH:mm'): $db $tables tables, $([Math]::Round((Get-Item $out).Length/1KB)) KB -> $(Split-Path $out -Leaf); kept newest $Keep"
