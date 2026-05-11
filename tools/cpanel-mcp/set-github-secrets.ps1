<#
.SYNOPSIS
  Configure GitHub Actions secrets and variables for the cPanel deploy workflow.

.DESCRIPTION
  Reads the deploy FTP password from .deploy-credentials.local.txt and writes
  every secret + variable the workflow needs into the GitHub repo.

  Run this once. After it completes, every push to main that touches
  "BillSense Admin/admin-panel/" auto-deploys to billsense.dev-environment.site.

.PREREQUISITES
  - GitHub CLI installed (https://cli.github.com/) — `gh --version` should work
  - You are inside a clone of the BillSense repo
  - tools/cpanel-mcp/.deploy-credentials.local.txt exists (created by the
    cPanel cutover script)

.USAGE
  pwsh ./tools/cpanel-mcp/set-github-secrets.ps1

  Or from the repo root:
  pwsh -File "tools/cpanel-mcp/set-github-secrets.ps1"
#>

$ErrorActionPreference = "Stop"

# --- locate files ---
$repoRoot = (& git rev-parse --show-toplevel).Trim()
if (-not $repoRoot) { throw "Run this from inside a git checkout of the BillSense repo." }
Set-Location $repoRoot

$credPath = "tools/cpanel-mcp/.deploy-credentials.local.txt"
if (-not (Test-Path $credPath)) {
  throw "Missing $credPath. Run the cPanel cutover script first (or recreate it manually with deploy@dev-environment.site's password)."
}

# --- check gh CLI ---
try { & gh --version | Out-Null } catch {
  Write-Host "GitHub CLI not installed. Install it: winget install GitHub.cli  (or https://cli.github.com/)" -ForegroundColor Red
  exit 1
}

# --- ensure logged in ---
$authStatus = & gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
  Write-Host "GitHub CLI is not authenticated. Running 'gh auth login' now..." -ForegroundColor Yellow
  & gh auth login -w -s "repo,workflow"
  if ($LASTEXITCODE -ne 0) { throw "gh auth login failed." }
}

# --- parse credentials file ---
$creds = @{}
Get-Content $credPath | ForEach-Object {
  if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.+)\s*$') {
    $creds[$matches[1]] = $matches[2].Trim()
  }
}
foreach ($k in @('CPANEL_FTP_HOST','CPANEL_FTP_USER','CPANEL_FTP_PASS','CPANEL_REMOTE_DIR')) {
  if (-not $creds.ContainsKey($k)) { throw "Missing $k in $credPath" }
}

# --- write secrets ---
Write-Host ""
Write-Host "Setting repository SECRETS..." -ForegroundColor Cyan
$secrets = @{
  CPANEL_FTP_HOST = $creds.CPANEL_FTP_HOST
  CPANEL_FTP_USER = $creds.CPANEL_FTP_USER
  CPANEL_FTP_PASS = $creds.CPANEL_FTP_PASS
}
foreach ($name in $secrets.Keys) {
  $value = $secrets[$name]
  $value | & gh secret set $name --body -
  if ($LASTEXITCODE -eq 0) {
    Write-Host "  + $name set" -ForegroundColor Green
  } else {
    Write-Host "  ! $name FAILED" -ForegroundColor Red
  }
}

# --- write variables ---
Write-Host ""
Write-Host "Setting repository VARIABLES..." -ForegroundColor Cyan
$variables = @{
  CPANEL_REMOTE_DIR  = $creds.CPANEL_REMOTE_DIR
  VITE_GITNEXUS_REPO = "UserDevAccount1/Billsense-Project"
}
foreach ($name in $variables.Keys) {
  $value = $variables[$name]
  & gh variable set $name --body $value
  if ($LASTEXITCODE -eq 0) {
    Write-Host "  + $name = $value" -ForegroundColor Green
  } else {
    Write-Host "  ! $name FAILED" -ForegroundColor Red
  }
}

# --- optional VITE_ secrets (skip if blank) ---
$optional = @{
  VITE_GEMINI_API_KEY = ""
  VITE_OPENAI_API_KEY = ""
  VITE_GITHUB_PAT     = ""
}
$haveOptional = $optional.Values | Where-Object { $_ -ne "" }
if ($haveOptional) {
  Write-Host ""
  Write-Host "Setting optional VITE_* secrets..." -ForegroundColor Cyan
  foreach ($name in $optional.Keys) {
    if ($optional[$name]) {
      $optional[$name] | & gh secret set $name --body -
      Write-Host "  + $name set" -ForegroundColor Green
    }
  }
}

Write-Host ""
Write-Host "Verifying configuration..." -ForegroundColor Cyan
& gh secret list
Write-Host ""
& gh variable list

# --- offer to trigger a deploy ---
Write-Host ""
$ans = Read-Host "Trigger the deploy workflow now to verify everything end-to-end? (y/N)"
if ($ans -eq 'y' -or $ans -eq 'Y') {
  & gh workflow run deploy-admin-cpanel.yml --ref main
  Start-Sleep -Seconds 3
  Write-Host ""
  Write-Host "Watching the run..." -ForegroundColor Cyan
  & gh run watch
} else {
  Write-Host ""
  Write-Host "To trigger later: gh workflow run deploy-admin-cpanel.yml --ref main" -ForegroundColor DarkGray
  Write-Host "To watch:         gh run watch" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Done. Next push to main that touches 'BillSense Admin/admin-panel/' will auto-deploy." -ForegroundColor Green
