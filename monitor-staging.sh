#!/bin/bash

# Staging Environment Health Monitor
# Tests key endpoints and reports any failures

STAGING_EB="http://inviter-staging.eba-pi6d9nbm.us-west-2.elasticbeanstalk.com"
STAGING_API="https://v7ihwy6uv9.execute-api.us-west-2.amazonaws.com/prod"

echo "Staging Environment Health Check - $(date)"
echo "=============================================="

# Test Health Endpoints
echo "Testing Health Endpoints:"
curl -s -w "EB Health: %{http_code}\n" -o /dev/null "$STAGING_EB/health"
curl -s -w "API Health: %{http_code}\n" -o /dev/null "$STAGING_API/health"

# Test Images Endpoint
echo -e "\nTesting Images Endpoints:"
EB_IMAGES=$(curl -s -w "%{http_code}" "$STAGING_EB/images/predefined")
if [[ "${EB_IMAGES: -3}" == "200" ]]; then
    echo "EB Images: 200 ($(echo "$EB_IMAGES" | jq '. | length') images)"
else
    echo "EB Images: FAILED - ${EB_IMAGES: -3}"
fi

API_IMAGES=$(curl -s -w "%{http_code}" "$STAGING_API/images/predefined")
if [[ "${API_IMAGES: -3}" == "200" ]]; then
    echo "API Images: 200 ($(echo "$API_IMAGES" | jq '. | length') images)"
else
    echo "API Images: FAILED - ${API_IMAGES: -3}"
fi

# Test Auth Endpoints (should return 401)
echo -e "\nTesting Auth Endpoints (expecting 401):"
curl -s -w "EB Auth: %{http_code}\n" -o /dev/null -X POST "$STAGING_EB/auth/login" \
  -H "Content-Type: application/json" -d '{"phoneNumber": "test", "password": "test"}'
curl -s -w "API Auth: %{http_code}\n" -o /dev/null -X POST "$STAGING_API/auth/login" \
  -H "Content-Type: application/json" -d '{"phoneNumber": "test", "password": "test"}'

echo -e "\nHealth check completed at $(date)"