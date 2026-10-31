param(
    [Parameter(Mandatory)] [string] $Tree,
    [string] $Dll = '',
    [string] $GitRoot = 'C:\home\git',
    [int] $Throttle = 4
)
$ErrorActionPreference = 'Continue'
$dotnet = Join-Path $env:LOCALAPPDATA 'spiral-bin\toolchains\dotnet\dotnet.exe'
$env:SPIRAL_COMPILER_PACKAGE_DIR = 'C:\home\git\spiral\deps\The-Spiral-Language\VS Code Plugin'
if (-not $Dll) {
    $envScript = @((Join-Path $GitRoot 'spiral/apps/compiler/scripts/env.ps1'), (Join-Path $GitRoot 'spiral/apps/compiler/tmp/scripts/env.ps1')) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $envScript) { "regen compare VOID: no compiler env.ps1 under $GitRoot"; exit 2 }
    . $envScript
    $Dll = Get-SpiralCompilerDll (ConvertTo-SpiralMode 'single-flight') 'Release'
}
$coreDll = Join-Path (Split-Path $Dll) 'SpiralCompilerCore.dll'
$core = if (Test-Path $coreDll) { (Get-FileHash $coreDll -Algorithm SHA256).Hash.Substring(0, 16) } else { 'MISSING' }
"dll $((Get-FileHash $Dll).Hash.Substring(0, 12)) core $core at $Dll; dotnet $(Test-Path $dotnet)"
if (Test-Path $Tree) { Remove-Item $Tree -Recurse -Force }
Expand-Archive "$Tree.zip" $Tree
$crates = @(Get-Content (Join-Path $Tree 'crates.txt') | Where-Object { $_ })
$jobs = foreach ($crate in $crates) { foreach ($side in 'base', 'cand') { [pscustomobject]@{ Crate = $crate; Side = $side } } }
$rows = $jobs | ForEach-Object -ThrottleLimit $Throttle -Parallel {
    $src = Join-Path $using:Tree "$($_.Side)\src"
    $entry = [regex]::Match([IO.File]::ReadAllText((Join-Path $src "$($_.Crate)\Cargo.toml")), '(?m)^entry\s*=\s*"([^"]+)"').Groups[1].Value
    $out = Join-Path $using:Tree "$($_.Side)-out\$($_.Crate).rs"
    New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
    $global:LASTEXITCODE = 0
    $text = (& $using:dotnet $using:Dll --backend Rust (Join-Path $src "$($_.Crate)\$entry") $out 2>&1 | Select-Object -First 4) -join ' '
    [pscustomobject]@{ Crate = $_.Crate; Side = $_.Side; Lines = (Test-Path $out) ? @(Get-Content $out).Count : -1; Note = $text.Substring(0, [Math]::Min(400, $text.Length)) }
}
if (@($rows | Where-Object Lines -ge 0).Count -eq 0) { "regen compare VOID: nothing compiled"; exit 2 }
foreach ($crate in $crates) {
    $base = $rows | Where-Object { $_.Crate -eq $crate -and $_.Side -eq 'base' }
    $cand = $rows | Where-Object { $_.Crate -eq $crate -and $_.Side -eq 'cand' }
    $baseFile = Join-Path $Tree "base-out\$crate.rs"
    $candFile = Join-Path $Tree "cand-out\$crate.rs"
    $same = (Test-Path $baseFile) -and (Test-Path $candFile) -and ((Get-FileHash $baseFile).Hash -eq (Get-FileHash $candFile).Hash)
    "{0,-26} base {1,5} cand {2,5} identical={3}" -f $crate, $base.Lines, $cand.Lines, $same
    if ($cand.Lines -lt 0) { "  CAND FAILED: $($cand.Note)" }
    if ($base.Lines -lt 0) { "  BASE FAILED: $($base.Note)" }
}
"candidate crates over 1000 lines: $(@($rows | Where-Object { $_.Side -eq 'cand' -and $_.Lines -gt 1000 }).Count)"
"candidate compile failures: $(@($rows | Where-Object { $_.Side -eq 'cand' -and $_.Lines -lt 0 }).Count)"
$missingArms = @(Get-ChildItem (Join-Path $Tree 'cand-out') -Filter *.rs -ErrorAction SilentlyContinue | Select-String -SimpleMatch 'compile_error!("backend_switch' -List)
"candidate crates with a missing backend arm (compile_error!, fails at cargo check): $($missingArms.Count)"
$missingArms | ForEach-Object { "  MISSING ARM: $($_.Filename)" }
