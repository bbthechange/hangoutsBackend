#!/bin/bash
set -e

STAGING_PROFILE="inviter-staging"
PROD_ACCOUNT="871070087012"

# Safety check: Verify we're using the staging account
echo "Verifying AWS account and profile..."
CURRENT_ACCOUNT=$(aws sts get-caller-identity --profile ${STAGING_PROFILE} --query Account --output text 2>/dev/null || echo "ERROR")

if [ "$CURRENT_ACCOUNT" = "ERROR" ]; then
    echo "❌ ERROR: Unable to verify AWS account. Check if profile '${STAGING_PROFILE}' is configured."
    echo "Run: aws configure --profile ${STAGING_PROFILE}"
    exit 1
fi

if [ "$CURRENT_ACCOUNT" = "$PROD_ACCOUNT" ]; then
    echo "❌ ERROR: Current account ($CURRENT_ACCOUNT) is the PRODUCTION account!"
    echo "This script is designed for staging environment only."
    echo "Please configure a separate AWS account for staging with profile: ${STAGING_PROFILE}"
    exit 1
fi

echo "✅ Using staging account: ${CURRENT_ACCOUNT}"
echo "✅ Using profile: ${STAGING_PROFILE}"

API_ID=$(aws apigateway get-rest-apis --query 'items[?name==`inviter-staging-api`].id' --output text --profile ${STAGING_PROFILE})
BASE_URL="https://${API_ID}.execute-api.us-west-2.amazonaws.com/prod"

echo "Running end-to-end tests against: ${BASE_URL}"

# Test user registration
echo "Testing user registration..."
REGISTER_RESPONSE=$(curl -s -X POST "${BASE_URL}/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+15551234567", "password": "testpass123", "displayName": "Staging Test User"}')

echo "Register response: ${REGISTER_RESPONSE}"

# Test login
echo "Testing login..."
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+15551234567", "password": "testpass123"}')

echo "Login response: ${LOGIN_RESPONSE}"

# Extract JWT token
JWT_TOKEN=$(echo ${LOGIN_RESPONSE} | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -n "${JWT_TOKEN}" ]; then
  echo "✅ JWT token obtained successfully"
  
  # Test authenticated endpoints
  echo "Testing profile endpoint..."
  curl -s -H "Authorization: Bearer ${JWT_TOKEN}" "${BASE_URL}/profile" && echo "✅ Profile endpoint working"
  
  echo "Testing events endpoint..."
  curl -s -H "Authorization: Bearer ${JWT_TOKEN}" "${BASE_URL}/events" && echo "✅ Events endpoint working"
  
  echo "Testing image upload URL..."
  curl -s -X POST -H "Authorization: Bearer ${JWT_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"key": "events/test/test.jpg", "contentType": "image/jpeg"}' \
    "${BASE_URL}/images/upload-url" && echo "✅ Image upload URL working"
else
  echo "❌ Failed to obtain JWT token"
fi

echo "End-to-end testing complete!"