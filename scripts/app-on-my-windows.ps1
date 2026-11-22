# Packages examples\an-app-of-ones-own for Windows with jebol-devtools, installs
# it, runs the installed and the portable app, reads back what Windows made of
# them, and writes it all to build\windows-app-report.txt, the file to paste
# back. Goal 2e.
#
# First it answers the question 2e left for this machine: does a registration
# under HKEY_CURRENT_USER\Software\Classes\AppUserModelId let an app no
# installer placed raise a toast? It asks you whether a toast appeared.
#
#   powershell -ExecutionPolicy Bypass -File scripts\app-on-my-windows.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\app-on-my-windows.ps1 -Unattended
#
# It installs what it needs with winget: a JDK 25 (Temurin) and the .NET SDK,
# then the WiX toolset jpackage uses, as a .NET tool. -Unattended is the CI's:
# the tools are already installed, Windows' notification history answers the
# toast question, the installer runs quietly, and any FAIL exits with 1.

param([switch]$Unattended)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
New-Item -ItemType Directory -Force build | Out-Null
$report = "build\windows-app-report.txt"
Set-Content $report "" -Encoding UTF8
$script:failures = 0
function Say($text) {
    Write-Output $text
    Add-Content -Path $report -Value $text -Encoding UTF8
}
function Fail($text) { $script:failures++; Say "FAIL  $text" }
function Check($what, [scriptblock]$test) {
    try {
        $answer = & $test
        if ($answer) { Say "ok    $what  ($answer)" } else { Fail $what }
    } catch {
        Fail "$what  ($($_.Exception.Message))"
    }
}
function OutputOf([scriptblock]$command) {
    $ErrorActionPreference = "Continue"
    & $command 2>&1 | ForEach-Object { "$_" }
}
function CommitBeingChecked {
    if ($env:JEBOL_COMMIT) { return $env:JEBOL_COMMIT }
    if (Get-Command git -ErrorAction SilentlyContinue) { return (git rev-parse --short HEAD) }
    "unknown"
}

$identity = "org.jebol.example.notes"
$example = "examples\an-app-of-ones-own"
Say "Windows $([Environment]::OSVersion.Version), $(Get-Date)"
Say "commit $(CommitBeingChecked)"

