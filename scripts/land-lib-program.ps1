param(
    [string] $Program = '',
    [string] $Note = '',
    [string] $Backlog = 'C:\home\git\i574n.github\docs\backlog',
    [Parameter(Mandatory)] [string] $File,
    [Parameter(Mandatory)] [string] $Base,
    [Parameter(Mandatory)] [string] $Target,
    [string] $FollowProgram,
    [string] $FollowFile,
    [string] $FollowBase,
    [string[]] $ExemptWindow = @(),
    [string] $ExemptAncestry = ''
)
$ErrorActionPreference = 'Stop'
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
if (-not (Test-Path -LiteralPath $kit)) { $kit = Get-ChildItem "$env:LOCALAPPDATA\kit\supervisor" -Filter kit-*.exe | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -Expand FullName }
$lib = 'C:\home\git\spiral\lib\spiral'
$live = Join-Path $lib $File
function Get-Hash16([string] $Path) { (Get-FileHash -LiteralPath $Path).Hash.Substring(0, 16) }
if (!$Program -eq !$Note) { throw 'land-lib-program: pass exactly one of -Program <file> or -Note <ITEM>/<n>' }
$undoName = if ($Note) { $Note -replace '/', '-n' } else { [IO.Path]::GetFileNameWithoutExtension($Program) }
$undo = Join-Path $env:LOCALAPPDATA "kit\undo\$undoName"
if ($Note) {
    $id, $number = $Note -split '/', 2
    $marker = 'PROGRAM (kit do <lib> -):'
    $text = "$((& $kit agile show $Backlog $id --json | ConvertFrom-Json).notes[[int]$number - 1].text)"
    $at = $text.IndexOf($marker)
    if ($at -lt 0) { "land-lib-program / $Note has no '$marker' block"; exit 3 }
    New-Item -ItemType Directory -Force $undo | Out-Null
    $Program = Join-Path $undo 'program.kitdo'
    [IO.File]::WriteAllText($Program, $text.Substring($at + $marker.Length).TrimStart("`r", "`n"))
}
$have = Get-Hash16 $live
if ($have -eq $Target) { "land-lib-program / $File already at $Target" }
elseif ($have -ne $Base) { "land-lib-program / $File is $have, not the proven base ${Base}; re-anchor first"; exit 4 }
else {
    $guardArgs = @(if ($ExemptWindow) { '-ExemptWindow'; $ExemptWindow -join ',' }) + @(if ($ExemptAncestry) { '-ExemptAncestry'; $ExemptAncestry })
    New-Item -ItemType Directory -Force $undo | Out-Null
    Copy-Item -LiteralPath $live (Join-Path $undo "$File.$have") -Force
    $out = @(pwsh -NoProfile -File (Join-Path $PSScriptRoot 'lib-guarded-do.ps1') -Program $Program @guardArgs 2>&1 | ForEach-Object { "$_" })
    $code = $LASTEXITCODE
    $out | Select-Object -Last 6
    if ($code) { "land-lib-program / lib-guarded-do exit $code, nothing landed"; exit $code }
    $have = Get-Hash16 $live
    if ($have -ne $Target) { Copy-Item -LiteralPath (Join-Path $undo "$File.$Base") $live -Force; "land-lib-program / landed $have, expected ${Target}; restored $(Get-Hash16 $live)"; exit 5 }
    "land-lib-program / $File landed $Target (undo: $undo)"
}
if (!$FollowProgram) { return }
if ((Get-Hash16 $FollowFile) -ne $FollowBase) { "land-lib-program / $FollowFile is $(Get-Hash16 $FollowFile), not ${FollowBase}; follow-up left for review"; exit 6 }
Get-Content -Raw $FollowProgram | & $kit do (Split-Path $FollowFile) - 2>&1 | Select-Object -Last 2
"land-lib-program / $FollowFile now $(Get-Hash16 $FollowFile)"
