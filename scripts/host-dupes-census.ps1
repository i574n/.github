param([Parameter(Mandatory)] [string] $Src, [int] $Top = 25, [string] $Show)
$ErrorActionPreference = 'Stop'
$defs = foreach ($file in Get-ChildItem $Src -Recurse -Filter *.spi | Where-Object { $_.FullName -notmatch '\\target\\' }) {
    $lines = [IO.File]::ReadAllLines($file.FullName)
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $m = [regex]::Match($lines[$i], '^:[=~] (\^ )?(?<name>[\w'']+)')
        if (-not $m.Success) { continue }
        $text = $lines[$i]
        $j = $i
        while ($j + 1 -lt $lines.Count -and $lines[$j + 1] -match '^\s+\S') { $j++; $text += ' ' + $lines[$j].Trim() }
        $at = $text.IndexOf("`$'")
        if ($at -lt 0) { continue }
        $body = ($text.Substring($at) -replace '\\ ', '' -replace '\s+', ' ' -replace '!\w+', '!x').Trim()
        if ($body.Length -lt 40) { continue }
        [pscustomobject]@{ Package = $file.Directory.Name; Name = $m.Groups['name'].Value; Body = $body; Where = "$($file.Directory.Name)/$($file.Name):$($i + 1)" }
    }
}
$groups = @($defs | Group-Object Body | Where-Object { @($_.Group.Package | Sort-Object -Unique).Count -ge 2 } | Sort-Object { @($_.Group.Package | Sort-Object -Unique).Count } -Descending)
"host-macro definitions: $(@($defs).Count); bodies duplicated across packages: $($groups.Count); duplicate definitions: $(($groups | ForEach-Object { $_.Count } | Measure-Object -Sum).Sum)"
if ($Show) {
    $groups | Where-Object { $_.Name.Contains($Show) } | ForEach-Object { $_.Group | ForEach-Object { "  $($_.Where) $($_.Name)" } }
    return
}
$groups | Select-Object -First $Top | ForEach-Object {
    "{0,2} pkgs  {1,-40} {2}" -f @($_.Group.Package | Sort-Object -Unique).Count, (($_.Group.Name | Sort-Object -Unique) -join ','), $_.Name.Substring(0, [Math]::Min(110, $_.Name.Length))
}
