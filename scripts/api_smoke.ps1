# 功能：OpenGEO Bot MVP API 端到端验证脚本
# 时间：2026-05-08 14:07:00
# 作者：AxeXie

$ErrorActionPreference = "Stop"
$base = if ($env:OPEN_GEOBOT_BASE_URL) { $env:OPEN_GEOBOT_BASE_URL } else { "http://127.0.0.1:8000" }

Write-Host "[1/10] Create project"
$projectBody = @{
    project_name = "OpenGEO API Demo"
    project_type = "website"
    source_url = "https://example.com"
    brand_name = "OpenGEO"
    aliases = @("opengeo bot")
    language = "zh-CN"
    region = "global"
    competitors = @("comp-a")
} | ConvertTo-Json -Depth 5
$project = Invoke-RestMethod -Method Post -Uri "$base/projects" -ContentType "application/json" -Body $projectBody
$projectId = $project.project_id

Write-Host "[2/10] Generate prompts"
$promptBody = @{ count = 20 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/prompts/generate" -ContentType "application/json" -Body $promptBody | Out-Null

Write-Host "[3/10] Create baseline run"
$runBody = @{
    run_type = "baseline"
    engines = @("engine_alpha", "engine_beta")
} | ConvertTo-Json
$baseline = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/runs" -ContentType "application/json" -Body $runBody

Write-Host "[4/10] Generate insights"
$insightBody = @{
    run_id = $baseline.run_id
    limit = 20
} | ConvertTo-Json
$insights = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/insights" -ContentType "application/json" -Body $insightBody
if ($insights -isnot [System.Array]) {
    $insights = @($insights)
}
if ($insights.Count -eq 0) {
    Write-Host "No insights generated, create another run and retry."
    $retryRunBody = @{
        run_type = "on-demand"
        engines = @("engine_alpha", "engine_beta")
    } | ConvertTo-Json
    $retryRun = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/runs" -ContentType "application/json" -Body $retryRunBody
    $insightBody = @{
        run_id = $retryRun.run_id
        limit = 20
    } | ConvertTo-Json
    $insights = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/insights" -ContentType "application/json" -Body $insightBody
    if ($insights -isnot [System.Array]) {
        $insights = @($insights)
    }
}
if ($insights.Count -eq 0) {
    throw "No insights generated after retry."
}

Write-Host "[5/10] Generate playbook"
$playbookBody = @{
    insight_id = $insights[0].insight_id
} | ConvertTo-Json
$playbook = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/playbooks" -ContentType "application/json" -Body $playbookBody

Write-Host "[6/10] Create after run"
$afterBody = @{
    run_type = "after"
    engines = @("engine_alpha", "engine_beta")
} | ConvertTo-Json
$after = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/runs" -ContentType "application/json" -Body $afterBody

Write-Host "[7/10] Verify baseline/after"
$verifyBody = @{
    baseline_run_id = $baseline.run_id
    after_run_id = $after.run_id
} | ConvertTo-Json
$verification = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/verification" -ContentType "application/json" -Body $verifyBody

Write-Host "[8/10] Save strategy memory"
$memoryBody = @{
    playbook_id = $playbook.playbook_id
    verification_report_id = $verification.report_id
} | ConvertTo-Json
$memory = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/strategy-memory" -ContentType "application/json" -Body $memoryBody

Write-Host "[9/10] Build monitor report"
$monitor = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/monitor/$($after.run_id)?language=zh-CN"

Write-Host "[10/10] Read overview + weekly report"
$overview = Invoke-RestMethod -Method Get -Uri "$base/projects/$projectId/overview"
$weekly = Invoke-RestMethod -Method Get -Uri "$base/projects/$projectId/weekly-report?language=zh-CN"

Write-Host "`n=== Smoke Result ==="
Write-Host "project_id: $projectId"
Write-Host "baseline_run_id: $($baseline.run_id)"
Write-Host "after_run_id: $($after.run_id)"
Write-Host "insight_count: $($insights.Count)"
Write-Host "playbook_id: $($playbook.playbook_id)"
Write-Host "verification_summary: $($verification.summary)"
Write-Host "strategy_memory_id: $($memory.memory_id)"
Write-Host "monitor_alert_count: $($monitor.alerts.Count)"
Write-Host "top_opportunity_count: $($overview.top_opportunities.Count)"
Write-Host "weekly_subject: $($weekly.subject)"
# 功能：OpenGEO Bot MVP API 端到端验证脚本
# 时间：2026-05-08 14:07:00
# 作者：AxeXie

$ErrorActionPreference = "Stop"
$base = if ($env:OPEN_GEOBOT_BASE_URL) { $env:OPEN_GEOBOT_BASE_URL } else { "http://127.0.0.1:8000" }

Write-Host "[1/10] Create project"
$projectBody = @{
    project_name = "OpenGEO API Demo"
    project_type = "website"
    source_url = "https://example.com"
    brand_name = "OpenGEO"
    aliases = @("opengeo bot")
    language = "zh-CN"
    region = "global"
    competitors = @("comp-a")
} | ConvertTo-Json -Depth 5
$project = Invoke-RestMethod -Method Post -Uri "$base/projects" -ContentType "application/json" -Body $projectBody
$projectId = $project.project_id

Write-Host "[2/10] Generate prompts"
$promptBody = @{ count = 20 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/prompts/generate" -ContentType "application/json" -Body $promptBody | Out-Null

Write-Host "[3/10] Create baseline run"
$runBody = @{
    run_type = "baseline"
    engines = @("engine_alpha", "engine_beta")
} | ConvertTo-Json
$baseline = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/runs" -ContentType "application/json" -Body $runBody

Write-Host "[4/10] Generate insights"
$insightBody = @{
    run_id = $baseline.run_id
    limit = 20
} | ConvertTo-Json
$insights = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/insights" -ContentType "application/json" -Body $insightBody
if ($insights.Count -eq 0) {
    throw "No insights generated."
}

Write-Host "[5/10] Generate playbook"
$playbookBody = @{
    insight_id = $insights[0].insight_id
} | ConvertTo-Json
$playbook = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/playbooks" -ContentType "application/json" -Body $playbookBody

Write-Host "[6/10] Create after run"
$afterBody = @{
    run_type = "after"
    engines = @("engine_alpha", "engine_beta")
} | ConvertTo-Json
$after = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/runs" -ContentType "application/json" -Body $afterBody

Write-Host "[7/10] Verify baseline/after"
$verifyBody = @{
    baseline_run_id = $baseline.run_id
    after_run_id = $after.run_id
} | ConvertTo-Json
$verification = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/verification" -ContentType "application/json" -Body $verifyBody

Write-Host "[8/10] Save strategy memory"
$memoryBody = @{
    playbook_id = $playbook.playbook_id
    verification_report_id = $verification.report_id
} | ConvertTo-Json
$memory = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/strategy-memory" -ContentType "application/json" -Body $memoryBody

Write-Host "[9/10] Build monitor report"
$monitor = Invoke-RestMethod -Method Post -Uri "$base/projects/$projectId/monitor/$($after.run_id)?language=zh-CN"

Write-Host "[10/10] Read overview + weekly report"
$overview = Invoke-RestMethod -Method Get -Uri "$base/projects/$projectId/overview"
$weekly = Invoke-RestMethod -Method Get -Uri "$base/projects/$projectId/weekly-report?language=zh-CN"

Write-Host "`n=== Smoke Result ==="
Write-Host "project_id: $projectId"
Write-Host "baseline_run_id: $($baseline.run_id)"
Write-Host "after_run_id: $($after.run_id)"
Write-Host "insight_count: $($insights.Count)"
Write-Host "playbook_id: $($playbook.playbook_id)"
Write-Host "verification_summary: $($verification.summary)"
Write-Host "strategy_memory_id: $($memory.memory_id)"
Write-Host "monitor_alert_count: $($monitor.alerts.Count)"
Write-Host "top_opportunity_count: $($overview.top_opportunities.Count)"
Write-Host "weekly_subject: $($weekly.subject)"
