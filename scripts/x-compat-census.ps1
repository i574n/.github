param([string[]] $Repos = @(), [string] $PostabilityProfile = 'C:\home\git\x\postability\x.json')
$ErrorActionPreference = 'Stop'
$env:SPIRAL_POSTABILITY_PROFILE = $PostabilityProfile
$dll = Join-Path $env:LOCALAPPDATA 'spiral-bin\bin\single-flight\SpiralCompiler\Release\net11.0\SpiralCompiler.dll'
$all = [ordered]@{
    'spiral lib' = @('C:\home\git\spiral', 'lib/spiral'); tibia = @('C:\home\git\tibia', 'src apps'); x = @('C:\home\git\x', '.')
    kit = @('C:\home\git\kit', 'src'); fiscal = @('C:\home\git\fiscal', '.'); dice = @('C:\home\git\dice', '.')
    alphabet = @('C:\home\git\alphabet', '.'); polyglot = @('C:\home\git\polyglot', '.')
}
$Repos = @($Repos | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
foreach ($name in $all.Keys) {
    if ($Repos -and $name -notin $Repos) { continue }
    $root, $paths = $all[$name]
    if (-not (Test-Path $root)) { "$name / missing"; continue }
    Push-Location $root
    try {
        $files = @(git ls-files -- ($paths -split ' ') | Where-Object { $_ -like '*.spi' })
        if (-not $files.Count) { "$name / 0 tracked .spi"; continue }
        $over = 0; $mention = 0; $byFile = @{}
        for ($i = 0; $i -lt $files.Count; $i += 200) {
            $chunk = $files[$i..([Math]::Min($i + 199, $files.Count - 1))]
            foreach ($line in @(& dotnet $dll --x-compat @chunk 2>&1 | ForEach-Object { "$_" })) {
                if ($line -match '^(?<f>[^:]+):\d+:\d+: overweight') { $over++; $byFile[$Matches.f] = 1 + [int]$byFile[$Matches.f] }
                elseif ($line -match '^[^:]+:\d+:\d+: (mention|hashtag|cashtag)') { $mention++ }
            }
        }
        $top = ($byFile.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 5 | ForEach-Object { "$($_.Key) $($_.Value)" }) -join '; '
        "{0} / files {1} / overweight {2} in {3} files / mentions {4} / top: {5}" -f $name, $files.Count, $over, $byFile.Count, $mention, $top
    } finally { Pop-Location }
}
