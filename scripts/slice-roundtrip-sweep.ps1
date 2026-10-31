param([string[]] $Slices = @(), [string[]] $Files = @(), [string] $Drafts = 'C:\home\data\lib-drafts')
$ErrorActionPreference = 'Stop'
$root = Join-Path $env:TEMP "slice-roundtrip-$(Get-Date -Format HHmmss)"
$base = Join-Path $root 'base\lib'
robocopy C:\home\git\spiral\lib $base *.spi *.spir /S /XJD /XD target node_modules _build build .cache /R:2 /W:2 /NFL /NDL /NJH /NJS /NP | Out-Null
$targets = @($Files | ForEach-Object { $_ -split ',' } | Where-Object { $_ } | ForEach-Object { (Resolve-Path $_).Path })
$report = foreach ($slice in $Slices) {
    $apply = Join-Path $Drafts "$slice\apply.ps1"
    if (-not (Test-Path $apply)) { [pscustomobject]@{ Slice = $slice; State = "no apply.ps1 in $Drafts"; Files = '' }; continue }
    $copy = Join-Path $root "$slice\lib"
    robocopy $base $copy /E /R:2 /W:2 /NFL /NDL /NJH /NJS /NP | Out-Null
    $lib = Join-Path $copy 'spiral'
    $out = @(try { & $apply -Lib $lib -Apply 2>&1 | ForEach-Object { "$_" } } catch { "refused: $($_.Exception.Message)" })
    $changed = @(Get-ChildItem $lib -Recurse -File -Include *.spi, *.spir | Where-Object {
        $rel = $_.FullName.Substring($lib.Length + 1)
        $orig = Join-Path $base "spiral\$rel"
        -not (Test-Path $orig) -or [IO.File]::ReadAllText($orig) -cne [IO.File]::ReadAllText($_.FullName) })
    if (-not $changed) { [pscustomobject]@{ Slice = $slice; State = "applied nothing: $($out | Select-Object -Last 1)"; Files = '' }; continue }
    $targets += $changed.FullName -replace '_real\.spir$', '.spi' | Sort-Object -Unique
    [pscustomobject]@{ Slice = $slice; State = 'applied'; Files = ($changed.Name -join ',') }
}
$exs = Join-Path $root 'diff.exs'
[IO.File]::WriteAllText($exs, @'
alias Spiral.Cells.Document
differs = fn x, y ->
  a = String.split(x, "\n")
  b = String.split(y, "\n")
  first = Enum.find_index(Enum.zip(a, b), fn {p, q} -> p != q end)
  "differs at line #{(first || min(length(a), length(b))) + 1}: #{inspect(Enum.at(a, first || 0))}"
end
for spi_path <- String.split(System.get_env("ROUNDTRIP_FILES", ""), ";", trim: true) do
  spi = File.read!(spi_path)
  spir_path = String.replace_suffix(spi_path, ".spi", "_real.spir")
  spir = if File.exists?(spir_path), do: File.read!(spir_path), else: ""
  back = spi |> Document.parse_spi(spir) |> Document.to_livemd() |> Document.parse_livemd()
  {spi1, spir1} = {Document.to_spi_tested(back), Document.to_spir_tested(back)}
  state =
    cond do
      spi1 != spi -> "spi " <> differs.(spi, spi1)
      spir != "" and spir1 != spir -> "spir " <> differs.(spir, spir1)
      true -> "ok"
    end
  IO.puts("ROUNDTRIP\t#{spi_path}\t#{state}")
end
'@)
$env:PATH = "$HOME\scoop\apps\erlang\current\bin;$HOME\scoop\apps\elixir\current\bin;$env:PATH"
$env:MIX_BUILD_PATH = Join-Path $env:LOCALAPPDATA 'spiral-bin\mix-builds\roundtrip-sweep'
if (-not (Test-Path "$env:MIX_BUILD_PATH\lib\spiral_cells\ebin")) {
    $seed = Get-ChildItem (Split-Path $env:MIX_BUILD_PATH) -Directory | Where-Object { Test-Path "$($_.FullName)\lib\spiral_cells\ebin\Elixir.Spiral.Cells.Document.beam" } |
        Sort-Object LastWriteTime | Select-Object -Last 1
    if (-not $seed) { throw "slice-roundtrip-sweep: no mix build with Spiral.Cells.Document under $(Split-Path $env:MIX_BUILD_PATH) to seed from" }
    robocopy $seed.FullName $env:MIX_BUILD_PATH /E /R:2 /W:2 /NFL /NDL /NJH /NJS /NP | Out-Null
}
$env:ROUNDTRIP_FILES = (@($targets) + @(Get-ChildItem (Join-Path $base 'spiral') -Filter 'near.spi' -Recurse | ForEach-Object FullName)) -join ';'
Push-Location C:\home\git\spiral\apps\cells
try { $mixOut = @(& mix.bat run --no-start --no-deps-check $exs 2>&1 | ForEach-Object { "$_" }) } finally { Pop-Location }
$trip = @($mixOut | Where-Object { $_ -match '^ROUNDTRIP' })
if (-not $trip) { $mixOut | Select-Object -Last 15 | ForEach-Object { "mix / $_" } }
if ($report) { $report | Format-Table -AutoSize | Out-String -Width 220 }
foreach ($t in $trip) { $p, $s = ($t -split "`t")[1, 2]; '{0,-70} {1}' -f ($p.StartsWith($root) ? $p.Substring($root.Length + 1) : $p), $s }
"control (base near.spi must be ok): $(@($trip | Where-Object { $_ -match 'base\\lib' -and $_ -match "`tok$" }).Count -eq 1)"
Remove-Item -LiteralPath $root -Recurse -Force
