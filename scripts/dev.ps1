<#
.SYNOPSIS
    OpenGeoBot Unified Development Script (PowerShell 7)
.DESCRIPTION
    Function: M0 engineering scaffold — unified dev entry point
    Time: 2026-07-03
    Author: AxeXie

    Usage:
      pwsh ./scripts/dev.ps1 doctor|bootstrap|infra-up|migrate|dev|sim-up|test|e2e|down
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Command = ''
)

$ErrorActionPreference = 'Stop'

# -----------------------------------------------------------------------------
# Resolve project root (this file lives in <root>/scripts/)
# -----------------------------------------------------------------------------
$ScriptDir = $PSScriptRoot
$RootDir = (Resolve-Path (Join-Path $ScriptDir '..')).Path
Set-Location $RootDir

$ComposeFile = Join-Path $RootDir 'deploy/compose/compose.yml'
$EnvFile = Join-Path $RootDir '.env'
$EnvExample = Join-Path $RootDir '.env.example'
$WebConsoleDir = Join-Path $RootDir 'apps/web-console'
$AgentRuntimeDir = Join-Path $RootDir 'services/agent-runtime'
$PidDir = Join-Path $RootDir '.dev-pids'

# Track background process objects for the `dev` command
$script:DevProcesses = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()

# -----------------------------------------------------------------------------
# Helpers
# -----------------------------------------------------------------------------
function Write-Ok($msg)   { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Fail($msg) { Write-Host "[X]  $msg" -ForegroundColor Red }
function Write-Info($msg) { Write-Host "[i]  $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[!]  $msg" -ForegroundColor Yellow }

function Load-Env {
    if (Test-Path $EnvFile) {
        Get-Content $EnvFile | ForEach-Object {
            $line = $_.Trim()
            if ($line -and -not $line.StartsWith('#')) {
                $idx = $line.IndexOf('=')
                if ($idx -gt 0) {
                    $key = $line.Substring(0, $idx).Trim()
                    $val = $line.Substring($idx + 1).Trim()
                    # Strip surrounding quotes
                    if (($val.StartsWith('"') -and $val.EndsWith('"')) -or
                        ($val.StartsWith("'") -and $val.EndsWith("'"))) {
                        $val = $val.Substring(1, $val.Length - 2)
                    }
                    Set-Item -Path "Env:$key" -Value $val
                }
            }
        }
    }
}

function Test-PortInUse {
    param([int]$Port)
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        $listener.Stop()
        return $false
    } catch {
        return $true
    }
}

function Check-Port {
    param([int]$Port, [string]$Label)
    if (Test-PortInUse -Port $Port) {
        Write-Warn "Port $Port ($Label) is already in use"
    } else {
        Write-Ok "Port $Port ($Label) is free"
    }
}

function Wait-ForHealth {
    param(
        [string]$Service,
        [int]$MaxWait = 120
    )
    Write-Info "Waiting for $Service to become healthy (max ${MaxWait}s)..."
    $elapsed = 0
    while ($elapsed -lt $MaxWait) {
        # Compose service container name is opengeobot-<service>-1
        $containers = @("opengeobot-$Service-1", "opengeobot_${Service}_1")
        $status = 'not-found'
        foreach ($c in $containers) {
            try {
                $result = docker inspect --format='{{.State.Health.Status}}' $c 2>$null
                if ($LASTEXITCODE -eq 0 -and $result) {
                    $status = $result.Trim()
                    break
                }
            } catch { }
        }
        if ($status -eq 'healthy') {
            Write-Ok "$Service is healthy"
            return
        }
        Write-Host -NoNewline '.'
        Start-Sleep -Seconds 2
        $elapsed += 2
    }
    Write-Host ''
    Write-Fail "$Service did not become healthy within ${MaxWait}s"
}

# =============================================================================
# Subcommands
# =============================================================================

