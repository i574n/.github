param(
    [string] $TempRoot = (Join-Path $env:LOCALAPPDATA 'Temp\claude'),
    [string] $Transcripts = (Join-Path $HOME '.claude\projects'),
    [string] $Standing = (Join-Path $HOME '.claude\autonomous\standing.txt'),
    [int] $AliveHours = 6,
    [string] $Out = ''
)
$ErrorActionPreference = 'Stop'
$copyFolders = '^(target|node_modules|\.cache|_build|deps|bin-node|sdk-src|\.git|obj|bin|zig-out|\.zig-cache)$|backup|^pre-land|-copy$|^copy-|mirror|snapshot'
$deliverableExtensions = @('.spi', '.spir', '.spiproj', '.ps1', '.do', '.rs', '.fs', '.gleam', '.lua', '.zig', '.py', '.exs', '.ex', '.toml', '.json', '.md')
$teams = @{}
if (Test-Path $Standing) { foreach ($line in [IO.File]::ReadAllLines($Standing)) { if ($line -match '^([0-9a-f]{8})=(\S+)$') { $teams[$Matches[1]] = $Matches[2] } } }
function Measure-Tree([string] $root) {
    $stats = [pscustomobject]@{ Files = 0; Bytes = [int64]0; Newest = [datetime]::MinValue; Deliverables = 0; CopyFolders = 0 }
    $pending = [Collections.Generic.Stack[string]]::new(); $pending.Push($root)
    while ($pending.Count) {
        $folder = $pending.Pop()
        try {
            foreach ($child in [IO.Directory]::EnumerateDirectories($folder)) {
                $name = [IO.Path]::GetFileName($child)
                if ((Get-Item -LiteralPath $child -Force).LinkType) { continue }
                if ($name -match $copyFolders) { $stats.CopyFolders++ } else { $pending.Push($child) }
            }
            foreach ($file in [IO.Directory]::EnumerateFiles($folder)) {
                $info = [IO.FileInfo]::new($file)
                $stats.Files++; $stats.Bytes += $info.Length
                if ($info.LastWriteTime -gt $stats.Newest) { $stats.Newest = $info.LastWriteTime }
                if ($info.Extension.ToLowerInvariant() -in $deliverableExtensions) { $stats.Deliverables++ }
            }
        } catch { }
    }
    $stats
}
$rows = foreach ($project in [IO.Directory]::GetDirectories($TempRoot)) {
    foreach ($session in [IO.Directory]::GetDirectories($project)) {
        $id = [IO.Path]::GetFileName($session)
        if ($id -notmatch '^[0-9a-f]{8}-') { continue }
        $scratch = Join-Path $session 'scratchpad'
        if (-not (Test-Path $scratch)) { continue }
        $transcript = Join-Path (Join-Path $Transcripts ([IO.Path]::GetFileName($project))) "$id.jsonl"
        $last = if (Test-Path $transcript) { [IO.File]::GetLastWriteTime($transcript) } else { $null }
        $alive = $last -and $last -gt (Get-Date).AddHours(-$AliveHours)
        $stats = Measure-Tree $scratch
        [pscustomobject]@{
            Session = $id.Substring(0, 8)
            Team = $teams[$id.Substring(0, 8)] ?? '-'
            Alive = $alive
            LastTurn = if ($last) { $last.ToString('MM-dd HH:mm') } else { '-' }
            GB = [math]::Round($stats.Bytes / 1GB, 2)
            Files = $stats.Files
            Deliverables = $stats.Deliverables
            CopyFolders = $stats.CopyFolders
            Newest = if ($stats.Files) { $stats.Newest.ToString('MM-dd HH:mm') } else { '-' }
        }
    }
}
$rows = @($rows | Sort-Object GB -Descending)
$table = $rows | Format-Table -AutoSize | Out-String -Width 200
$summary = "scratch inventory $(Get-Date -Format 'yyyy-MM-dd HH:mm'): $($rows.Count) session scratchpads, $([math]::Round(($rows | Measure-Object GB -Sum).Sum, 1)) GB outside copy folders, $(@($rows | Where-Object Alive).Count) alive, $(($rows | Measure-Object Deliverables -Sum).Sum) deliverable-looking files (source and script extensions outside copy folders)"
if ($Out) { Set-Content -Path $Out -Value ($table, $summary) }
$table
$summary
