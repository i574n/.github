param(
    [Parameter(Mandatory)] [string] $Root,
    [switch] $Apply,
    [int] $Show = 12
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path $Root).Path
$opener = '!!!!Export('

function Get-SpiFiles {
    if (Test-Path (Join-Path $Root '.git')) {
        git -C $Root grep -l -F --untracked $opener -- '*.spi' 2>$null | ForEach-Object { Join-Path $Root ($_ -replace '/', '\') }
    } else {
        Get-ChildItem $Root -Recurse -File -Filter *.spi | Where-Object { $_.FullName -notmatch '\\(target|\.cache|_build|deps|node_modules)\\' -and (Select-String -LiteralPath $_.FullName -SimpleMatch $opener -Quiet) } | ForEach-Object FullName
    }
}

function Find-CallEnd([string]$Text, [int]$Start) {
    $depth = 1
    $comma = -1
    $i = $Start
    while ($i -lt $Text.Length) {
        $c = $Text[$i]
        if ($c -eq '"') {
            $i++
            while ($i -lt $Text.Length -and $Text[$i] -ne '"') { if ($Text[$i] -eq '\') { $i++ }; $i++ }
        } elseif ($c -eq "'" -and ($i -eq 0 -or $Text[$i - 1] -notmatch "[\w']")) {
            $i++
            while ($i -lt $Text.Length -and $Text[$i] -ne "'") { if ($Text[$i] -eq '\') { $i++ }; $i++ }
        } elseif ($c -eq '/' -and $i + 1 -lt $Text.Length -and $Text[$i + 1] -eq '/') {
            while ($i -lt $Text.Length -and $Text[$i] -ne "`n") { $i++ }
            continue
        } elseif ($c -eq '(' -or $c -eq '[' -or $c -eq '{') {
            $depth++
        } elseif ($c -eq ')' -or $c -eq ']' -or $c -eq '}') {
            $depth--
            if ($depth -eq 0) { return @{ Close = $i; Comma = $comma } }
        } elseif ($c -eq ',' -and $depth -eq 1 -and $comma -lt 0) {
            $comma = $i
        }
        $i++
    }
    $null
}

function Test-Wrapped([string]$F) {
    if (-not $F.StartsWith('(')) { return $false }
    $end = Find-CallEnd $F 1
    $end -and $end.Close -eq $F.Length - 1
}

function Convert-Site([string]$Text, [int]$At) {
    $end = Find-CallEnd $Text ($At + $opener.Length)
    if (-not $end) { return @{ Skip = 'unbalanced' } }
    if ($end.Comma -lt 0) { return @{ Skip = 'one argument' } }
    $nameRaw = $Text.Substring($At + $opener.Length, $end.Comma - $At - $opener.Length)
    $name = $nameRaw.Trim()
    if ($name -notmatch '^("[^"\\]*"|[A-Za-z_][\w'']*(\.[A-Za-z_][\w'']*)*)$') { return @{ Skip = "name is neither a literal nor an identifier path: $name" } }
    $f = $Text.Substring($end.Comma + 1, $end.Close - $end.Comma - 1)
    $lineEnd = $Text.IndexOf("`n", $end.Close)
    $tail = if ($lineEnd -lt 0) { $Text.Substring($end.Close + 1) } else { $Text.Substring($end.Close + 1, $lineEnd - $end.Close - 1) }
    if ($nameRaw -notmatch "`n" -and $f -notmatch "`n") {
        $body = $f.Trim()
        $call = if (Test-Wrapped $body) { $body } else { "($body)" }
        return @{ Start = $At; Length = $end.Close - $At + 1; New = "export $name $call"; Shape = 'single-line' }
    }
    $stacked = [regex]::Match($Text.Substring($At, $end.Close - $At + 1), '^!!!!Export\(\r?\n([ \t]+(?:"[^"\\]*"|[A-Za-z_][\w'']*(?:\.[A-Za-z_][\w'']*)*)),\r?\n([\s\S]*?)\r?\n[ \t]*\)$')
    if (-not $stacked.Success) { return @{ Skip = 'multi-line shape not stacked' } }
    if ($tail.Trim()) { return @{ Skip = "code after the closing paren: $($tail.Trim())" } }
    @{ Start = $At; Length = $end.Close - $At + 1; New = "export`n$($stacked.Groups[1].Value)`n$($stacked.Groups[2].Value)"; Shape = 'stacked' }
}

function Get-PackageFile([string]$File) {
    $d = Split-Path $File -Parent
    while ($d -and $d.Length -ge $Root.Length) {
        $p = Join-Path $d 'package.spiproj'
        if (Test-Path $p) { return $p }
        $d = Split-Path $d -Parent
    }
    $null
}

$files = @(Get-SpiFiles)
$sites = 0; $byShape = @{}; $skipped = [System.Collections.Generic.List[string]]::new(); $plans = [ordered]@{}; $needSpiral = [ordered]@{}
foreach ($file in $files) {
    $text = [IO.File]::ReadAllText($file)
    if ($text -match '(?m)^:=\s*export\b') { $skipped.Add("$file : defines export itself (a wrapper)"); continue }
    $package = Get-PackageFile $file
    if (-not $package) { $skipped.Add("$file : no package.spiproj"); continue }
    $projText = [IO.File]::ReadAllText($package)
    $hasSpiral = $projText -match '(?m)^\s*\|?\s*spiral-\s*$'
    if (-not $hasSpiral -and $projText -notmatch '(?m)^[ \t]+\|core-[ \t]*\r?$') { $skipped.Add("$file : $package has no |core- line to add spiral- after"); continue }
    $edits = foreach ($m in [regex]::Matches($text, [regex]::Escape($opener))) {
        $e = Convert-Site $text $m.Index
        if ($e.Skip) { $skipped.Add("${file}:$(($text.Substring(0, $m.Index) -split "`n").Count) : $($e.Skip)") } else { $e }
    }
    $edits = @($edits | Where-Object { $_ })
    if (-not $edits) { continue }
    foreach ($e in ($edits | Sort-Object Start -Descending)) { $text = $text.Remove($e.Start, $e.Length).Insert($e.Start, $e.New) }
    if ($text.Contains($opener) -and -not ($skipped | Where-Object { $_.StartsWith("${file}:") })) { throw "sites left in $file after rewrite" }
    $plans[$file] = $text
    $sites += $edits.Count
    foreach ($e in $edits) { $byShape[$e.Shape] = 1 + [int]$byShape[$e.Shape] }
    if (-not $hasSpiral) { $needSpiral[$package] = $true }
}

"files with !!!!Export: $($files.Count); rewritten sites: $sites ($(($byShape.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name) $($_.Value)" }) -join ', ')) in $($plans.Count) files; packages given spiral-: $($needSpiral.Count); skipped: $($skipped.Count)"
$skipped | Select-Object -First $Show | ForEach-Object { "  skip $($_.Substring([Math]::Min($Root.Length + 1, $_.Length)))" }
if (-not $Apply) { 'dry run: nothing written'; exit 0 }

$utf8 = { param($path) $b = [IO.File]::ReadAllBytes($path); [Text.UTF8Encoding]::new($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) }
foreach ($file in $plans.Keys) { [IO.File]::WriteAllText($file, $plans[$file], (& $utf8 $file)) }
foreach ($package in $needSpiral.Keys) {
    $projText = [IO.File]::ReadAllText($package)
    $m = [regex]::Match($projText, '(?m)^([ \t]+)\|core-[ \t]*(\r?\n)')
    [IO.File]::WriteAllText($package, $projText.Insert($m.Index + $m.Length, "$($m.Groups[1].Value)spiral-$($m.Groups[2].Value)"), (& $utf8 $package))
}
"applied: $sites sites in $($plans.Count) files, spiral- added to $($needSpiral.Count) package.spiproj"
