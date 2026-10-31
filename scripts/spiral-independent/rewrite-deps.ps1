param(
    [string[]] $Repos = @('dice', 'alphabet'),
    [switch] $Apply
)
$ErrorActionPreference = 'Stop'
$reposRoot = 'C:\home\git'

$pathRules = @(
    @{ Old = 'deps/polyglot/deps/spiral/'; New = 'deps/spiral/' }
    @{ Old = 'deps/polyglot/scripts/core.ps1'; New = 'deps/spiral/scripts/core.ps1' }
    @{ Old = '../../polyglot/deps/spiral/'; New = '../../spiral/' }
    @{ Old = '../polyglot/scripts/core.ps1'; New = '../spiral/scripts/core.ps1' }
)
$cloneBlock = [regex]::new('(?m)^([ \t]*)Set-Location polyglot\n\1git pull\n')
$spiralCloneLines = @('Set-Location ..', 'git clone --recurse-submodules https://$domain/$owner/spiral.git', 'Set-Location spiral', 'git pull')
$polyglotLinkLine = 'EnsureSymbolicLink -Path "../deps/polyglot" -Target "../../polyglot"'
$spiralLinkLine = 'EnsureSymbolicLink -Path "../deps/spiral" -Target "../../spiral"'
$todoBefore = "# TODO: SPIRAL-INDEPENDENT dice still reaches spiral through polyglot (deps/polyglot/deps/spiral, polyglot core.ps1);`n#     lab/SPIRAL-INDEPENDENT/rewrite-deps.ps1 moves 22 files of dice + alphabet to deps/spiral, Common.fs keeps polyglot"
$todoAfter = "# TODO: SPIRAL-INDEPENDENT dice keeps deps/polyglot only for lib/fsharp/build.ps1's Common.fs (polyglot/lib/fsharp);`n#     drop this link and the polyglot init once the F# lib of dice retires or Common.fs moves"
$polyglotInitLine ="{ pwsh ../../polyglot/scripts/init.ps1 -fast `$(`$fast ?? '') } | Invoke-Block"

function Get-RewrittenText([string] $repo, [string] $relative, [string] $text) {
    $rewritten = $text
    foreach ($rule in $pathRules) { $rewritten = $rewritten.Replace($rule.Old, $rule.New) }
    if ($relative -eq 'scripts/init.ps1') {
        $rewritten = ($rewritten.Split("`n") | Where-Object { -not $_.Contains('polyglot/apps/parser/build.ps1') }) -join "`n"
        $rewritten = $rewritten.Replace($todoBefore, $todoAfter)
        if (-not $rewritten.Contains('/spiral.git')) {
            if ($cloneBlock.Matches($rewritten).Count -ne 1) { throw "$repo/$relative has no single polyglot clone block to add the spiral clone to" }
            $rewritten = $cloneBlock.Replace($rewritten, [System.Text.RegularExpressions.MatchEvaluator] {
                param($match)
                $indent = $match.Groups[1].Value
                $match.Value + (($spiralCloneLines | ForEach-Object { "$indent$_`n" }) -join '')
            }, 1)
        }
        if ($repo -eq 'alphabet') {
            $rewritten = ($rewritten.Split("`n") | Where-Object { $_.Trim() -ne $polyglotLinkLine }) -join "`n"
        }
    }
    $initNeedsSpiralLink = $relative -eq 'scripts/init.ps1' -and -not $rewritten.Contains($spiralLinkLine)
    if ($initNeedsSpiralLink) {
        $anchor = if ($rewritten.Contains($polyglotInitLine)) { $polyglotInitLine } else { $polyglotLinkLine }
        if (-not $rewritten.Contains($anchor)) { throw "$relative has neither the polyglot init line nor the polyglot link line" }
        $rewritten = $rewritten.Replace($anchor, "$anchor`n$spiralLinkLine")
    }
    $rewritten
}

function Get-TrackedScripts([string] $root) {
    git -C $root ls-files -- '*.ps1' '*.spiproj' '*.yml' '*.toml' '*.json'
}

$changedFiles = [System.Collections.Generic.List[string]]::new()
$keptReferences = [System.Collections.Generic.List[string]]::new()
$pendingWrites = [System.Collections.Generic.List[object]]::new()
foreach ($repo in $Repos) {
    $root = Join-Path $reposRoot $repo
    foreach ($relative in (Get-TrackedScripts $root)) {
        $path = Join-Path $root $relative
        $text = [IO.File]::ReadAllText($path)
        $rewritten = Get-RewrittenText $repo $relative $text
        $lineNumber = 0
        foreach ($line in $rewritten.Split("`n")) {
            $lineNumber++
            if ($line.Contains('deps/polyglot')) { $keptReferences.Add("  $repo/${relative}:$lineNumber $($line.Trim())") }
        }
        if ($rewritten -eq $text) { continue }
        if ($relative.EndsWith('.ps1')) {
            $tokens = $null
            $parseErrors = $null
            [void][System.Management.Automation.Language.Parser]::ParseInput($rewritten, [ref]$tokens, [ref]$parseErrors)
            if ($parseErrors.Count -gt 0) { throw "$repo/$relative does not parse after the rewrite: $($parseErrors[0].Message)" }
        }
        $changedFiles.Add("$repo/$relative")
        "== $repo/$relative"
        $preview = Join-Path $env:LOCALAPPDATA 'spiral-bin/runs/spiral-independent-preview'
        New-Item -ItemType Directory -Force $preview | Out-Null
        $beforeFile = Join-Path $preview 'before.txt'
        $afterFile = Join-Path $preview 'after.txt'
        [IO.File]::WriteAllText($beforeFile, $text)
        [IO.File]::WriteAllText($afterFile, $rewritten)
        git diff --no-index --no-color -U0 $beforeFile $afterFile | Where-Object { $_ -match '^[+-][^+-]|^[+-]$' } | ForEach-Object { "  $_" }
        $pendingWrites.Add([pscustomobject]@{ Path = $path; Text = $rewritten })
    }
}
if ($Apply) { foreach ($write in $pendingWrites) { [IO.File]::WriteAllText($write.Path, $write.Text) } }
"files changed: $($changedFiles.Count) (applied: $([bool] $Apply))"
"deps/polyglot references left after the rewrite (polyglot-owned files, kept on purpose):"
$keptReferences
