param(
    [ValidateSet("x64")]
    [string] $Arch = "x64"
)

$ErrorActionPreference = "Stop"

$RootDir = Split-Path $PSScriptRoot -Parent
$ResourceArch = "x86_64"
$OutDir = Join-Path $RootDir "build/native-runtimes/windows-$ResourceArch"
$BuildOut = Join-Path $RootDir "build/libpag-build/windows-$ResourceArch"

Remove-Item -Recurse -Force $OutDir, $BuildOut -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $OutDir, $BuildOut | Out-Null

$VsWhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio/Installer/vswhere.exe"
if (Test-Path $VsWhere) {
    $VsInstallPath = & $VsWhere -latest -property installationPath
    if (-not [string]::IsNullOrWhiteSpace($VsInstallPath)) {
        $env:CMAKE_MSVC_PATH = Join-Path $VsInstallPath "VC"
    }
}

Push-Location (Join-Path $RootDir "reference/libpag")
try {
    npm install -g depsync --silent
    node build_pag `
        -p win `
        -a $Arch `
        -DPAG_USE_C=ON `
        -DPAG_BUILD_SHARED=ON `
        -DPAG_BUILD_TESTS=OFF `
        -DPAG_BUILD_FRAMEWORK=OFF `
        -DCMAKE_POLICY_DEFAULT_CMP0091=NEW `
        -DCMAKE_MSVC_RUNTIME_LIBRARY=MultiThreaded `
        -o $BuildOut
}
finally {
    Pop-Location
}

$Library = Get-ChildItem -Path $BuildOut -Recurse -File |
    Where-Object { $_.Name -eq "pag.dll" -or $_.Name -eq "libpag.dll" } |
    Select-Object -First 1

if ($null -eq $Library) {
    Write-Error "libpag DLL was not produced under $BuildOut"
}

Copy-Item $Library.FullName -Destination (Join-Path $OutDir "pag.dll") -Force
Write-Host "Staged native runtime:"
Get-ChildItem -Path $OutDir -File | ForEach-Object { $_.FullName }
