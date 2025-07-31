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

echo "Shutting down staging environment to save costs..."

# Scale down to zero instances
aws elasticbeanstalk update-environment \
  --environment-name ${ENVIRONMENT_NAME} \
  --option-settings \
    Namespace=aws:autoscaling:asg,OptionName=MinSize,Value=0 \
    Namespace=aws:autoscaling:asg,OptionName=MaxSize,Value=0 \
  --profile ${STAGING_PROFILE}

echo "Environment is shutting down. This will take a few minutes."
echo "All EC2 instances will be terminated to save costs."
echo "To restart: ./scripts/start-staging-environment.sh"