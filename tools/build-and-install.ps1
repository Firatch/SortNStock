# Builds SortNStock and installs the resulting jar into a test instance's mods folder.
#
# This is the single documented way to get a build in front of you. Running it means the mods folder
# always holds the newest jar, so a test can never accidentally run an older build.
#
# Usage:
#     pwsh -File tools\build-and-install.ps1 -ModsDir 'C:\path\to\.minecraft\mods'
#
# or set the SORTNSTOCK_MODS_DIR environment variable once and omit the parameter.

param(
    [string]$ModsDir = $env:SORTNSTOCK_MODS_DIR
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ModsDir))
{
    throw "No mods folder given. Pass -ModsDir '<...>\.minecraft\mods' or set SORTNSTOCK_MODS_DIR."
}

$root = Split-Path -Parent $PSScriptRoot
$builtJar = Join-Path $root 'build\libs\sortnstock-26.1.2-0.1.0.jar'
$targetJar = Join-Path $ModsDir 'sortnstock-26.1.2-0.1.0.jar'

# --- build -------------------------------------------------------------------------------------
$env:GRADLE_OPTS = '-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT'

Push-Location $root
try
{
    $output = & (Join-Path $root 'gradlew.bat') clean build --no-daemon --console=plain 2>&1
    $buildOk = $LASTEXITCODE -eq 0
}
finally
{
    Pop-Location
}

$passed = ($output | Select-String 'PASSED').Count
$failed = ($output | Select-String 'FAILED').Count

if ($buildOk -eq $false)
{
    $output | Select-String -Pattern 'error:|错误|FAILED' | ForEach-Object { '  ' + $_.Line.Trim() }
    throw "Build FAILED; the jar was NOT installed."
}

# A passing build with failing tests must never be installed.
if ($failed -gt 0)
{
    throw "Tests reported $failed failure(s); the jar was NOT installed."
}

if ((Test-Path $builtJar) -eq $false)
{
    throw "Build succeeded but $builtJar is missing."
}

# --- verify the destination before touching it --------------------------------------------------
if ((Test-Path $ModsDir) -eq $false)
{
    throw "Mods folder not found: $ModsDir"
}

if ((Test-Path $targetJar) -eq $false)
{
    throw "No SortNStock jar at $targetJar - is $ModsDir really the right mods folder?"
}

$resolved = (Resolve-Path $targetJar).Path

if ((Split-Path -Leaf $resolved) -ne 'sortnstock-26.1.2-0.1.0.jar')
{
    throw "Refusing to install: resolved path '$resolved' is not a SortNStock jar."
}

# --- detect a running game BEFORE reporting success --------------------------------------------
# A jar replaced while Minecraft is running is never loaded, which has already caused a whole round
# of testing to be done against a stale build.
$gameRunning = $false

try
{
    $javaProcs = Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction Stop
    $gameRunning = @($javaProcs | Where-Object { $_.CommandLine -match 'Minecraft|fabric' }).Count -gt 0
}
catch
{
    # If process inspection is unavailable, fall back to a log-file lock check.
    $log = Join-Path (Split-Path -Parent $ModsDir) 'logs\latest.log'

    if (Test-Path $log)
    {
        try
        {
            $stream = [System.IO.File]::Open($log, 'Open', 'ReadWrite', 'None')
            $stream.Close()
        }
        catch
        {
            $gameRunning = $true
        }
    }
}

# --- install -----------------------------------------------------------------------------------
Copy-Item $builtJar $resolved -Force

$hash = (Get-FileHash $resolved -Algorithm SHA256).Hash.Substring(0, 16)
$info = Get-Item $resolved

Write-Output ''
Write-Output "  tests    : $passed passed, $failed failed"
Write-Output "  installed: $hash  $($info.Length) bytes  $($info.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))"
Write-Output "  target   : $resolved"

if ($gameRunning)
{
    Write-Output ''
    Write-Output '  !! Minecraft is RUNNING. A replaced jar is NOT loaded by a running game.'
    Write-Output '  !! Quit the game completely and start it again, or the old build will be tested.'
}
else
{
    Write-Output ''
    Write-Output '  Game is not running - the next launch will use this build.'
}
