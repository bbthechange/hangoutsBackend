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

echo "Building and deploying to staging..."

# Build application
./gradlew clean build

# Copy JAR for deployment
cp build/libs/inviter-0.0.1-SNAPSHOT.jar ./application.jar

# Ensure environment is running before deployment
./scripts/start-staging-environment.sh

# Deploy to staging
eb deploy ${ENVIRONMENT_NAME} --profile ${STAGING_PROFILE}

echo "Deployment complete!"

# Test the deployment
EB_URL=$(aws elasticbeanstalk describe-environments \
  --environment-names ${ENVIRONMENT_NAME} \
  --query 'Environments[0].CNAME' \
  --output text --profile ${STAGING_PROFILE})

echo "Testing deployment..."
curl -f "http://${EB_URL}/health" && echo "✅ Health check passed" || echo "❌ Health check failed"