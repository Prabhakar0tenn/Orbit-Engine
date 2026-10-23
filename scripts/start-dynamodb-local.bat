@echo off
title DynamoDB Local (port 8000)

rem Change this if you move the DynamoDB Local folder.
set DDB_DIR=C:\Users\PrabhakarLal\Downloads\dynamodb_local_latest

if not exist "%DDB_DIR%\DynamoDBLocal.jar" (
    echo DynamoDBLocal.jar not found in %DDB_DIR%
    pause
    exit /b 1
)

cd /d "%DDB_DIR%"
echo Starting DynamoDB Local on http://localhost:8000 - close this window to stop it.
java -Djava.library.path=./DynamoDBLocal_lib -jar DynamoDBLocal.jar -sharedDb
pause
