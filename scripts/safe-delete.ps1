function ConvertTo-ComparablePath([string] $Path) {
    [IO.Path]::TrimEndingDirectorySeparator([IO.Path]::GetFullPath($Path))
}

function Get-ForbiddenDeleteRoots {
    $folders = 'UserProfile', 'LocalApplicationData', 'ApplicationData', 'Windows', 'System', 'ProgramFiles', 'ProgramFilesX86', 'CommonApplicationData' | ForEach-Object { [Environment]::GetFolderPath($_) }
    $profile = [Environment]::GetFolderPath('UserProfile')
    $usersRoot = if ($profile) { Split-Path $profile -Parent }
    $profiles = if ($usersRoot -and (Test-Path -LiteralPath $usersRoot)) { [IO.Directory]::GetDirectories($usersRoot) }
    $profileData = foreach ($p in @($profiles)) { foreach ($sub in 'AppData', 'AppData\Local', 'AppData\Roaming', 'AppData\LocalLow', 'AppData\Local\Temp') { Join-Path $p $sub } }
    $variables = $env:USERPROFILE, $HOME, $env:TEMP, $env:TMP, $env:LOCALAPPDATA, $env:APPDATA, $env:SystemRoot, $env:ProgramData
    $drives = [IO.DriveInfo]::GetDrives() | ForEach-Object { $_.RootDirectory.FullName }
    @($folders + $usersRoot + $profiles + $profileData + $variables + [IO.Path]::GetTempPath() + $drives) | Where-Object { $_ } | ForEach-Object { ConvertTo-ComparablePath $_ } | Select-Object -Unique
}

function Test-PathContains([string] $Ancestor, [string] $Path) {
    $Path.Equals($Ancestor, [StringComparison]::OrdinalIgnoreCase) -or $Path.StartsWith($Ancestor.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-SafeDeleteRoot([string] $Path, [string] $Under) {
    if ([string]::IsNullOrWhiteSpace($Path) -or -not [IO.Path]::IsPathFullyQualified($Path)) { throw "refuse delete: '$Path' is empty or relative" }
    $full = ConvertTo-ComparablePath $Path
    foreach ($root in Get-ForbiddenDeleteRoots) {
        if (Test-PathContains $full $root) { throw "refuse delete: '$full' is or contains '$root' (a drive root, profile, temp root or system folder)" }
    }
    if ($Under) {
        $base = ConvertTo-ComparablePath $Under
        if ($full.Equals($base, [StringComparison]::OrdinalIgnoreCase) -or -not (Test-PathContains $base $full)) { throw "refuse delete: '$full' is not inside '$Under'" }
    }
    $full
}

function Remove-TreeSafely([string] $Path, [string] $Under) {
    $full = Assert-SafeDeleteRoot $Path $Under
    if (Test-Path -LiteralPath $full) { Remove-Item -LiteralPath $full -Recurse -Force }
}
