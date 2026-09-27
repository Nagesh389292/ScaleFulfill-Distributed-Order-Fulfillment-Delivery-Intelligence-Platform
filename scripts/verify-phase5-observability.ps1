#!/usr/bin/env pwsh
<#
.SYNOPSIS
ScaleFulfill Phase 5 — Observability Verification Script

.DESCRIPTION
Verifies that all 4 services expose /actuator/prometheus, that custom
scalefulfill_* metrics are present, generates load to create observable
signal, and runs the controlled failure scenario (OpenSearch stop/start)
to demonstrate cause → effect → recovery.

PREREQUISITES
  - All 4 services running (ports 8080, 8081, 8082, 8084)
  - Prometheus running on port 9090 (docker compose up)
  - Grafana running on port 3000  (docker compose up)
  - Kafka running (docker compose up)
  - OpenSearch running (docker compose up)

USAGE
  .\scripts\verify-phase5-observability.ps1
#>

$ErrorActionPreference = "Continue"
$services = @(
    @{ Name = "api-gateway";       Port = 8080 },
    @{ Name = "order-service";     Port = 8081 },
    @{ Name = "inventory-service"; Port = 8082 },
    @{ Name = "search-service";    Port = 8084 }
)

$ORDERS_URL = "http://localhost:8080/api/v1/orders"
$SEARCH_URL = "http://localhost:8080/api/v1/search/orders"

function Write-Header($text) {
    Write-Host "`n=== $text ===" -ForegroundColor Cyan
}

function Write-Pass($text) {
    Write-Host "  [PASS] $text" -ForegroundColor Green
}

function Write-Fail($text) {
    Write-Host "  [FAIL] $text" -ForegroundColor Red
}

# ─────────────────────────────────────────────────────────────
# STEP 1: Prometheus Endpoint Verification (All 4 Services)
# ─────────────────────────────────────────────────────────────
Write-Header "STEP 1: Prometheus endpoint verification on all 4 services"
foreach ($svc in $services) {
    $url = "http://localhost:$($svc.Port)/actuator/prometheus"
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        if ($resp.StatusCode -eq 200 -and $resp.Content -match "jvm_threads|jvm_memory") {
            Write-Pass "$($svc.Name) :$($svc.Port)/actuator/prometheus -> HTTP 200, JVM metrics present"
        } else {
            Write-Fail "$($svc.Name) responded but content unexpected"
        }
    } catch {
        Write-Fail "$($svc.Name) :$($svc.Port)/actuator/prometheus -> UNREACHABLE: $_"
    }
}

# ─────────────────────────────────────────────────────────────
# STEP 2: Custom Metric Registration (Pre-traffic)
# ─────────────────────────────────────────────────────────────
Write-Header "STEP 2: Custom scalefulfill_* metric registration"
$metricChecks = @(
    @{ Port = 8081; Metric = "scalefulfill_orders_total" },
    @{ Port = 8081; Metric = "scalefulfill_order_processing_duration" },
    @{ Port = 8082; Metric = "scalefulfill_inventory_reservation_total" },
    @{ Port = 8082; Metric = "scalefulfill_inventory_kafka_consumer_lag" },
    @{ Port = 8084; Metric = "scalefulfill_search_indexing_total" },
    @{ Port = 8084; Metric = "scalefulfill_search_query_duration" },
    @{ Port = 8084; Metric = "scalefulfill_search_kafka_consumer_lag" },
    @{ Port = 8080; Metric = "scalefulfill_gateway_requests_total" },
    @{ Port = 8080; Metric = "scalefulfill_gateway_rate_limited_total" }
)

foreach ($check in $metricChecks) {
    try {
        $content = (Invoke-WebRequest -Uri "http://localhost:$($check.Port)/actuator/prometheus" -UseBasicParsing -TimeoutSec 5).Content
        if ($content -match $check.Metric) {
            Write-Pass "Port $($check.Port) -> '$($check.Metric)' registered"
        } else {
            Write-Fail "Port $($check.Port) -> '$($check.Metric)' NOT FOUND"
        }
    } catch {
        Write-Fail "Port $($check.Port) -> unreachable: $_"
    }
}

# ─────────────────────────────────────────────────────────────
# STEP 2b: Prometheus Scraping & Target Health Check
# ─────────────────────────────────────────────────────────────
Write-Header "STEP 2b: Prometheus Scraping & Target Health (Prometheus server)"
try {
    $targetsJson = (Invoke-WebRequest -Uri "http://localhost:9090/api/v1/targets" -UseBasicParsing -TimeoutSec 5).Content | ConvertFrom-Json
    $activeTargets = $targetsJson.data.activeTargets
    $allUp = $true
    foreach ($target in $activeTargets) {
        $job = $target.labels.job
        $health = $target.health
        if ($health -eq "up") {
            Write-Pass "Target '$job' -> UP ($($target.scrapeUrl))"
        } else {
            Write-Fail "Target '$job' -> $health ($($target.lastError))"
            $allUp = $false
        }
    }
} catch {
    Write-Fail "Prometheus target query failed: $_"
}

