param(
    [string] $Root = 'C:\home\git',
    [string] $Store = (Join-Path $env:LOCALAPPDATA 'kit\backups\unstaged'),
    [int] $KeepDays = 14,
    [int] $MaxFileMB = 20,
    [switch] $Verify
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$run = Join-Path $Store $stamp
New-Item -ItemType Directory -Force $run | Out-Null
$rows = foreach ($repo in [IO.Directory]::GetDirectories($Root)) {
    $item = Get-Item -LiteralPath $repo
    if ($item.LinkType -or -not (Test-Path (Join-Path $repo '.git'))) { continue }
    $name = $item.Name
    $diff = @(& git -C $repo diff --binary 2>$null)
    $index = @(& git -C $repo diff --cached --binary 2>$null)
    $untracked = @(& git -C $repo ls-files --others --exclude-standard -z 2>$null | ForEach-Object { $_ -split "`0" } | Where-Object { $_ })
    $head = "$(& git -C $repo rev-parse HEAD 2>$null)"
    $kept = [Collections.Generic.List[string]]::new(); $skipped = [Collections.Generic.List[string]]::new()
    foreach ($relative in $untracked) {
        $full = Join-Path $repo $relative
        if (-not [IO.File]::Exists($full)) { continue }
        if ((Get-Item -LiteralPath $full).Length -gt $MaxFileMB * 1MB) { $skipped.Add($relative) } else { $kept.Add($relative) }
    }
    if (-not $diff.Count -and -not $index.Count -and -not $kept.Count) { continue }
    $zipPath = Join-Path $run "$name.zip"
    $zip = [IO.Compression.ZipFile]::Open($zipPath, 'Create')
    try {
        foreach ($part in @(@('unstaged.patch', $diff), @('staged.patch', $index))) {
            if (-not $part[1].Count) { continue }
            $entry = $zip.CreateEntry($part[0]); $writer = [IO.StreamWriter]::new($entry.Open())
            try { $writer.Write(($part[1] -join "`n") + "`n") } finally { $writer.Dispose() }
        }
        $meta = $zip.CreateEntry('backup.txt'); $writer = [IO.StreamWriter]::new($meta.Open())
        try { $writer.Write("repo=$repo`nhead=$head`nat=$stamp`nuntracked=$($kept.Count)`nskipped_over_${MaxFileMB}mb=$($skipped -join ';')`n") } finally { $writer.Dispose() }
        foreach ($relative in $kept) {
            try { [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $repo $relative), "untracked/$($relative.Replace('\', '/'))") }
            catch { $skipped.Add("$relative (unreadable)") }
        }
    } finally { $zip.Dispose() }
    [pscustomobject]@{ Repo = $name; Head = $head.Substring(0, [Math]::Min(8, $head.Length)); UnstagedLines = $diff.Count; StagedLines = $index.Count; Untracked = $kept.Count; Skipped = $skipped.Count; MB = [math]::Round((Get-Item $zipPath).Length / 1MB, 2) }
}
$rows = @($rows)
if ($Verify) {
    foreach ($row in $rows) {
        $zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $run "$($row.Repo).zip"))
        try {
            $mismatch = 0
            foreach ($entry in $zip.Entries | Where-Object { $_.FullName -like 'untracked/*' }) {
                $source = Join-Path (Join-Path $Root $row.Repo) $entry.FullName.Substring(10)
                $stream = $entry.Open(); $memory = [IO.MemoryStream]::new()
                try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
                $archived = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($memory.ToArray()))
                if (-not (Test-Path -LiteralPath $source) -or (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $archived) { $mismatch++ }
            }
            $row | Add-Member -NotePropertyName VerifyMismatch -NotePropertyValue $mismatch
        } finally { $zip.Dispose() }
    }
}
$expired = @(Get-ChildItem $Store -Directory | Where-Object { $_.Name -match '^\d{8}-\d{6}$' -and $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) })
$rows | Format-Table -AutoSize | Out-String -Width 160
"unstaged backup $stamp -> $run : $($rows.Count) repos with a layer, $([math]::Round(($rows | Measure-Object MB -Sum).Sum, 1)) MB; runs older than $KeepDays days (kept, listed for the archive rule): $($expired.Count)"
