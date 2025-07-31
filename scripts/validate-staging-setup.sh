#!/bin/bash
set -e

STAGING_PROFILE="inviter-staging"
ENVIRONMENT_NAME="inviter-staging"
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

echo "Validating staging environment setup..."

# 1. Verify EB environment health
echo "Checking Elastic Beanstalk environment..."
aws elasticbeanstalk describe-environment-health \
  --environment-name ${ENVIRONMENT_NAME} \
  --attribute-names All \
  --profile ${STAGING_PROFILE}

# 2. Verify DynamoDB tables
echo "Checking DynamoDB tables..."
TABLES=$(aws dynamodb list-tables --region us-west-2 --profile ${STAGING_PROFILE} --query 'TableNames' --output text)
echo "Available tables: ${TABLES}"

for table in Users Events Invites Devices; do
  aws dynamodb describe-table --table-name ${table} --region us-west-2 --profile ${STAGING_PROFILE} > /dev/null \
    && echo "✅ Table ${table} exists" || echo "❌ Table ${table} missing"
done

# 3. Verify S3 bucket
S3_BUCKET="inviter-event-images-staging-${CURRENT_ACCOUNT}"
echo "Checking S3 bucket: ${S3_BUCKET}"
aws s3 ls s3://${S3_BUCKET}/predefined/ --profile ${STAGING_PROFILE} > /dev/null \
  && echo "✅ S3 bucket accessible with predefined images" || echo "❌ S3 bucket issues"

# 4. Test application endpoints
EB_URL=$(aws elasticbeanstalk describe-environments \
  --environment-names ${ENVIRONMENT_NAME} \
  --query 'Environments[0].CNAME' \
  --output text --profile ${STAGING_PROFILE})

echo "Testing application at: http://${EB_URL}"

# Health check
curl -f "http://${EB_URL}/health" > /dev/null \
  && echo "✅ Health endpoint working" || echo "❌ Health endpoint failed"

# Predefined images
curl -f "http://${EB_URL}/images/predefined" > /dev/null \
  && echo "✅ Predefined images endpoint working" || echo "❌ Predefined images failed"

# Swagger UI
curl -f "http://${EB_URL}/swagger-ui.html" > /dev/null \
  && echo "✅ Swagger UI accessible" || echo "❌ Swagger UI failed"

echo "Validation complete!"