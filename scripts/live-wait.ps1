function Get-LiveBlock {
    $cli = 'C:\home\git\kit\kit.exe'
    @(& $cli window list 2>&1 | Where-Object { "$_" -match '\blive-gui\b' }) | Select-Object -First 1
}

function Wait-NoLiveBlock([string] $Label) {
    $since = Get-Date
    $lastSaid = [datetime]::MinValue
    while ($block = Get-LiveBlock) {
        if (((Get-Date) - $lastSaid).TotalMinutes -ge 5) {
            "$(Get-Date -Format 'HH:mm:ss') $Label waits for a live block to end ($([int]((Get-Date) - $since).TotalMinutes) min): $("$block".Trim())" | Write-Host
            $lastSaid = Get-Date
        }
        Start-Sleep -Seconds 20
    }
}
