#!/bin/bash
set -e

STAGING_PROFILE="inviter-staging"
ENVIRONMENT_NAME="inviter-staging"

# Safety check: Verify we're using the staging account
echo "Verifying AWS account and profile..."
CURRENT_ACCOUNT=$(aws sts get-caller-identity --profile ${STAGING_PROFILE} --query Account --output text 2>/dev/null || echo "ERROR")
PROD_ACCOUNT="871070087012"

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

echo "Starting staging environment..."

# Scale up the environment
aws elasticbeanstalk update-environment \
  --environment-name ${ENVIRONMENT_NAME} \
  --option-settings \
    Namespace=aws:autoscaling:asg,OptionName=MinSize,Value=1 \
    Namespace=aws:autoscaling:asg,OptionName=MaxSize,Value=2 \
  --profile ${STAGING_PROFILE}

echo "Waiting for environment to be ready..."
aws elasticbeanstalk wait environment-updated --environment-names ${ENVIRONMENT_NAME} --profile ${STAGING_PROFILE}

# Get environment URL
EB_URL=$(aws elasticbeanstalk describe-environments \
  --environment-names ${ENVIRONMENT_NAME} \
  --query 'Environments[0].CNAME' \
  --output text --profile ${STAGING_PROFILE})

echo "Environment is ready!"
echo "Environment URL: http://${EB_URL}"
echo "Health check: curl http://${EB_URL}/health"