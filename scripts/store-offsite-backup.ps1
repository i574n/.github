param([int] $Keep = 8)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'agent-runtime.ps1')
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\store-offsite-backup.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$stamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')   # true UTC; the Z suffix must not label local time
function Fail([string] $m) { Set-Content -LiteralPath $log -Value "store-offsite-backup $stamp FAIL: $m"; throw $m }

$envf = $env:OORT_ENV_FILE ?? [Environment]::GetEnvironmentVariable('OORT_ENV_FILE', 'User') ?? (Join-Path $env:USERPROFILE '.secrets\oort-s3.env')
if (-not (Test-Path $envf)) { Fail "no creds at $envf" }
$cfg = @{}
foreach ($line in (Get-Content -LiteralPath $envf)) { if ($line -match '^\s*([A-Z_0-9]+)\s*=\s*(.*?)\s*$') { $cfg[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'") } }
$region = $cfg['S3_REGION']
if (-not $region) { Fail 'S3_REGION missing from the oort env file' }
$env:RCLONE_S3_PROVIDER = 'Other'
$env:RCLONE_S3_ENDPOINT = $cfg['S3_ENDPOINT']
$env:RCLONE_S3_REGION = $region
$env:RCLONE_S3_ACCESS_KEY_ID = $cfg['S3_ACCESS_KEY']
$env:RCLONE_S3_SECRET_ACCESS_KEY = $cfg['S3_SECRET_KEY']
$env:RCLONE_S3_NO_CHECK_BUCKET = 'true'
$bucket = $env:OORT_BUCKET ?? [Environment]::GetEnvironmentVariable('OORT_BUCKET', 'User')
if (-not $bucket) { Fail 'OORT_BUCKET unset' }
$base = ":s3:$bucket/backups/box-critical"
$dest = "$base/$stamp/"

$scr = Join-Path $env:TEMP "store-backup-$stamp"
New-Item -ItemType Directory -Force $scr | Out-Null
try {
    $tb = Join-Path $scr 'critical.tar.gz'
    $srcs = @(
        @{ Dir = 'C:\home\git\i574n.github\docs'; Leaf = 'backlog' },
        @{ Dir = (Get-AgentProjectDir); Leaf = 'memory' },
        @{ Dir = (Get-AgentStateHome); Leaf = 'hooks' },
        @{ Dir = (Join-Path $env:USERPROFILE '.config'); Leaf = 'remote-machines.json' }
    )
    $tarArgs = @('-czf', $tb)
    foreach ($s in $srcs) { $tarArgs += @('-C', $s.Dir, $s.Leaf) }
    $agileSpi = 'C:\home\git\i574n.github\docs\backlog\state\agile.spi'
    $agileLock = 'C:\home\git\i574n.github\docs\backlog\state\agile.spi.lock'
    $locked = $false
    $lockDeadline = (Get-Date).AddSeconds(90)
    while (-not $locked -and (Get-Date) -lt $lockDeadline) {
        & cmd.exe /d /c mkdir "$agileLock" 2>$null
        $locked = $LASTEXITCODE -eq 0
        if ($locked) { Set-Content -LiteralPath (Join-Path $agileLock 'holder') -Value "$PID $([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())" -NoNewline }
        if (-not $locked) { Start-Sleep -Milliseconds 250 }
    }
    $consistent = $false
    try {
        for ($try = 1; $try -le 3; $try++) {
            $before = (Get-Item -LiteralPath $agileSpi -ErrorAction SilentlyContinue).LastWriteTimeUtc
            & tar.exe @tarArgs
            if ($LASTEXITCODE -ne 0) { Fail "tar exit $LASTEXITCODE" }
            $after = (Get-Item -LiteralPath $agileSpi -ErrorAction SilentlyContinue).LastWriteTimeUtc
            if ($before -and $after -and $before -eq $after) { $consistent = $true; break }
            if ($try -lt 3) { Start-Sleep -Seconds 3 }
        }
    } finally {
        if ($locked) { Remove-Item -LiteralPath (Join-Path $agileLock 'holder') -Force -ErrorAction SilentlyContinue; Remove-Item -LiteralPath $agileLock -Force -ErrorAction SilentlyContinue }
    }
    $snapNote = if ($locked -and $consistent) { ' LOCKED' } elseif ($consistent) { '' } else { ' WARN:agile.spi-written-during-tar(snapshot-may-be-torn)' }
    $sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $tb).Hash.ToLower()
    $mb = [math]::Round((Get-Item $tb).Length / 1MB, 1)

    & rclone copyto $tb ($dest + 'critical.tar.gz') --contimeout 10s --timeout 180s 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "rclone push exit $LASTEXITCODE" }
    $shaFile = Join-Path $scr 'critical.sha256'
    "$sha  critical.tar.gz" | Set-Content -LiteralPath $shaFile
    & rclone copyto $shaFile ($dest + 'critical.sha256') --contimeout 10s --timeout 60s 2>&1 | Out-Null

    $dl = Join-Path $scr 'dl.tar.gz'
    & rclone copyto ($dest + 'critical.tar.gz') $dl --contimeout 10s --timeout 180s 2>&1 | Out-Null
    $dsha = (Get-FileHash -Algorithm SHA256 -LiteralPath $dl).Hash.ToLower()
    $verified = ($sha -eq $dsha)

    $snaps = @(& rclone lsf $base/ --dirs-only 2>$null | ForEach-Object { $_.TrimEnd('/') } | Sort-Object)
    if ($snaps.Count -gt $Keep) {
        foreach ($old in $snaps[0..($snaps.Count - $Keep - 1)]) { & rclone purge "$base/$old" 2>&1 | Out-Null }
    }

    $driftNote = ''
    try {
        $size = & rclone size ":s3:$bucket" --json --contimeout 10s --timeout 120s 2>$null | ConvertFrom-Json
        if ($size -and $null -ne $size.bytes) {
            $inventory = Join-Path (Split-Path $log) 'oort-inventory.tsv'
            if (-not (Test-Path $inventory)) { Set-Content -LiteralPath $inventory -Value "iso`tobjects`tbytes" }
            $now = Get-Date
            Add-Content -LiteralPath $inventory -Value "$($now.ToString('o'))`t$($size.count)`t$($size.bytes)"
            $rows = @(Import-Csv -LiteralPath $inventory -Delimiter "`t" | Where-Object { [datetime]$_.iso -ge $now.AddDays(-30) })
            $first = $rows | Select-Object -First 1
            if ($rows.Count -ge 2 -and [double]$first.bytes -gt 0 -and [double]$size.bytes -gt 2 * [double]$first.bytes) {
                $driftNote = " WARN:oort-size-doubled-in-30d($([math]::Round([double]$first.bytes/1GB,2))->$([math]::Round([double]$size.bytes/1GB,2))GiB)"
            }
            $driftNote = " oort=$($size.count)obj/$([math]::Round([double]$size.bytes/1GB,2))GiB" + $driftNote
        }
        else { $driftNote = ' oort-inventory=UNKNOWN' }
    }
    catch { $driftNote = ' oort-inventory=UNKNOWN' }

    $line = if ($verified) { "store-offsite-backup $stamp ok: $mb MB -> oort $dest VERIFIED hash-identical ($($sha.Substring(0,16))); kept last $Keep of $($snaps.Count)$snapNote$driftNote" }
    else { "store-offsite-backup $stamp FAIL: round-trip sha mismatch ($($sha.Substring(0,12)) vs $($dsha.Substring(0,12)))" }
    Set-Content -LiteralPath $log -Value $line
    $line
    if (-not $verified) { exit 1 }
}
finally {
    Remove-Item -Recurse -Force $scr -ErrorAction SilentlyContinue
    foreach ($k in 'ACCESS_KEY_ID', 'SECRET_ACCESS_KEY') { Remove-Item "env:RCLONE_S3_$k" -ErrorAction SilentlyContinue }
}
