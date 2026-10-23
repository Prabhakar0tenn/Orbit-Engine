# Creates the Orbit-Engine tables on DynamoDB Local (start scripts\start-dynamodb-local.bat first).
$env:AWS_ACCESS_KEY_ID = "local"
$env:AWS_SECRET_ACCESS_KEY = "local"
$env:AWS_DEFAULT_REGION = "ap-south-1"
$endpoint = "http://localhost:8000"

Push-Location $PSScriptRoot
try {
    $existing = aws dynamodb list-tables --endpoint-url $endpoint --query "TableNames" --output text
    foreach ($table in "jobs", "executions", "idempotency_keys") {
        if ($existing -split "\s+" -contains $table) {
            Write-Host "$table already exists, skipping"
            continue
        }
        aws dynamodb create-table --cli-input-json "file://tables/$table.json" --endpoint-url $endpoint --no-cli-pager | Out-Null
        Write-Host "created $table"
    }
    aws dynamodb list-tables --endpoint-url $endpoint
}
finally {
    Pop-Location
}
