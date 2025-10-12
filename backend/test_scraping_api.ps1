# Test Scraping API
# This script demonstrates how a registrar can scrape courses

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "SAM COURSE SCRAPING API TEST" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

# Base URL
$baseUrl = "http://localhost:8080/api/scraping"

# Test 1: Get available subjects
Write-Host "`n--- Test 1: Get Available Subjects ---" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/available-subjects" -Method Get
    Write-Host "Available subjects: $($response.subjects -join ', ')" -ForegroundColor Green
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
}

# Test 2: Scrape CSE and BIO for Fall 2025
Write-Host "`n--- Test 2: Scrape CSE and BIO for Fall 2025 ---" -ForegroundColor Yellow
$scrapingRequest = @{
    term = "Fall 2025"
    subjects = @("CSE", "BIO")
} | ConvertTo-Json

Write-Host "Request: $scrapingRequest"

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/scrape" -Method Post `
        -Body $scrapingRequest `
        -ContentType "application/json"
    
    Write-Host "Success: $($response.success)" -ForegroundColor Green
    Write-Host "Message: $($response.message)" -ForegroundColor Green
    Write-Host "Term: $($response.term)" -ForegroundColor Green
    Write-Host "Subjects: $($response.subjects -join ', ')" -ForegroundColor Green
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
}

# Wait for scraping to complete
Write-Host "`nWaiting for scraping to complete (60 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 60

# Test 3: Get statistics
Write-Host "`n--- Test 3: Get Scraping Statistics ---" -ForegroundColor Yellow
try {
    $stats = Invoke-RestMethod -Uri "$baseUrl/stats" -Method Get
    Write-Host "Total Courses: $($stats.totalCourses)" -ForegroundColor Green
    Write-Host "Total Terms: $($stats.totalTerms)" -ForegroundColor Green
    Write-Host "Scraped Terms: $($stats.scrapedTerms)" -ForegroundColor Green
    Write-Host "`nCourses by Subject:" -ForegroundColor Green
    $stats.coursesBySubject.PSObject.Properties | ForEach-Object {
        Write-Host "  $($_.Name): $($_.Value)" -ForegroundColor Green
    }
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
}

# Test 4: Try to scrape the same term again (should be prevented)
Write-Host "`n--- Test 4: Try to Re-scrape Fall 2025 (Should Fail) ---" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/scrape" -Method Post `
        -Body $scrapingRequest `
        -ContentType "application/json"
    
    Write-Host "Response: $($response.message)" -ForegroundColor Yellow
} catch {
    Write-Host "Expected: Scraping prevented (term already scraped)" -ForegroundColor Green
}

Write-Host "`n================================================================================" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan

