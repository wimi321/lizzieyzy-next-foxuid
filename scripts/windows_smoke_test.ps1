param(
    [Parameter(Mandatory = $true)]
    [string]$AppExe,

    [string]$ConfigDir = "",

    [string]$RequiredLogDir = "gtp_logs",

    [int]$HealthyProcessSeconds = 15,

    [int]$WaitSeconds = 60,

    [switch]$PreserveConfig,

    [switch]$RequireConfig
)

$ErrorActionPreference = "Stop"

function Get-SmokeConfigDirCandidates {
    param(
        [string]$PreferredConfigDir
    )

    $candidates = New-Object System.Collections.Generic.List[string]
    if ($PreferredConfigDir -and $PreferredConfigDir.Trim()) {
        $candidates.Add($PreferredConfigDir)
    }
    if ($env:PUBLIC) {
        $candidates.Add((Join-Path $env:PUBLIC "Documents\LizzieYzyNext"))
        $candidates.Add((Join-Path $env:PUBLIC "LizzieYzyNext"))
    }
    if ($env:PROGRAMDATA) {
        $candidates.Add((Join-Path $env:PROGRAMDATA "LizzieYzyNext"))
    }
    if ($env:USERPROFILE) {
        $candidates.Add((Join-Path $env:USERPROFILE ".lizzieyzy-next"))
        $candidates.Add((Join-Path $env:USERPROFILE ".lizzieyzy-next-foxuid"))
    }

    return $candidates | Where-Object { $_ -and $_.Trim() } | Select-Object -Unique
}

function Resolve-ExistingSmokeConfigDir {
    param(
        [string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        if ((Test-Path -LiteralPath (Join-Path $candidate "config.txt")) `
            -or (Test-Path -LiteralPath (Join-Path $candidate "persist")) `
            -or (Test-Path -LiteralPath (Join-Path $candidate "runtime")) `
            -or (Test-Path -LiteralPath (Join-Path $candidate "save"))) {
            return $candidate
        }
    }

    if ($Candidates.Count -gt 0) {
        return $Candidates[0]
    }
    return ""
}

function Assert-NoBundledEngineStartupFailure {
    param(
        [string]$RuntimeLogDir
    )

    if (-not $RuntimeLogDir -or -not (Test-Path -LiteralPath $RuntimeLogDir)) {
        return
    }

    $patterns = @(
        'Error creating directory',
        'Could not create file',
        'Uncaught exception'
    )

    $logFiles = Get-ChildItem -LiteralPath $RuntimeLogDir -Filter *.log -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
    foreach ($logFile in $logFiles) {
        $content = Get-Content -LiteralPath $logFile.FullName -Raw -ErrorAction SilentlyContinue
        if (-not $content) {
            continue
        }
        foreach ($pattern in $patterns) {
            if ($content -match $pattern) {
                throw "Bundled KataGo startup log contains '$pattern': $($logFile.FullName)"
            }
        }
    }
}

if (-not (Test-Path -LiteralPath $AppExe)) {
    throw "App executable not found: $AppExe"
}

$appDir = Split-Path -Parent $AppExe
$consoleLogs = Join-Path $appDir "LastConsoleLogs_*.txt"
$errorLogs = Join-Path $appDir "LastErrorLogs_*.txt"
$configDirCandidates = @(Get-SmokeConfigDirCandidates -PreferredConfigDir $ConfigDir)

if ($configDirCandidates.Count -eq 0) {
    throw "No candidate config directory could be resolved for the Windows smoke test."
}

if (-not $PreserveConfig) {
    foreach ($candidate in $configDirCandidates) {
        if (Test-Path -LiteralPath $candidate) {
            Remove-Item -LiteralPath $candidate -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

Get-ChildItem -Path $consoleLogs -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
Get-ChildItem -Path $errorLogs -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host "Launching $AppExe"
$process = Start-Process -FilePath $AppExe -WorkingDirectory $appDir -PassThru

$deadline = (Get-Date).AddSeconds($WaitSeconds)
$passed = $false
$healthyDeadline = (Get-Date).AddSeconds($HealthyProcessSeconds)
$activeConfigDir = Resolve-ExistingSmokeConfigDir -Candidates $configDirCandidates

try {
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2

        if ($process.HasExited) {
            $exitCode = $process.ExitCode
            throw "Application exited early with code $exitCode"
        }

        $activeConfigDir = Resolve-ExistingSmokeConfigDir -Candidates $configDirCandidates
        $runtimeDir = Join-Path $activeConfigDir "runtime"
        $requiredRuntimeLogDir = Join-Path $runtimeDir $RequiredLogDir
        $configFile = Join-Path $activeConfigDir "config.txt"
        $persistFile = Join-Path $activeConfigDir "persist"
        $hasConfig = (Test-Path -LiteralPath $configFile) -and (Test-Path -LiteralPath $persistFile)
        $hasRuntimeLogs = Test-Path -LiteralPath $requiredRuntimeLogDir
        $hasRuntimeState = $hasRuntimeLogs -or (Test-Path -LiteralPath $runtimeDir)

        if ($hasConfig -and $hasRuntimeState) {
            Write-Host "Resolved config dir: $activeConfigDir"
            if ($hasRuntimeLogs) {
                Start-Sleep -Seconds 2
                Assert-NoBundledEngineStartupFailure -RuntimeLogDir $requiredRuntimeLogDir
            }
            if ($hasRuntimeLogs) {
                Write-Host "Smoke test passed. Config files and bundled KataGo runtime logs were created."
            }
            else {
                Write-Host "Smoke test passed. Config files were created and the runtime directory is writable."
            }
            $passed = $true
            return
        }

        if ($hasConfig) {
            Write-Host "Config files detected. Waiting briefly for runtime state..."
        }

        if ((Get-Date) -ge $healthyDeadline -and (-not $RequireConfig)) {
            Write-Host "Resolved config dir candidate: $activeConfigDir"
            Write-Host "Smoke test passed. App process stayed alive long enough for CI."
            $passed = $true
            return
        }
    }

    throw "Timed out waiting for config files, runtime state, or a healthy running process."
}
finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }

    $activeConfigDir = Resolve-ExistingSmokeConfigDir -Candidates $configDirCandidates
    Write-Host "--- Config dir ---"
    Write-Host $activeConfigDir
    if ($activeConfigDir -and (Test-Path -LiteralPath $activeConfigDir)) {
        Get-ChildItem -LiteralPath $activeConfigDir -Recurse -Force | Select-Object FullName, Length
    }

    Write-Host "--- Console logs ---"
    Get-ChildItem -Path $consoleLogs -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "### $($_.FullName)"
        Get-Content -LiteralPath $_.FullName -Tail 120 -ErrorAction SilentlyContinue
    }

    Write-Host "--- Error logs ---"
        Get-ChildItem -Path $errorLogs -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "### $($_.FullName)"
            Get-Content -LiteralPath $_.FullName -Tail 120 -ErrorAction SilentlyContinue
        }
}

if (-not $passed) {
    throw "Windows smoke test did not reach the success condition."
}
