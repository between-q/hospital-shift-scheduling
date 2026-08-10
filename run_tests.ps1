# Hospital Shift Scheduling System - Automated Test Script
# Usage: powershell -ExecutionPolicy Bypass -File run_tests.ps1
# Ensure backend is running at http://localhost:9090

$ErrorActionPreference = "Continue"
$BASE_URL = "http://localhost:9090"
$script:PASS = 0
$script:FAIL = 0
$script:TOTAL = 0

function Write-Result {
    param([string]$Name, [bool]$Ok, [string]$Detail = "")
    $script:TOTAL++
    if ($Ok) { $script:PASS++; Write-Host "  [PASS] $Name" -ForegroundColor Green }
    else { $script:FAIL++; Write-Host "  [FAIL] $Name - $Detail" -ForegroundColor Red }
}

function Get-Token {
    param([string]$Email, [string]$Password)
    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    try {
        $r = Invoke-RestMethod -Uri "$BASE_URL/api/auth/login" -Method POST -Body $body -ContentType "application/json"
        return $r.token
    } catch { return $null }
}

function Api-Get {
    param([string]$Path, [hashtable]$Headers = @{})
    return Invoke-RestMethod -Uri "$BASE_URL$Path" -Method GET -Headers $Headers
}

function Api-Post {
    param([string]$Path, [object]$Body, [hashtable]$Headers = @{})
    $json = $Body | ConvertTo-Json -Depth 5
    return Invoke-RestMethod -Uri "$BASE_URL$Path" -Method POST -Body $json -ContentType "application/json" -Headers $Headers
}

function Api-Put {
    param([string]$Path, [object]$Body, [hashtable]$Headers = @{})
    $json = $Body | ConvertTo-Json -Depth 5
    return Invoke-RestMethod -Uri "$BASE_URL$Path" -Method PUT -Body $json -ContentType "application/json" -Headers $Headers
}

function Api-Delete {
    param([string]$Path, [hashtable]$Headers = @{})
    return Invoke-WebRequest -Uri "$BASE_URL$Path" -Method DELETE -Headers $Headers
}