Say "--- a toast from an app registered under HKEY_CURRENT_USER alone"
$probe = "org.jebol.probe.toast"
$key = "HKCU:\Software\Classes\AppUserModelId\$probe"
New-Item -Path $key -Force | Out-Null
New-ItemProperty -Path $key -Name DisplayName -Value "JEBOL probe" -Force | Out-Null
[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
$xml = New-Object Windows.Data.Xml.Dom.XmlDocument
$xml.LoadXml("<toast><visual><binding template='ToastGeneric'><text>JEBOL probe</text><text>Registered under HKEY_CURRENT_USER only</text></binding></visual></toast>")
try {
    [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier($probe).Show([Windows.UI.Notifications.ToastNotification]::new($xml))
    if ($Unattended) {
        Start-Sleep 3
        $history = [Windows.UI.Notifications.ToastNotificationManager]::History.GetHistory($probe)
        Say "toast shown: $(if ($history.Count -gt 0) { 'y, in the notification history' } else { 'n, not in the notification history' })"
        [Windows.UI.Notifications.ToastNotificationManager]::History.Clear($probe)
    } else {
        $seen = Read-Host "Did a toast titled 'JEBOL probe' appear? (y/n)"
        Say "toast shown: $seen"
    }
} catch {
    Fail "showing the toast: $($_.Exception.Message)"
}
Remove-Item -Path $key -Recurse -Force

Say "--- the tools"
if ($Unattended) {
    $extensions = OutputOf { wix extension list -g }
    if (-not ($extensions -match 'WixToolset.Util.wixext') -or -not ($extensions -match 'WixToolset.UI.wixext')) {
        wix extension add -g WixToolset.Util.wixext/5.0.2 WixToolset.UI.wixext/5.0.2
    }
} elseif (-not (Get-Command java -ErrorAction SilentlyContinue) -or -not ((OutputOf { java -version }) -match '"2[5-9]')) {
    winget install --id EclipseAdoptium.Temurin.25.JDK -e --accept-source-agreements --accept-package-agreements
}
if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    winget install --id Microsoft.DotNet.SDK.8 -e --accept-source-agreements --accept-package-agreements
}
if (-not (Get-Command wix -ErrorAction SilentlyContinue)) {
    dotnet tool install --global wix --version 5.0.2
    wix extension add -g WixToolset.Util.wixext/5.0.2 WixToolset.UI.wixext/5.0.2
}
Say (OutputOf { java -version } | Select-Object -First 1)
Say "wix $(wix --version)"

Say "--- packaging"
.\gradlew.bat :devtools:publishDevtools --console=plain -q
Push-Location $example
OutputOf { ..\..\gradlew.bat packageWindows --console=plain } | ForEach-Object { Write-Output $_; Add-Content -Path "..\..\$report" -Value $_ -Encoding UTF8 }
Pop-Location
$packages = "$example\build\jebol\windows\package"
$installer = Get-ChildItem $packages -Filter *.exe | Select-Object -First 1
Check "an installer was made" { $installer.Name }
Check "a portable app image was made" { (Test-Path "$packages\Notes\Notes.exe") }
Add-Type -AssemblyName System.Drawing
Check ".NET reads the .ico jebol-devtools wrote" {
    $icon = New-Object System.Drawing.Icon("$example\build\jebol\windows\resources\Notes.ico", 256, 256)
    "$($icon.Width)x$($icon.Height)"
}

if ($Unattended) {
    Say "--- installing quietly"
    Start-Process -Wait $installer.FullName -ArgumentList "/quiet"
} else {
    Say "--- installing; click through the installer if it asks"
    Start-Process -Wait $installer.FullName
}
$shortcut = Get-ChildItem "$env:APPDATA\Microsoft\Windows\Start Menu\Programs" -Recurse -Filter "Notes.lnk" | Select-Object -First 1
Check "a Start-menu shortcut was made" { $shortcut.FullName }
Check "the shortcut carries the AppUserModelID" {
    $shell = New-Object -ComObject Shell.Application
    $item = $shell.NameSpace($shortcut.DirectoryName).ParseName($shortcut.Name)
    $id = $item.ExtendedProperty("System.AppUserModel.ID")
    if ($id -eq $identity) { $id } else { $null }
}

$store = "$env:APPDATA\$identity"
Remove-Item $store -Recurse -Force -ErrorAction SilentlyContinue
$installed = Get-ChildItem "$env:LOCALAPPDATA" -Recurse -Filter "Notes.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
Check "the installed app is there" { $installed.FullName }
$running = Start-Process $installed.FullName -PassThru
Start-Sleep 20
Check "the installed app is running" { -not $running.HasExited }
Check "it keeps a store under its identity" { Test-Path $store }
Add-Type -AssemblyName System.Windows.Forms
$screen = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
$shot = New-Object System.Drawing.Bitmap $screen.Width, $screen.Height
[System.Drawing.Graphics]::FromImage($shot).CopyFromScreen($screen.Location, [System.Drawing.Point]::Empty, $screen.Size)
$shot.Save("build\windows-app.png")
Say "a screenshot is in build\windows-app.png"
Stop-Process -Id $running.Id -Force -ErrorAction SilentlyContinue

$portable = Start-Process "$packages\Notes\Notes.exe" -PassThru
Start-Sleep 20
Check "the portable app runs from its folder" { -not $portable.HasExited }
Stop-Process -Id $portable.Id -Force -ErrorAction SilentlyContinue

Say "--- done; paste build\windows-app-report.txt back, and attach build\windows-app.png"
if ($Unattended -and $script:failures -gt 0) { exit 1 }
