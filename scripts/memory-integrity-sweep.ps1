#requires -Version 5.1
param(
    [string] $Dir,
    [string] $Backlog = (Join-Path $PSScriptRoot '..\docs\backlog'),
    [switch] $NoteOnDrift
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'agent-runtime.ps1')
if (-not $Dir) { $Dir = Get-AgentMemoryDir }
$index = Join-Path $Dir 'MEMORY.md'
$issues = [Collections.Generic.List[string]]::new()
$files = Get-ChildItem $Dir -Filter '*.md' -File | Where-Object { $_.Name -ne 'MEMORY.md' }
$shingles = @{}
$refLinks = [Collections.Generic.HashSet[string]]::new()
foreach ($f in $files) {
    $raw = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction SilentlyContinue
    if ("$raw" -notmatch '(?s)^---\s*\r?\n.*?\r?\n---') { $issues.Add("NO-FRONTMATTER $($f.Name)"); continue }
    $fm = [regex]::Match($raw, '(?s)^---\s*\r?\n(.*?)\r?\n---').Groups[1].Value
    $nameDecl = [regex]::Match($fm, '(?m)^\s*name:\s*(.+?)\s*$').Groups[1].Value
    if (-not $nameDecl) { $issues.Add("NO-NAME $($f.Name)") }
    elseif ($nameDecl -ne $f.BaseName) { $issues.Add("NAME-MISMATCH $($f.Name): name='$nameDecl'") }
    if ($fm -notmatch '(?m)^\s*description:\s*\S') { $issues.Add("NO-DESCRIPTION $($f.Name)") }
    if ($fm -notmatch '(?m)type:\s*\S') { $issues.Add("NO-TYPE $($f.Name)") }
    $body = ($raw -replace '(?s)^---\s*\r?\n.*?\r?\n---', '')
    foreach ($m in [regex]::Matches($body, '\[\[([a-z0-9][a-z0-9-]*)\]\]')) { [void]$refLinks.Add($m.Groups[1].Value) }
    $words = @([regex]::Matches($body.ToLower(), '[a-z0-9]+') | ForEach-Object { $_.Value })
    $sh = [Collections.Generic.HashSet[string]]::new()
    for ($i = 0; $i + 6 -le $words.Count; $i++) { [void]$sh.Add(($words[$i..($i + 5)] -join ' ')) }
    if ($sh.Count -gt 0) { $shingles[$f.Name] = $sh }
}
$idx = Get-Content -LiteralPath $index -Raw -ErrorAction SilentlyContinue
$linked = [regex]::Matches("$idx", '\(([A-Za-z0-9._-]+\.md)\)') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique
$onDisk = $files.Name
foreach ($l in $linked) { if ($l -notin $onDisk) { $issues.Add("INDEX-DEAD-LINK $l") } }
$orphans = @($onDisk | Where-Object { $_ -notin $linked })
foreach ($o in $orphans) { $issues.Add("ORPHAN $o") }
$slugs = [Collections.Generic.HashSet[string]]::new(); $files | ForEach-Object { [void]$slugs.Add($_.BaseName) }
$futureLinks = @($refLinks | Where-Object { -not $slugs.Contains($_) } | Sort-Object)
$dups = [Collections.Generic.List[string]]::new()
$names2 = @($shingles.Keys)
for ($i = 0; $i -lt $names2.Count; $i++) {
    for ($j = $i + 1; $j -lt $names2.Count; $j++) {
        $a = $shingles[$names2[$i]]; $b = $shingles[$names2[$j]]
        $small = if ($a.Count -le $b.Count) { $a } else { $b }; $big = if ($a.Count -le $b.Count) { $b } else { $a }
        $inter = 0; foreach ($s in $small) { if ($big.Contains($s)) { $inter++ } }
        $union = $a.Count + $b.Count - $inter
        if ($union -gt 0 -and ($inter / $union) -ge 0.35) { $dups.Add(("DUP-CANDIDATE {0} <-> {1} ({2:N2})" -f $names2[$i], $names2[$j], ($inter / $union))) }
    }
}
$citeLine = 'citations: (skipped)'
try {
    $cl = & (Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe') agile links $Backlog --memory $Dir 2>$null | Select-Object -Last 1
    if ("$cl".Trim()) { $citeLine = "citations: $("$cl".Trim())" }
}
catch { $citeLine = 'citations: (error)' }
$verdict = if ($issues.Count) { "DRIFT ($($issues.Count))" } else { 'clean' }
$line = "memory-integrity-sweep $(Get-Date -Format 'yyyy-MM-dd HH:mm') ${verdict}: $($files.Count) files, $($linked.Count) indexed, $($orphans.Count) orphan, $($futureLinks.Count) future-links, $($dups.Count) dup-candidates"
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\memory-integrity-sweep.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Set-Content -LiteralPath $log -Value (@($line) + $issues + $dups + @("future-links: $($futureLinks -join ', ')", $citeLine))
$line
$issues | Select-Object -First 30
$dups
$citeLine
if ($issues.Count) {
    if ($NoteOnDrift) { & (Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe') agile note (Join-Path $PSScriptRoot '..\docs\backlog') MEMORY-INTEGRITY-SWEEP "sweep: $line (first: $($issues[0]))" --as org 2>&1 | Out-Null; if ($LASTEXITCODE -ne 0) { Add-Content -LiteralPath $log -Value "memory-integrity-sweep $(Get-Date -Format 'yyyy-MM-dd HH:mm') NOTE FAILED rc=$LASTEXITCODE (MEMORY-INTEGRITY-SWEEP not delivered)" } }
    exit 1
}
exit 0
