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
function ProcessShowingWindowFor($launcher) {
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$($launcher.Id)"
    $ids = @($launcher.Id) + @($children | ForEach-Object { [int]$_.ProcessId })
    Get-Process -Id $ids -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne [IntPtr]::Zero } |
        Select-Object -First 1
}
function MeanColourDistance($worn, $expected) {
    $total = 0
    $counted = 0
    for ($across = 0; $across -lt $expected.Width; $across++) {
        for ($down = 0; $down -lt $expected.Height; $down++) {
            $b = $expected.GetPixel($across, $down)
            if ($b.A -ne 255) { continue }
            $a = $worn.GetPixel($across, $down)
            $total += [Math]::Abs($a.R - $b.R) + [Math]::Abs($a.G - $b.G) + [Math]::Abs($a.B - $b.B)
            $counted++
        }
    }
    [int]($total / $counted)
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
    Say "--- removing an earlier install, so the one checked is the one just made"
    $installerApi = New-Object -ComObject WindowsInstaller.Installer
    foreach ($product in $installerApi.ProductsEx("", "", 7)) {
        if ($product.InstallProperty("ProductName") -eq "Notes") {
            $code = $product.GetType().InvokeMember("ProductCode", "GetProperty", $null, $product, $null)
            $removing = Start-Process -Wait -PassThru msiexec.exe -ArgumentList "/x $code /quiet"
            Say "removed $code, msiexec answered $($removing.ExitCode)"
        }
    }
    Say "--- installing quietly"
    $installing = Start-Process -Wait -PassThru $installer.FullName -ArgumentList "/quiet"
    Say "the installer answered $($installing.ExitCode)"
    Check "the installer finished" { if ($installing.ExitCode -eq 0) { "exit code 0" } else { $null } }
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
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class JebolWindowCheck {
    [DllImport("user32.dll")] static extern IntPtr SendMessage(IntPtr window, uint message, IntPtr wide, IntPtr wordy);
    [DllImport("user32.dll")] static extern IntPtr GetClassLongPtr(IntPtr window, int index);
    [DllImport("shell32.dll")] static extern int SHGetPropertyStoreForWindow(IntPtr window, ref Guid iid, [MarshalAs(UnmanagedType.Interface)] out IPropertyStore store);
    [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPropertyStore {
        int GetCount(out uint count);
        int GetAt(uint index, out PropertyKey key);
        int GetValue(ref PropertyKey key, out PropVariant value);
        int SetValue(ref PropertyKey key, ref PropVariant value);
        int Commit();
    }
    [StructLayout(LayoutKind.Sequential, Pack = 4)] struct PropertyKey { public Guid FormatId; public uint PropertyId; }
    [StructLayout(LayoutKind.Sequential)] struct PropVariant { public ushort Kind; public ushort Spare1, Spare2, Spare3; public IntPtr Value; public IntPtr More; }
    public static string AppUserModelId(IntPtr window) {
        Guid iid = new Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99");
        IPropertyStore store;
        if (SHGetPropertyStoreForWindow(window, ref iid, out store) != 0) { return null; }
        PropertyKey key = new PropertyKey { FormatId = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3"), PropertyId = 5 };
        PropVariant value;
        if (store.GetValue(ref key, out value) != 0 || value.Kind != 31) { return null; }
        return Marshal.PtrToStringUni(value.Value);
    }
    public static System.Drawing.Bitmap Icon(IntPtr window) {
        IntPtr icon = SendMessage(window, 0x7F, new IntPtr(1), IntPtr.Zero);
        if (icon == IntPtr.Zero) { icon = GetClassLongPtr(window, -14); }
        return icon == IntPtr.Zero ? null : System.Drawing.Icon.FromHandle(icon).ToBitmap();
    }
}
"@
$shown = ProcessShowingWindowFor $running
Check "the running app shows a window" { if ($shown) { $shown.MainWindowTitle } }
Check "the running app's window carries its AppUserModelID" {
    if (-not $shown) { throw "there is no window to read" }
    $declared = [JebolWindowCheck]::AppUserModelId($shown.MainWindowHandle)
    if ($declared -ne $identity) { throw "the window carries '$declared'" }
    $declared
}
Check "the running app's window wears the app's icon, not JEBOL's" {
    if (-not $shown) { throw "there is no window to read" }
    $worn = [JebolWindowCheck]::Icon($shown.MainWindowHandle)
    if ($worn -eq $null) { throw "the window has no icon" }
    $source = [System.Drawing.Image]::FromFile((Resolve-Path "$example\icon.png").Path)
    $expected = New-Object System.Drawing.Bitmap -ArgumentList $source, $worn.Width, $worn.Height
    $distance = MeanColourDistance $worn $expected
    if ($distance -ge 100) { throw "its colours are $distance from the app's icon.png" }
    "$($worn.Width)x$($worn.Height), colours $distance from the app's icon.png"
}
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
