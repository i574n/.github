param(
    [Parameter(Mandatory)] [string] $Name,
    [Parameter(Mandatory)] [string] $Root,
    [string[]] $Exclude = @(),
    [switch] $Apply,
    [switch] $SkipFormat,
    [string] $Compiler,
    [switch] $SymbolTestHeaders
)
$ErrorActionPreference = 'Stop'
$Exclude = @($Exclude | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$work = Join-Path ([IO.Path]::GetTempPath()) "respell-repo\$Name"
if (Test-Path $work) { Remove-Item -LiteralPath $work -Recurse -Force }
$original = Join-Path $work 'original'
$symbols = Join-Path $work 'symbols'
$words = Join-Path $work 'words'
$baseline = Join-Path $work 'baseline-words'
$tracked = @(git -C $Root ls-files -- '*.spi' '*.spir' 2>$null | Where-Object { $rel = $_; -not ($Exclude | Where-Object { $rel -like $_ }) })
$untracked = @(git -C $Root ls-files --others --exclude-standard -- '*.spi' '*.spir' 2>$null | Where-Object { $rel = $_; -not ($Exclude | Where-Object { $rel -like $_ }) })
$files = @($tracked + $untracked | Sort-Object -Unique)
if (-not $files.Count) { "no .spi files under $Root"; exit 0 }
foreach ($rel in $files) {
    foreach ($copy in $original, $symbols, $words, $baseline) {
        $to = Join-Path $copy $rel
        New-Item -ItemType Directory -Force (Split-Path $to) | Out-Null
        Copy-Item -LiteralPath (Join-Path $Root $rel) $to
    }
}
. 'C:\home\git\spiral\apps\compiler\tmp\scripts\env.ps1'
$dotnet = Resolve-SpiralDotnet
$candidate = if ($Compiler) { $Compiler } else { Get-ChildItem (Join-Path $env:LOCALAPPDATA 'spiral-bin\candidates\pack-spelling\bin\single-flight') -Recurse -Filter SpiralCompiler.dll | Where-Object FullName -match '\\Debug\\' | Select-Object -First 1 -ExpandProperty FullName }
$installed = Join-Path $env:LOCALAPPDATA 'spiral-bin\bin\single-flight\SpiralCompiler\Release\net11.0\SpiralCompiler.dll'
$env:SPIRAL_FORMAT_PARALLEL = '2'
$toSymbols = @(& $dotnet $candidate format --spelling symbols $symbols 2>&1 | ForEach-Object { "$_" })
$errors = @($toSymbols | Where-Object { $_ -match '^(format error|rejected)\b' })
$headersRestored = 0
$changed = [Collections.Generic.List[string]]::new()
foreach ($rel in $files) {
    $before = [IO.File]::ReadAllText((Join-Path $original $rel))
    $path = Join-Path $symbols $rel
    $after = [IO.File]::ReadAllText($path)
    $a = $before.Split("`n"); $b = $after.Split("`n")
    $finalNewlineAdded = -not $before.EndsWith("`n") -and $b.Count -eq $a.Count + 1 -and $b[-1] -eq ''
    if ($a.Count -ne $b.Count -and -not $finalNewlineAdded) { $errors += "$rel line count $($a.Count) -> $($b.Count)"; continue }
    if (-not $SymbolTestHeaders) { for ($i = 0; $i -lt $a.Count; $i++) { if ($a[$i].StartsWith('test ') -and $b[$i] -ne $a[$i]) { $b[$i] = $a[$i]; $headersRestored++ } } }
    $fixed = [string]::Join("`n", $b)
    if ($fixed -ne $after) { [IO.File]::WriteAllText($path, $fixed, [Text.UTF8Encoding]::new($false)) }
    if ($fixed -ne $before) { $changed.Add($rel) }
}
foreach ($rel in $changed) { Copy-Item -LiteralPath (Join-Path $symbols $rel) (Join-Path $words $rel) -Force }
& $dotnet $candidate format --spelling words $words 2>&1 | Out-Null
& $dotnet $candidate format --spelling words $baseline 2>&1 | Out-Null
$notBack = @($files | Where-Object { [IO.File]::ReadAllText((Join-Path $words $_)).TrimEnd("`n") -ne [IO.File]::ReadAllText((Join-Path $baseline $_)).TrimEnd("`n") })
$hashes = @{}; foreach ($rel in $files) { $hashes[$rel] = (Get-FileHash (Join-Path $symbols $rel)).Hash }
if (-not $SkipFormat) { & $dotnet $installed format $symbols 2>&1 | Out-Null }
$formatterChanged = @($files | Where-Object { (Get-FileHash (Join-Path $symbols $_)).Hash -ne $hashes[$_] })
"${Name} ($Root): $($files.Count) files, $($changed.Count) respelled, $headersRestored test headers kept as written, errors $($errors.Count), words round trip differs $($notBack.Count), installed formatter changes $($formatterChanged.Count)"
$errors | Select-Object -First 5 | ForEach-Object { "  error: $_" }
$notBack | Select-Object -First 5 | ForEach-Object { "  not back: $_" }
$formatterChanged | Select-Object -First 5 | ForEach-Object { "  formatter: $_" }
$green = -not $errors.Count -and -not $notBack.Count
if (-not $green) { "$Name RED: nothing applied"; exit 1 }
if ($formatterChanged.Count) { "${Name}: the installed formatter normalizes $($formatterChanged.Count) files on top; the land applies the formatted text" }
if (-not $Apply) { "$Name GREEN on copies; pass -Apply to write the $($changed.Count + $formatterChanged.Count) changed files into $Root (originals stay in $original)"; exit 0 }
$touched = @($changed + $formatterChanged | Sort-Object -Unique)
foreach ($rel in $touched) { Copy-Item -LiteralPath (Join-Path $symbols $rel) (Join-Path $Root $rel) -Force }
"$Name applied: $($touched.Count) files in $Root; restore with robocopy $original $Root /E"
