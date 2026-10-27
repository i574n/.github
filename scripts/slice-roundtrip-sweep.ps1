param([string[]] $Slices)
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:TEMP "slice-roundtrip-$(Get-Date -Format HHmmss)"
$base = Join-Path $root 'base\lib'
robocopy C:\home\git\spiral\lib $base *.spi *.spir /S /XJD /XD target node_modules _build build .cache /R:2 /W:2 /NFL /NDL /NJH /NJS /NP | Out-Null
$files = @()
$report = foreach ($slice in $Slices) {
    $apply = Join-Path $env:TEMP "$slice\apply.ps1"
    if (-not (Test-Path $apply)) { [pscustomobject]@{ Slice = $slice; State = 'no apply.ps1'; Files = '' }; continue }
    $copy = Join-Path $root "$slice\lib"
    robocopy $base $copy /E /R:2 /W:2 /NFL /NDL /NJH /NJS /NP | Out-Null
    $lib = Join-Path $copy 'spiral'
    $out = @(try { & $apply -Lib $lib -Apply 2>&1 | ForEach-Object { "$_" } } catch { "refused: $($_.Exception.Message)" })
    $changed = @(Get-ChildItem $lib -Recurse -File -Include *.spi | Where-Object {
        $rel = $_.FullName.Substring($lib.Length + 1)
        $orig = Join-Path $base "spiral\$rel"
        -not (Test-Path $orig) -or [IO.File]::ReadAllText($orig) -cne [IO.File]::ReadAllText($_.FullName) })
    if (-not $changed) { [pscustomobject]@{ Slice = $slice; State = "applied nothing: $($out | Select-Object -Last 1)"; Files = '' }; continue }
    $files += $changed.FullName
    [pscustomobject]@{ Slice = $slice; State = 'applied'; Files = ($changed.Name -join ',') }
}
$exs = Join-Path $root 'diff.exs'
[IO.File]::WriteAllText($exs, @'
alias Spiral.Cells.Document
for spi_path <- String.split(System.get_env("ROUNDTRIP_FILES", ""), ";", trim: true) do
  spi = File.read!(spi_path)
  back = spi |> Document.parse_spi("") |> Document.to_livemd() |> Document.parse_livemd() |> Document.to_spi_tested()
  a = String.split(spi, "\n")
  b = String.split(back, "\n")
  first = Enum.find_index(Enum.zip(a, b), fn {x, y} -> x != y end)
  state = if spi == back, do: "ok", else: "differs at line #{(first || min(length(a), length(b))) + 1}: #{inspect(Enum.at(a, first || 0))}"
  IO.puts("ROUNDTRIP\t#{spi_path}\t#{state}")
end
'@)
$env:PATH = "$HOME\scoop\apps\erlang\current\bin;$HOME\scoop\apps\elixir\current\bin;$env:PATH"
$env:MIX_BUILD_PATH = Join-Path $env:LOCALAPPDATA 'spiral-bin\cells-builds\main-tree'
$env:ROUNDTRIP_FILES = (@($files) + @(Get-ChildItem (Join-Path $base 'spiral') -Filter 'near.spi' -Recurse | ForEach-Object FullName)) -join ';'
Push-Location C:\home\git\spiral\apps\cells
try { $trip = @(& mix.bat run --no-compile --no-start --no-deps-check $exs 2>&1 | Where-Object { "$_" -match '^ROUNDTRIP' } | ForEach-Object { "$_" }) } finally { Pop-Location }
$report | Format-Table -AutoSize | Out-String -Width 220
foreach ($t in $trip) { $p, $s = ($t -split "`t")[1, 2]; '{0,-70} {1}' -f $p.Substring($root.Length + 1), $s }
"control (base near.spi must be ok): $(@($trip | Where-Object { $_ -match 'base\\lib' -and $_ -match "`tok$" }).Count -eq 1)"
Remove-Item -LiteralPath $root -Recurse -Force
