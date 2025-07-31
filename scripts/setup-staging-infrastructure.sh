#!/bin/bash
set -e

STAGING_PROFILE="inviter-staging"
REGION="us-west-2"
PROD_ACCOUNT="871070087012"

# Safety check: Verify we're using the staging account
echo "Verifying AWS account and profile..."
STAGING_ACCOUNT_ID=$(aws sts get-caller-identity --profile $STAGING_PROFILE --query Account --output text 2>/dev/null || echo "ERROR")

if [ "$STAGING_ACCOUNT_ID" = "ERROR" ]; then
    echo "❌ ERROR: Unable to verify AWS account. Check if profile '${STAGING_PROFILE}' is configured."
    echo "Run: aws configure --profile ${STAGING_PROFILE}"
    exit 1
fi

if [ "$STAGING_ACCOUNT_ID" = "$PROD_ACCOUNT" ]; then
    echo "❌ ERROR: Current account ($STAGING_ACCOUNT_ID) is the PRODUCTION account!"
    echo "This script is designed for staging environment only."
    echo "Please configure a separate AWS account for staging with profile: ${STAGING_PROFILE}"
    exit 1
fi

echo "✅ Using staging account: ${STAGING_ACCOUNT_ID}"
echo "✅ Using profile: ${STAGING_PROFILE}"

S3_BUCKET="inviter-event-images-staging-${STAGING_ACCOUNT_ID}"

echo "Setting up staging infrastructure in account: ${STAGING_ACCOUNT_ID}"

# 1. Create S3 Bucket
echo "Creating S3 bucket: ${S3_BUCKET}"
aws s3 mb s3://${S3_BUCKET} --region ${REGION} --profile ${STAGING_PROFILE}

# Set lifecycle policy for cost optimization
cat > s3-lifecycle-policy.json << EOF
{
    "Rules": [
        {
            "ID": "StagingCleanup",
            "Status": "Enabled",
            "Filter": {"Prefix": "events/"},
            "Expiration": {"Days": 30}
        }
    ]
}
EOF

aws s3api put-bucket-lifecycle-configuration \
  --bucket ${S3_BUCKET} \
  --lifecycle-configuration file://s3-lifecycle-policy.json \
  --profile ${STAGING_PROFILE}

# 2. Copy predefined images from production (requires prod profile access)
echo "Copying predefined images from production..."
aws s3 sync s3://inviter-event-images-871070087012/predefined/ s3://${S3_BUCKET}/predefined/ \
  --source-region us-west-2 --region ${REGION} \
  --profile inviter # Use prod profile to read

# Switch to staging profile for destination operations
aws s3api put-bucket-policy --bucket ${S3_BUCKET} --profile ${STAGING_PROFILE} --policy '{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AllowEBInstanceAccess",
            "Effect": "Allow",
            "Principal": {"AWS": "arn:aws:iam::'${STAGING_ACCOUNT_ID}':role/aws-elasticbeanstalk-ec2-role"},
            "Action": ["s3:GetObject", "s3:PutObject", "s3:PutObjectAcl"],
            "Resource": "arn:aws:s3:::'${S3_BUCKET}'/*"
        }
    ]
}'

# 3. Create IAM roles for Elastic Beanstalk
echo "Creating IAM roles..."
aws iam create-role --role-name aws-elasticbeanstalk-ec2-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ec2.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }' --profile ${STAGING_PROFILE} || echo "Role already exists"

aws iam create-role --role-name aws-elasticbeanstalk-service-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "elasticbeanstalk.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }' --profile ${STAGING_PROFILE} || echo "Role already exists"

# Attach managed policies
aws iam attach-role-policy --role-name aws-elasticbeanstalk-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AWSElasticBeanstalkWebTier --profile ${STAGING_PROFILE}
aws iam attach-role-policy --role-name aws-elasticbeanstalk-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess --profile ${STAGING_PROFILE}
aws iam attach-role-policy --role-name aws-elasticbeanstalk-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess --profile ${STAGING_PROFILE}

aws iam attach-role-policy --role-name aws-elasticbeanstalk-service-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSElasticBeanstalkEnhancedHealth --profile ${STAGING_PROFILE}
aws iam attach-role-policy --role-name aws-elasticbeanstalk-service-role \
  --policy-arn arn:aws:iam::aws:policy/AWSElasticBeanstalkManagedUpdatesCustomerRolePolicy --profile ${STAGING_PROFILE}
aws iam attach-role-policy --role-name aws-elasticbeanstalk-service-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSElasticBeanstalkService --profile ${STAGING_PROFILE}
aws iam attach-role-policy --role-name aws-elasticbeanstalk-service-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSElasticBeanstalkServiceRolePolicy --profile ${STAGING_PROFILE}

# Create instance profile
aws iam create-instance-profile --instance-profile-name aws-elasticbeanstalk-ec2-role --profile ${STAGING_PROFILE} || echo "Instance profile exists"
aws iam add-role-to-instance-profile --instance-profile-name aws-elasticbeanstalk-ec2-role \
  --role-name aws-elasticbeanstalk-ec2-role --profile ${STAGING_PROFILE} || echo "Role already added"

# Create custom S3 policy for staging bucket
aws iam put-role-policy --role-name aws-elasticbeanstalk-ec2-role \
  --policy-name S3StagingBucketAccess \
  --policy-document '{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": ["s3:PutObject", "s3:PutObjectAcl"],
            "Resource": "arn:aws:s3::'${S3_BUCKET}'/*"
        }
    ]
  }' --profile ${STAGING_PROFILE}

# Wait for IAM roles to propagate before creating EB resources
echo "Waiting 120 seconds for IAM roles to propagate..."
sleep 120

# 4. Initialize Elastic Beanstalk application
echo "Creating Elastic Beanstalk application..."
aws elasticbeanstalk create-application \
  --application-name inviter-app-staging \
  --description "Inviter staging environment" \
  --region ${REGION} --profile ${STAGING_PROFILE} || echo "Application exists"

echo "Infrastructure setup complete!"
echo "S3 Bucket: ${S3_BUCKET}"
echo "Account ID: ${STAGING_ACCOUNT_ID}"