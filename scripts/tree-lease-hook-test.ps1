param([string] $Root = (Join-Path $env:TEMP 'kit-lease-probe-root'))
$root = $Root
$hook = 'C:\Users\i574n\.claude\hooks\kit-tree-lease.ps1'
[void][IO.Directory]::CreateDirectory($root)
$leaseFile = Join-Path $env:LOCALAPPDATA ('kit\tree-leases\probe-' + $PID + '.lease')
Set-Content $leaseFile "root=$root
pid=$PID
holder=tree-lease-hook-test
since=$(Get-Date -Format HH:mm)"
$cases = @(
    @('allow', "& $root\kit.exe agile note C:\home\git\i574n.github\docs\backlog X ""y"" --as tibia 2>&1"),
    @('allow', "$root/kit.exe agile set C:\home\git\i574n.github\docs\backlog X 50 Active 2>`$null"),
    @('allow', "Get-Content $root\src\kit_jobs\host.spi 2>`$null | Select-Object -First 3"),
    @('allow', "& $root\kit.exe job list *> `$null"),
    @('allow', "`$n=@'`nnote naming $root and Move-Item and copy`n'@; kit.exe agile note C:\x ID `$n --as kit"),
    @('block', "Set-Content -Path $root\src\a.spi -Value @'`nx`n'@"),    @('block', "Set-Content $root\src\a.spi 'x'"),
    @('block', "echo x > $root\src\a.spi"),
    @('block', "Copy-Item C:\temp\a.spi $root\src\a.spi"),
    @('block', "& $root\kit.exe agile list C:\x 2>&1; Remove-Item $root\src\b.spi"),
    @('allow', "Get-Content $root\scripts\lane-copy-swap.do"),
    @('allow', "Get-Content $root\docs\rm-notes.md, $root\src\kit_do_move\move.spi"),
    @('block', "cp C:\temp\a.spi $root\src\a.spi"),
    @('block', "copy C:\temp\a.spi $root\src\a.spi"),
    @('block', "rm $root\src\b.spi")
)
"lease: $leaseFile"
foreach ($case in $cases) {
    $payload = @{ tool_name = 'PowerShell'; tool_input = @{ command = $case[1] } } | ConvertTo-Json -Compress
    $null = $payload | pwsh -NoProfile -NonInteractive -File $hook 2>&1
    $got = if ($LASTEXITCODE -eq 2) { 'block' } else { 'allow' }
    "{0}  want={1} got={2}  {3}" -f $(if ($got -eq $case[0]) { 'OK ' } else { 'BAD' }), $case[0], $got, $case[1].Substring(0, [Math]::Min(80, $case[1].Length))
}

Remove-Item $leaseFile -Force
