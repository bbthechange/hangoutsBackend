#!/bin/bash
set -e

STAGING_PROFILE="inviter-staging"
REGION="us-west-2"
API_NAME="inviter-staging-api"
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

# Get EB environment URL
EB_URL=$(aws elasticbeanstalk describe-environments \
  --application-name inviter-app-staging \
  --environment-names inviter-staging \
  --query 'Environments[0].CNAME' \
  --output text --profile ${STAGING_PROFILE})

echo "Setting up API Gateway for EB URL: http://${EB_URL}"

# 1. Create REST API
API_ID=$(aws apigateway create-rest-api \
  --name ${API_NAME} \
  --description "Staging API Gateway for Inviter" \
  --endpoint-configuration types=EDGE \
  --query 'id' --output text --profile ${STAGING_PROFILE})

echo "Created API Gateway: ${API_ID}"

# Get root resource ID
ROOT_RESOURCE_ID=$(aws apigateway get-resources \
  --rest-api-id ${API_ID} \
  --query 'items[?path==`/`].id' \
  --output text --profile ${STAGING_PROFILE})

# 2. Create root resource method (ANY /)
aws apigateway put-method \
  --rest-api-id ${API_ID} \
  --resource-id ${ROOT_RESOURCE_ID} \
  --http-method ANY \
  --authorization-type NONE \
  --profile ${STAGING_PROFILE}

# Set up root integration
aws apigateway put-integration \
  --rest-api-id ${API_ID} \
  --resource-id ${ROOT_RESOURCE_ID} \
  --http-method ANY \
  --type HTTP_PROXY \
  --integration-http-method ANY \
  --uri "http://${EB_URL}/" \
  --profile ${STAGING_PROFILE}

# 3. Create proxy resource ({proxy+})
PROXY_RESOURCE_ID=$(aws apigateway create-resource \
  --rest-api-id ${API_ID} \
  --parent-id ${ROOT_RESOURCE_ID} \
  --path-part '{proxy+}' \
  --query 'id' --output text --profile ${STAGING_PROFILE})

# Create proxy method (ANY {proxy+})
aws apigateway put-method \
  --rest-api-id ${API_ID} \
  --resource-id ${PROXY_RESOURCE_ID} \
  --http-method ANY \
  --authorization-type NONE \
  --request-parameters method.request.path.proxy=true \
  --profile ${STAGING_PROFILE}

# Set up proxy integration
aws apigateway put-integration \
  --rest-api-id ${API_ID} \
  --resource-id ${PROXY_RESOURCE_ID} \
  --http-method ANY \
  --type HTTP_PROXY \
  --integration-http-method ANY \
  --uri "http://${EB_URL}/{proxy}" \
  --request-parameters integration.request.path.proxy=method.request.path.proxy \
  --profile ${STAGING_PROFILE}

# 4. Deploy API
aws apigateway create-deployment \
  --rest-api-id ${API_ID} \
  --stage-name prod \
  --stage-description "Staging environment" \
  --description "Initial deployment" \
  --profile ${STAGING_PROFILE}

# Get API Gateway URL
API_URL="https://${API_ID}.execute-api.${REGION}.amazonaws.com/prod"

echo "API Gateway setup complete!"
echo "API ID: ${API_ID}"
echo "API URL: ${API_URL}"
echo "Test with: curl ${API_URL}/health"