function Invoke-Doctor {
    Write-Info 'Running environment diagnostics...'
    Write-Host ''
    $allGood = $true

    Write-Host '== Tools ==' -ForegroundColor Cyan

    if (Get-Command git -ErrorAction SilentlyContinue) {
        Write-Ok "git $(& git --version | ForEach-Object { $_ -replace 'git version ', '' })"
    } else {
        Write-Fail 'git not found'
        $allGood = $false
    }

    if (Get-Command java -ErrorAction SilentlyContinue) {
        $jv = & java -version 2>&1 | Select-Object -First 1
        if ($jv -match '"(\d+)\.') {
            $major = $Matches[1]
            if ($major -eq '21') {
                Write-Ok "java $($jv -replace '.*"([^"]+)".*', '`$1')"
            } else {
                Write-Fail "java found but version 21.x is required (got $major)"
                $allGood = $false
            }
        } else {
            Write-Ok 'java (version unclear)'
        }
    } else {
        Write-Fail 'java not found (require JDK 21)'
        $allGood = $false
    }

    if (Get-Command docker -ErrorAction SilentlyContinue) {
        Write-Ok "docker $(& docker --version)"
    } else {
        Write-Fail 'docker not found'
        $allGood = $false
    }

    if (docker compose version 2>$null) {
        Write-Ok 'docker compose available'
    } else {
        Write-Fail 'docker compose plugin not found'
        $allGood = $false
    }

    if (Get-Command node -ErrorAction SilentlyContinue) {
        Write-Ok "node $(& node --version)"
    } else {
        Write-Fail 'node not found (require >=22)'
        $allGood = $false
    }

    if (Get-Command pnpm -ErrorAction SilentlyContinue) {
        Write-Ok "pnpm $(& pnpm --version)"
    } else {
        Write-Warn 'pnpm not found (frontend bootstrap will be skipped)'
    }

    if (Get-Command python3 -ErrorAction SilentlyContinue) {
        Write-Ok "python3 $(& python3 --version)"
    } elseif (Get-Command python -ErrorAction SilentlyContinue) {
        Write-Ok "python $(& python --version)"
    } else {
        Write-Fail 'python3 not found (require 3.12)'
        $allGood = $false
    }

    if (Get-Command uv -ErrorAction SilentlyContinue) {
        Write-Ok "uv $(& uv --version 2>$null)"
    } else {
        Write-Warn 'uv not found (agent-runtime bootstrap will be skipped)'
    }

    Write-Host ''
    Write-Host '== Ports ==' -ForegroundColor Cyan
    Check-Port -Port 5432 -Label 'PostgreSQL'
    Check-Port -Port 4222 -Label 'NATS'
    Check-Port -Port 9000 -Label 'MinIO'
    Check-Port -Port 8080 -Label 'Cloud Control'
    Check-Port -Port 5173 -Label 'Vite Dev Server'

    Write-Host ''
    if ($allGood) {
        Write-Ok 'Environment looks ready'
    } else {
        Write-Warn 'Some checks failed — review the output above'
    }
}

function Invoke-Bootstrap {
    Write-Info 'Bootstrapping project dependencies...'
    Load-Env

    # 1. Maven validate (non-recursive to validate parent only)
    Write-Info 'Validating Maven parent POM...'
    $mvnw = Join-Path $RootDir 'mvnw'
    if ($IsWindows -or $env:OS -eq 'Windows_NT') {
        $mvnwCmd = Join-Path $RootDir 'mvnw.cmd'
    } else {
        $mvnwCmd = $mvnw
    }
    if (Test-Path $mvnwCmd) {
        if ($IsWindows -or $env:OS -eq 'Windows_NT') {
            & $mvnwCmd -N validate -B
        } else {
            & bash $mvnw -N validate -B
        }
        if ($LASTEXITCODE -eq 0) { Write-Ok 'Maven parent POM validated' }
        else { Write-Fail 'Maven validation failed' }
    } else {
        Write-Warn 'mvnw not found, skipping Maven validation'
    }

    # 2. Copy .env.example -> .env if missing (idempotent)
    if (-not (Test-Path $EnvFile)) {
        if (Test-Path $EnvExample) {
            Copy-Item $EnvExample $EnvFile
            Write-Ok 'Copied .env.example -> .env (adjust values as needed)'
        } else {
            Write-Warn '.env.example not found, skipping .env creation'
        }
    } else {
        Write-Ok '.env already exists'
    }

    # 3. Frontend dependencies (if package.json exists)
    $pkgJson = Join-Path $WebConsoleDir 'package.json'
    if (Test-Path $pkgJson) {
        if (Get-Command pnpm -ErrorAction SilentlyContinue) {
            Write-Info 'Installing frontend dependencies (pnpm install)...'
            & pnpm install
            Write-Ok 'Frontend dependencies installed'
        } else {
            Write-Warn 'pnpm not found, skipping frontend bootstrap'
        }
    } else {
        Write-Info 'apps/web-console/package.json not found yet, skipping frontend bootstrap'
    }

    # 4. Agent runtime dependencies (if pyproject.toml exists)
    $pyProject = Join-Path $AgentRuntimeDir 'pyproject.toml'
    if (Test-Path $pyProject) {
        if (Get-Command uv -ErrorAction SilentlyContinue) {
            Write-Info 'Syncing agent-runtime dependencies (uv sync)...'
            Push-Location $AgentRuntimeDir
            & uv sync
            Pop-Location
            Write-Ok 'Agent-runtime dependencies synced'
        } else {
            Write-Warn 'uv not found, skipping agent-runtime bootstrap'
        }
    } else {
        Write-Info 'services/agent-runtime/pyproject.toml not found, skipping agent bootstrap'
    }

    Write-Ok 'Bootstrap complete'
}

function Invoke-InfraUp {
    Write-Info 'Starting infrastructure profile...'
    Load-Env
    docker compose -f $ComposeFile --profile infra up -d
    if ($LASTEXITCODE -ne 0) { Write-Fail 'Failed to start infra'; exit 1 }
    Write-Ok 'Infrastructure containers started'

    Write-Info 'Waiting for healthchecks...'
    Wait-ForHealth -Service postgres -MaxWait 60
    Wait-ForHealth -Service nats -MaxWait 30
    Wait-ForHealth -Service minio -MaxWait 60
    Write-Ok 'Infrastructure is ready'
}

function Invoke-Migrate {
    Write-Info 'Running Flyway migrations...'
    Load-Env

    # Use localhost PostgreSQL (the dev workflow runs migrations against the host port)
    $dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }
    $dbPort = if ($env:DB_PORT) { $env:DB_PORT } else { '5432' }
    $dbName = if ($env:DB_NAME) { $env:DB_NAME } else { 'opengeobot' }
    $dbUser = if ($env:DB_USER) { $env:DB_USER } else { 'opengeobot' }
    $dbPass = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'opengeobot_dev' }

    $flywayUrl = "jdbc:postgresql://${dbHost}:${dbPort}/${dbName}"
    $mvnw = Join-Path $RootDir 'mvnw'
    if ($IsWindows -or $env:OS -eq 'Windows_NT') {
        & (Join-Path $RootDir 'mvnw.cmd') -pl apps/cloud-control/bootstrap flyway:migrate -B `
            "-Dflyway.url=$flywayUrl" `
            "-Dflyway.user=$dbUser" `
            "-Dflyway.password=$dbPass" `
            '-Dflyway.locations=classpath:db/migration' `
            '-Dflyway.baseline-on-migrate=true'
    } else {
        & bash $mvnw -pl apps/cloud-control/bootstrap flyway:migrate -B `
            "-Dflyway.url=$flywayUrl" `
            "-Dflyway.user=$dbUser" `
            "-Dflyway.password=$dbPass" `
            '-Dflyway.locations=classpath:db/migration' `
            '-Dflyway.baseline-on-migrate=true'
    }
    if ($LASTEXITCODE -eq 0) { Write-Ok 'Flyway migrations applied' }
    else { Write-Fail 'Flyway migration failed'; exit 1 }
}

function Stop-DevServers {
    foreach ($proc in $script:DevProcesses) {
        if ($proc -and -not $proc.HasExited) {
            try {
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            } catch { }
        }
    }
    $script:DevProcesses.Clear()

    # Clean up PID files
    $javaPidFile = Join-Path $PidDir 'java.pid'
    $webPidFile = Join-Path $PidDir 'web.pid'
    foreach ($pf in @($javaPidFile, $webPidFile)) {
        if (Test-Path $pf) {
            try {
                $pidVal = [int](Get-Content $pf -Raw).Trim()
                if ($pidVal -and (Get-Process -Id $pidVal -ErrorAction SilentlyContinue)) {
                    Stop-Process -Id $pidVal -Force -ErrorAction SilentlyContinue
                }
            } catch { }
            Remove-Item $pf -Force -ErrorAction SilentlyContinue
        }
    }
}

function Invoke-Dev {
    Write-Info 'Starting development servers...'
    Load-Env
    if (-not (Test-Path $PidDir)) { New-Item -ItemType Directory -Path $PidDir -Force | Out-Null }

    # Ensure no stale processes
    if ((Test-Path (Join-Path $PidDir 'java.pid')) -or (Test-Path (Join-Path $PidDir 'web.pid'))) {
        Write-Warn 'Stale PID files found, cleaning up...'
        Stop-DevServers
    }

    # Cleanup on exit
    $cleanup = {
        Write-Info 'Stopping development servers...'
        Stop-DevServers
    }
    $null = Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action $cleanup -ErrorAction SilentlyContinue

    # 1. Start Java backend
    Write-Info 'Starting cloud-control (Spring Boot)...'
    $env:SPRING_PROFILES_ACTIVE = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { 'dev' }
    $env:DB_HOST = if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }
    $env:DB_PORT = if ($env:DB_PORT) { $env:DB_PORT } else { '5432' }
    $env:DB_NAME = if ($env:DB_NAME) { $env:DB_NAME } else { 'opengeobot' }
    $env:DB_USER = if ($env:DB_USER) { $env:DB_USER } else { 'opengeobot' }
    $env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'opengeobot_dev' }
    $env:NATS_URL = if ($env:NATS_URL) { $env:NATS_URL } else { 'nats://localhost:4222' }
    $env:MINIO_ENDPOINT = if ($env:MINIO_ENDPOINT) { $env:MINIO_ENDPOINT } else { 'http://localhost:9000' }

    $javaLog = Join-Path $PidDir 'java.log'
    $mvnw = Join-Path $RootDir 'mvnw'
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    if ($IsWindows -or $env:OS -eq 'Windows_NT') {
        $psi.FileName = Join-Path $RootDir 'mvnw.cmd'
        $psi.Arguments = '-pl apps/cloud-control/bootstrap spring-boot:run -B'
    } else {
        $psi.FileName = 'bash'
        $psi.Arguments = "`"$mvnw`" -pl apps/cloud-control/bootstrap spring-boot:run -B"
    }
    $psi.WorkingDirectory = $RootDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $javaProc = [System.Diagnostics.Process]::Start($psi)
    $script:DevProcesses.Add($javaProc)
    Set-Content -Path (Join-Path $PidDir 'java.pid') -Value $javaProc.Id

    # Redirect output to log file in background
    $null = Register-ObjectEvent -InputObject $javaProc -EventName OutputDataReceived -Action {
        if ($EventArgs.Data) { Add-Content -Path (Join-Path $using:PidDir 'java.log') -Value $EventArgs.Data }
    }
    $javaProc.BeginOutputRead()

    # 2. Start frontend (if it exists)
    $webStarted = $false
    $pkgJson = Join-Path $WebConsoleDir 'package.json'
    if ((Test-Path $pkgJson) -and (Get-Command pnpm -ErrorAction SilentlyContinue)) {
        Write-Info 'Starting web-console (Vite)...'
        $webPsi = [System.Diagnostics.ProcessStartInfo]::new()
        $webPsi.FileName = 'pnpm'
        $webPsi.Arguments = 'dev'
        $webPsi.WorkingDirectory = $WebConsoleDir
        $webPsi.UseShellExecute = $false
        $webPsi.RedirectStandardOutput = $true
        $webPsi.RedirectStandardError = $true
        $webProc = [System.Diagnostics.Process]::Start($webPsi)
        $script:DevProcesses.Add($webProc)
        Set-Content -Path (Join-Path $PidDir 'web.pid') -Value $webProc.Id
        $webStarted = $true
    } else {
        Write-Warn 'web-console not ready or pnpm missing, skipping frontend'
    }

    Write-Host ''
    Write-Ok 'Development servers starting'
    Write-Host '  Backend  : http://localhost:8080' -ForegroundColor Cyan
    if ($webStarted) {
        Write-Host '  Frontend : http://localhost:5173' -ForegroundColor Cyan
    }
    Write-Host '  Press Ctrl+C to stop both servers' -ForegroundColor Yellow
    Write-Host ''

    # Keep the script alive until interrupted
    try {
        while ($script:DevProcesses.Count -gt 0) {
            $anyAlive = $false
            foreach ($p in $script:DevProcesses) {
                if (-not $p.HasExited) { $anyAlive = $true; break }
            }
            if (-not $anyAlive) { break }
            Start-Sleep -Milliseconds 500
        }
    } finally {
        Stop-DevServers
    }
}

function Invoke-SimUp {
    Write-Info 'Starting simulation stack...'
    Load-Env

    # Start infrastructure (NATS) and simulation services together.
    # docker compose up -d is idempotent: already-running containers are not restarted.
    docker compose -f $ComposeFile --profile infra --profile sim up -d --build
    if ($LASTEXITCODE -ne 0) { Write-Fail 'Failed to start simulation stack'; exit 1 }
    Write-Ok 'Simulation stack containers started'

    Write-Info 'Waiting for healthchecks...'
    Wait-ForHealth -Service nats -MaxWait 30
    Wait-ForHealth -Service sim-adapter -MaxWait 60
    Wait-ForHealth -Service edge-gateway -MaxWait 60
    Wait-ForHealth -Service safety-gateway -MaxWait 60
    Wait-ForHealth -Service local-skill-executor -MaxWait 60
    Write-Ok 'Simulation stack is ready'

    Write-Host ''
    Write-Host '  Simulation Stack Services:' -ForegroundColor Cyan
    Write-Host '  Safety Gateway   : http://localhost:8081/health' -ForegroundColor Cyan
    Write-Host '  NATS Monitoring   : http://localhost:8222' -ForegroundColor Cyan
    Write-Host '  MinIO Console     : http://localhost:9001' -ForegroundColor Cyan
    Write-Host "  Use 'down' to stop (data preserved)" -ForegroundColor Yellow
    Write-Host ''
}

function Invoke-Test {
    Write-Info 'Running Java tests...'
    Load-Env
    $mvnw = Join-Path $RootDir 'mvnw'
    if ($IsWindows -or $env:OS -eq 'Windows_NT') {
        & (Join-Path $RootDir 'mvnw.cmd') test -B
    } else {
        & bash $mvnw test -B
    }
    if ($LASTEXITCODE -eq 0) { Write-Ok 'Java tests passed' }
    else { Write-Fail 'Java tests failed'; exit 1 }

    $pkgJson = Join-Path $WebConsoleDir 'package.json'
    if ((Test-Path $pkgJson) -and (Get-Command pnpm -ErrorAction SilentlyContinue)) {
        $hasVitest = (Select-String -Path $pkgJson -Pattern '"vitest"' -Quiet) -or
                     (Test-Path (Join-Path $WebConsoleDir 'vitest.config.ts')) -or
                     (Test-Path (Join-Path $WebConsoleDir 'vitest.config.js'))
        if ($hasVitest) {
            Write-Info 'Running frontend tests (vitest)...'
            Push-Location $WebConsoleDir
            & pnpm test -- --run
            Pop-Location
            if ($LASTEXITCODE -eq 0) { Write-Ok 'Frontend tests passed' }
            else { Write-Fail 'Frontend tests failed'; exit 1 }
        } else {
            Write-Info 'Vitest not configured in web-console, skipping frontend tests'
        }
    }

    # --- Python component tests ---
    Write-Info 'Running Python tests...'
    $pyComponents = @(
        'edge/gateway',
        'edge/safety-gateway',
        'edge/local-skill-executor',
        'services/sim-adapter',
        'services/ros1-adapter',
        'services/agent-runtime',
        'services/mcp-tool-gateway'
    )
    $pyPassed = [System.Collections.ArrayList]::new()
    $pyFailed = [System.Collections.ArrayList]::new()
    $pySkipped = [System.Collections.ArrayList]::new()

    $hasUv = Get-Command uv -ErrorAction SilentlyContinue
    $hasPython = Get-Command python3 -ErrorAction SilentlyContinue
    if (-not $hasPython) { $hasPython = Get-Command python -ErrorAction SilentlyContinue }

    foreach ($comp in $pyComponents) {
        $compDir = Join-Path $RootDir $comp
        if (-not (Test-Path $compDir)) {
            Write-Warn "Directory not found, skipping: $comp"
            $pySkipped.Add($comp) | Out-Null
            continue
        }
        $pyProject = Join-Path $compDir 'pyproject.toml'
        if (-not (Test-Path $pyProject)) {
            Write-Info "No pyproject.toml, skipping: $comp"
            $pySkipped.Add($comp) | Out-Null
            continue
        }

        Write-Info "Testing Python component: $comp"
        Push-Location $compDir
        if ($hasUv) {
            & uv run pytest -q
        } elseif ($hasPython) {
            & $hasPython.Source -m pytest -q
        } else {
            Pop-Location
            Write-Warn "Neither uv nor python found, skipping: $comp"
            $pySkipped.Add($comp) | Out-Null
            continue
        }
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Python tests passed: $comp"
            $pyPassed.Add($comp) | Out-Null
        } else {
            Write-Fail "Python tests failed: $comp"
            $pyFailed.Add($comp) | Out-Null
        }
        Pop-Location
    }

    Write-Host ''
    Write-Host '== Python Test Summary ==' -ForegroundColor Cyan
    Write-Host "  Passed:  $($pyPassed.Count)"
    Write-Host "  Failed:  $($pyFailed.Count)"
    Write-Host "  Skipped: $($pySkipped.Count)"
    if ($pyPassed.Count -gt 0) {
        Write-Host "  Passed components: $($pyPassed -join ', ')"
    }
    if ($pyFailed.Count -gt 0) {
        Write-Host "  Failed components: $($pyFailed -join ', ')"
    }
    if ($pySkipped.Count -gt 0) {
        Write-Host "  Skipped components: $($pySkipped -join ', ')"
    }
    Write-Host ''

    if ($pyFailed.Count -gt 0) {
        Write-Warn 'Some Python test components failed — see output above'
    } else {
        Write-Ok 'All Python tests passed'
    }
}

function Invoke-E2E {
    Write-Info 'Starting full stack (infra + observability + cloud)...'
    Load-Env
    docker compose -f $ComposeFile --profile full up -d --build
    if ($LASTEXITCODE -ne 0) { Write-Fail 'Failed to start full stack'; exit 1 }
    Write-Ok 'Full stack containers started'

    Write-Info 'Waiting for healthchecks...'
    Wait-ForHealth -Service postgres -MaxWait 60
    Wait-ForHealth -Service nats -MaxWait 30
    Wait-ForHealth -Service minio -MaxWait 60
    Wait-ForHealth -Service cloud-control -MaxWait 120
    Wait-ForHealth -Service web-console -MaxWait 60
    Write-Ok 'Full stack is ready'
}

function Invoke-Down {
    Write-Info 'Stopping Docker Compose stack (data preserved)...'
    if (Test-Path $ComposeFile) {
        docker compose -f $ComposeFile down
        Write-Ok 'Docker Compose stack stopped (volumes retained)'
    } else {
        Write-Warn 'Compose file not found, skipping docker down'
    }
    # Also stop any background dev servers
    Stop-DevServers
}

function Show-Usage {
    @'
OpenGeoBot Unified Development Script

Usage:
  pwsh ./scripts/dev.ps1 <command>

Commands:
  doctor      Check toolchain and ports
  bootstrap   Install/validate all project dependencies (idempotent)
  infra-up    Start infrastructure containers (Postgres, NATS, MinIO)
  migrate     Run Flyway migrations against local PostgreSQL
  dev         Start backend + frontend dev servers (Ctrl+C to stop)
  sim-up      Start simulation stack (infra + sim-adapter + edge + safety + skill executor)
  test        Run Java, frontend (if configured), and Python tests
  e2e         Build and start the full stack (infra + observability + cloud)
  down        Stop Docker Compose stack and dev servers (keeps volumes)
'@
}

# =============================================================================
# Dispatch
# =============================================================================
switch ($Command) {
    'doctor'    { Invoke-Doctor }
    'bootstrap' { Invoke-Bootstrap }
    'infra-up'  { Invoke-InfraUp }
    'migrate'   { Invoke-Migrate }
    'dev'       { Invoke-Dev }
    'sim-up'    { Invoke-SimUp }
    'test'      { Invoke-Test }
    'e2e'       { Invoke-E2E }
    'down'      { Invoke-Down }
    { $_ -in '', '-h', '--help' } { Show-Usage }
    default {
        Write-Fail "Unknown command: $Command"
        Show-Usage
        exit 1
    }
}
