#!/bin/bash

BASE_URL="http://localhost:8080/v1"

echo "=========================================="
echo "Salary Calculator - API Test Requests"
echo "=========================================="
echo ""

echo "1. Health Check"
curl -s -X GET "$BASE_URL/health" | jq '.'
echo -e "\n"

echo "2. Supported Countries"
curl -