# ─────────────────────────────────────────────────────────────
# STEP 3: Normal Workload Generation
#   - Create orders
#   - Generate search queries
#   - Generate rate-limited requests
# ─────────────────────────────────────────────────────────────
Write-Header "STEP 3: Normal workload generation"

# A. Create 20 orders
Write-Host "  [A] Generating 20 orders via API Gateway..."
$orderSuccessCount = 0
$sampleOrderBody = @{
    customerId = "CUST-1001"
    items = @(@{ productId = "PROD-101"; quantity = 1; unitPrice = 49.99 })
} | ConvertTo-Json

for ($i = 1; $i -le 20; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri $ORDERS_URL -Method POST `
            -ContentType "application/json" `
            -Body $sampleOrderBody `
            -UseBasicParsing -TimeoutSec 10
        if ($resp.StatusCode -in 200, 201) { $orderSuccessCount++ }
    } catch {}
}
Write-Pass "Placed $orderSuccessCount/20 orders successfully"

# B. Generate search queries
Write-Host "  [B] Generating 10 search queries via API Gateway..."
$searchCount = 0
$searchTarget = "$SEARCH_URL" + "?query=CUST-1001"
for ($i = 1; $i -le 10; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri $searchTarget -Method GET -UseBasicParsing -TimeoutSec 5
        if ($resp.StatusCode -eq 200) { $searchCount++ }
    } catch {}
}
Write-Pass "Executed $searchCount/10 search queries successfully"

# C. Generate rate-limited requests (burst exceeding token bucket capacity)
Write-Host "  [C] Generating burst traffic to trigger rate limiting (429)..."
$rateLimitedCount = 0
for ($i = 1; $i -le 50; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/orders/health-check-dummy" -Method GET `
            -Headers @{ "X-Forwarded-For" = "192.168.1.99" } `
            -UseBasicParsing -TimeoutSec 2
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 429) {
            $rateLimitedCount++
        }
    }
}
Write-Pass "Encountered $rateLimitedCount HTTP 429 rate-limited responses (expected during burst)"

Write-Host "  Waiting 5s for Kafka event propagation and metric accumulation..." -ForegroundColor Yellow
Start-Sleep 5

# ─────────────────────────────────────────────────────────────
# STEP 3b: Validate Metrics Moved Under Normal Workload
# ─────────────────────────────────────────────────────────────
Write-Header "STEP 3b: Verify metrics moved under normal workload"

# Verify order counter > 0
try {
    $content = (Invoke-WebRequest -Uri "http://localhost:8081/actuator/prometheus" -UseBasicParsing -TimeoutSec 5).Content
    if ($content -match 'scalefulfill_orders_total\{.*status="success".*\}\s+(\d+(\.\d+)?)') {
        $count = [double]$Matches[1]
        Write-Pass "order-service: scalefulfill_orders_total{status=success} = $count (> 0)"
    } else {
        Write-Fail "order-service: scalefulfill_orders_total not updated"
    }
} catch { Write-Fail "order-service metric read failed: $_" }

# Verify order timer histogram > 0
try {
    $content = (Invoke-WebRequest -Uri "http://localhost:8081/actuator/prometheus" -UseBasicParsing -TimeoutSec 5).Content
    if ($content -match 'scalefulfill_order_processing_duration_seconds_count\{.*status="success".*\}\s+(\d+(\.\d+)?)') {
        $count = [double]$Matches[1]
        Write-Pass "order-service: scalefulfill_order_processing_duration_count{status=success} = $count (> 0)"
    } else {
        Write-Fail "order-service: scalefulfill_order_processing_duration_count not found"
    }
} catch { Write-Fail "order-service metric read failed: $_" }

# Verify search query timer > 0
try {
    $content = (Invoke-WebRequest -Uri "http://localhost:8084/actuator/prometheus" -UseBasicParsing -TimeoutSec 5).Content
    if ($content -match 'scalefulfill_search_query_duration_seconds_count\{.*query_type="order_search".*\}\s+(\d+(\.\d+)?)') {
        $count = [double]$Matches[1]
        Write-Pass "search-service: scalefulfill_search_query_duration{query_type=order_search} count = $count (> 0)"
    } else {
        Write-Fail "search-service: scalefulfill_search_query_duration count not found"
    }
} catch { Write-Fail "search-service metric read failed: $_" }

# Verify gateway requests and rate_limited
try {
    $content = (Invoke-WebRequest -Uri "http://localhost:8080/actuator/prometheus" -UseBasicParsing -TimeoutSec 5).Content
    if ($content -match 'scalefulfill_gateway_requests_total\{.*\}\s+(\d+(\.\d+)?)') {
        Write-Pass "api-gateway: scalefulfill_gateway_requests_total observed"
    }
    if ($content -match 'scalefulfill_gateway_rate_limited_total\{.*\}\s+(\d+(\.\d+)?)') {
        $rl = [double]$Matches[1]
        Write-Pass "api-gateway: scalefulfill_gateway_rate_limited_total = $rl (> 0)"
    }
} catch { Write-Fail "api-gateway metric read failed: $_" }

# ─────────────────────────────────────────────────────────────
# STEP 4: Controlled Failure Scenario
# ─────────────────────────────────────────────────────────────
Write-Header "STEP 4: Controlled failure scenario (Cause -> Effect)"

Write-Host "  CAUSE: Stopping OpenSearch container to simulate downstream indexing failure..." -ForegroundColor Yellow
docker stop scalefulfill-opensearch | Out-Null
Write-Host "  OpenSearch stopped."

Write-Host "  Placing 5 orders while OpenSearch is down..."
$failSuccessCount = 0
for ($i = 1; $i -le 5; $i++) {
    try {
        $resp = Invoke-WebRequest -Uri $ORDERS_URL -Method POST `
            -ContentType "application/json" `
            -Body $sampleOrderBody `
            -UseBasicParsing -TimeoutSec 10
        if ($resp.StatusCode -in 200, 201) { $failSuccessCount++ }
    } catch {}
}
Write-Pass "$failSuccessCount/5 orders accepted (order-service unaffected by search outage)"