function Get-StatusCode {
    param([scriptblock]$Action)
    try { & $Action; return 200 }
    catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Hospital Shift Scheduling - Tests" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Authentication
Write-Host "--- Auth Tests ---" -ForegroundColor Yellow

$adminToken = Get-Token "admin@hospital.local" "Admin123!"
Write-Result "Admin login" ($adminToken -ne $null)

$docToken = Get-Token "demo_doc1@hospital.local" "Demo123!"
Write-Result "Doctor login" ($docToken -ne $null)

$badToken = Get-Token "admin@hospital.local" "wrongpass"
Write-Result "Reject wrong password" ($badToken -eq $null)

$adminH = @{ Authorization = "Bearer $adminToken" }
$docH = @{ Authorization = "Bearer $docToken" }

$code = Get-StatusCode { Api-Get "/api/shifts" }
Write-Result "Reject unauthenticated access" ($code -eq 401 -or $code -eq 403) "status=$code"

$code = Get-StatusCode { Api-Get "/api/admin/users" $docH }
Write-Result "Reject doctor accessing admin API" ($code -eq 401 -or $code -eq 403) "status=$code"

# 2. Department Management
Write-Host ""
Write-Host "--- Department Tests ---" -ForegroundColor Yellow

$depts = Api-Get "/api/departments" $adminH
Write-Result "List all departments" ($depts.Count -ge 4) "count=$($depts.Count)"

$dept1 = Api-Get "/api/departments/1" $adminH
Write-Result "Get department by ID" ($dept1.id -eq 1)

$newDept = Api-Post "/api/departments" @{ name = "TestDept_AUTO"; description = "Auto test" } $adminH
$testDeptId = $newDept.id
Write-Result "Create department" ($testDeptId -ne $null) "id=$testDeptId"

$code = Get-StatusCode { Api-Post "/api/departments" @{ name = "IllegalDept" } $docH }
Write-Result "Reject doctor creating dept" ($code -eq 401 -or $code -eq 403) "status=$code"

if ($testDeptId) {
    $updated = Api-Put "/api/departments/$testDeptId" @{ name = "TestDept_Updated" } $adminH
    Write-Result "Update department" ($updated.name -eq "TestDept_Updated")
}

# 3. User Management (replaces Employee tests)
Write-Host ""
Write-Host "--- User Management Tests ---" -ForegroundColor Yellow

$users = Api-Get "/api/admin/users" $adminH
Write-Result "List all users (admin)" ($users.Count -ge 5) "count=$($users.Count)"

$user1 = Api-Get "/api/admin/users/1" $adminH
Write-Result "Get user by ID" ($user1.id -eq 1)

# 4. Shift Management
Write-Host ""
Write-Host "--- Shift Tests ---" -ForegroundColor Yellow

$shifts = Api-Get "/api/shifts" $adminH
Write-Result "List all shifts" ($shifts.Count -ge 8) "count=$($shifts.Count)"

$openShifts = Api-Get "/api/shifts/open" $adminH
Write-Result "List open shifts" ($openShifts -is [array] -or $openShifts -ne $null)

$shiftById = Api-Get "/api/shifts/1" $adminH
Write-Result "Get shift by ID" ($shiftById.id -eq 1)

# Create shift - use correct DTO format
$newShift = Api-Post "/api/shifts" @{
    startTime = "2026-08-01T08:00:00"
    endTime = "2026-08-01T16:00:00"
    requiredRole = "DOCTOR"
    departmentId = 1
} $adminH
$testShiftId = $newShift.id
Write-Result "Create shift" ($testShiftId -ne $null) "id=$testShiftId"

# Assign employee to shift (use /api/shifts/{id} with UpdateShiftAssignmentRequest)
if ($testShiftId) {
    # Find a user with DOCTOR role (admin user doesn't have DOCTOR role)
    $doctorUser = $users | Where-Object { $_.roles -contains "DOCTOR" } | Select-Object -First 1
    if (-not $doctorUser) { $doctorUser = $users | Where-Object { $_.email -like "demo_doc*" } | Select-Object -First 1 }
    if ($doctorUser) {
        $assigned = Api-Put "/api/shifts/$testShiftId" @{
            assigneeUserId = $doctorUser.id
            status = "ASSIGNED"
        } $adminH
        Write-Result "Assign user to shift" ($assigned.status -eq "ASSIGNED" -or $assigned.assigneeUserId -eq $doctorUser.id)
    } else {
        Write-Result "Assign user to shift" $false "No doctor user found"
    }
}

# Update shift details via admin endpoint (requires ALL @NotNull fields)
if ($testShiftId) {
    $updShift = Api-Put "/api/admin/shifts/$testShiftId" @{
        startTime = "2026-08-01T09:00:00"
        endTime = "2026-08-01T17:00:00"
        requiredRole = "DOCTOR"
        status = "ASSIGNED"
        departmentId = 1
        notes = "Updated by test"
    } $adminH
    Write-Result "Update shift details" ($updShift -ne $null)
}

# Doctor cannot delete shift
$code = Get-StatusCode { Api-Delete "/api/shifts/$testShiftId" $docH }
Write-Result "Reject doctor deleting shift" ($code -eq 401 -or $code -eq 403) "status=$code"

# 5. Duty Calendar
Write-Host ""
Write-Host "--- Calendar Tests ---" -ForegroundColor Yellow

$cal = Api-Get "/api/calendar" $adminH
Write-Result "List calendar entries" ($cal -is [array] -or $cal -ne $null) "count=$($cal.Count)"

$newCal = Api-Post "/api/calendar" @{
    date = "2026-08-01"
    departmentId = 1
    summary = "Auto test duty"
    headcount = 3
} $adminH
$testCalId = $newCal.id
Write-Result "Create calendar entry" ($testCalId -ne $null) "id=$testCalId"

# 6. Agent Tasks
Write-Host ""
Write-Host "--- Agent Task Tests ---" -ForegroundColor Yellow

$task = Api-Post "/api/agent/tasks" @{
    taskType = "GENERATE_SCHEDULE"
    payload = '{"departmentId":1,"startDate":"2026-08-01","endDate":"2026-08-31"}'
} $adminH
$testTaskId = $task.id
Write-Result "Create agent task" ($testTaskId -ne $null) "id=$testTaskId"

$pending = Api-Get "/api/agent/tasks/pending" $adminH
Write-Result "List pending tasks" ($pending -is [array]) "count=$($pending.Count)"

if ($testTaskId) {
    $updTask = Api-Put "/api/agent/tasks/$testTaskId" @{
        status = "COMPLETED"
        result = '{"message":"Test done"}'
    } $adminH
    Write-Result "Update task status" ($updTask.status -eq "COMPLETED")
}

# 7. Edge Cases
Write-Host ""
Write-Host "--- Edge Case Tests ---" -ForegroundColor Yellow

$code = Get-StatusCode { Api-Get "/api/departments/99999" $adminH }
Write-Result "404/400 for nonexistent dept" ($code -eq 404 -or $code -eq 400) "status=$code"

$code = Get-StatusCode { Api-Post "/api/departments" @{ name = "" } $adminH }
Write-Result "Reject empty dept name" ($code -ne 200) "status=$code"

# 8. Cleanup
Write-Host ""
Write-Host "--- Cleanup ---" -ForegroundColor Yellow

if ($null -ne $testShiftId -and $testShiftId -gt 0) {
    try {
        $delResult = Invoke-WebRequest -Uri "$BASE_URL/api/shifts/$testShiftId" -Method DELETE -Headers $adminH -UseBasicParsing
        Write-Result "Delete test shift (ID=$testShiftId)" $true
    } catch {
        Write-Result "Delete test shift (ID=$testShiftId)" $false $_.Exception.Message
    }
} else {
    Write-Result "Delete test shift" $true "Skipped (no ID)"
}

if ($null -ne $testDeptId -and $testDeptId -gt 0) {
    try {
        $delResult = Invoke-WebRequest -Uri "$BASE_URL/api/departments/$testDeptId" -Method DELETE -Headers $adminH -UseBasicParsing
        Write-Result "Delete test department (ID=$testDeptId)" $true
    } catch {
        Write-Result "Delete test department (ID=$testDeptId)" $false $_.Exception.Message
    }
} else {
    Write-Result "Delete test department" $true "Skipped (no ID)"
}

# Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Tests Complete: $script:TOTAL total" -ForegroundColor Cyan
if ($script:FAIL -eq 0) {
    Write-Host "  ALL PASSED: $script:PASS / $script:TOTAL" -ForegroundColor Green
} else {
    Write-Host "  PASSED: $script:PASS  FAILED: $script:FAIL" -ForegroundColor Red
}
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

exit $(if ($script:FAIL -gt 0) { 1 } else { 0 })
