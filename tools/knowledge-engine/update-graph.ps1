# Knowledge Graph Auto-Update Script for PixelPlayer (Windows Powershell)
# Integrates with git pre-commit/post-merge hooks

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$ScanScript = Join-Path $ScriptDir "scan-project.mjs"

if (Test-Path $ScanScript) {
    node $ScanScript
} else {
    Write-Error "Error: scan-project.mjs not found in $ScriptDir"
    exit 1
}

# Build the derived SQLite knowledge-graph DB
$KgCli = Join-Path $ScriptDir "query\dist\cli.js"
if (Test-Path $KgCli) {
    Write-Host "Building graph.db..." -ForegroundColor Cyan
    node $KgCli build
} else {
    Write-Host "Warning: query/dist/cli.js not found — skipping graph.db build." -ForegroundColor Yellow
    Write-Host "  Run: pnpm kg:compile && pnpm kg:build (from tools/knowledge-engine/)" -ForegroundColor Yellow
}

Write-Host "🎉 Codebase Knowledge Graph updated successfully!" -ForegroundColor Green