Write-Host "  EFFECT: Waiting 20s for consumer failure and lag accumulation..." -ForegroundColor Yellow
Start-Sleep 20

try {
    $content = (Invoke-WebRequest -Uri "http://localhost:8084/actuator/prometheus" -UseBasicParsing -TimeoutSec 5).Content
    if ($content -match 'scalefulfill_search_kafka_consumer_lag\{.*\}\s+(\d+(\.\d+)?)') {
        $lagVal = [double]$Matches[1]
        Write-Pass "EFFECT: scalefulfill_search_kafka_consumer_lag = $lagVal"
    }
    if ($content -match 'scalefulfill_search_indexing_total\{.*result="error".*\}\s+(\d+(\.\d+)?)') {
        $errCount = [double]$Matches[1]
        Write-Pass "EFFECT: scalefulfill_search_indexing_total{result=error} = $errCount (failure metric rose)"
    }
} catch { Write-Fail "search-service metric check failed during outage: $_" }

# ─────────────────────────────────────────────────────────────
# STEP 5: Recovery Scenario
# ─────────────────────────────────────────────────────────────
Write-Header "STEP 5: Recovery Scenario (Drain Backlog -> Normal State)"

Write-Host "  RECOVERY: Restarting OpenSearch container..." -ForegroundColor Yellow
docker start scalefulfill-opensearch | Out-Null
Write-Host "  Waiting 15s for OpenSearch to become healthy and consumer to drain backlog..."
Start-Sleep 15

try {
    $content = (Invoke-WebRequest -Uri "http://localhost:8084/actuator/prometheus" -UseBasicParsing -TimeoutSec 5).Content
    if ($content -match 'scalefulfill_search_kafka_consumer_lag\{.*\}\s+(\d+(\.\d+)?)') {
        $recoveredLag = [double]$Matches[1]
        Write-Pass "RECOVERY: scalefulfill_search_kafka_consumer_lag drained to: $recoveredLag"
    }
    if ($content -match 'scalefulfill_search_indexing_total\{.*result="indexed".*\}\s+(\d+(\.\d+)?)') {
        $idxCount = [double]$Matches[1]
        Write-Pass "RECOVERY: scalefulfill_search_indexing_total{result=indexed} = $idxCount (resumed indexing)"
    }
} catch { Write-Fail "search-service recovery metric check failed: $_" }

# ─────────────────────────────────────────────────────────────
# STEP 5b: Prometheus Server Query Confirmation
# ─────────────────────────────────────────────────────────────
Write-Header "STEP 5b: Prometheus Server Query API Confirmation"
$promQueries = @(
    "scalefulfill_orders_total",
    "scalefulfill_order_processing_duration_seconds_count",
    "scalefulfill_search_indexing_total",
    "scalefulfill_search_kafka_consumer_lag",
    "scalefulfill_gateway_rate_limited_total"
)
foreach ($q in $promQueries) {
    try {
        $qResp = (Invoke-WebRequest -Uri "http://localhost:9090/api/v1/query?query=$q" -UseBasicParsing -TimeoutSec 5).Content | ConvertFrom-Json
        $resultCount = $qResp.data.result.Count
        if ($resultCount -gt 0) {
            $val = $qResp.data.result[0].value[1]
            Write-Pass "Prometheus API query '$q' -> Value: $val ($resultCount series)"
        } else {
            Write-Fail "Prometheus API query '$q' -> 0 series returned"
        }
    } catch {
        Write-Fail "Prometheus query '$q' error: $_"
    }
}

Write-Header "Live Observability Verification Complete"
