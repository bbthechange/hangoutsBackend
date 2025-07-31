#!/bin/bash

# Staging Infrastructure Setup Script for AWS Account 575960429871
# Assumes AWS_PROFILE=inviter-staging is configured

set -e

echo "Setting up Inviter staging infrastructure..."

# Variables
ACCOUNT_ID="575960429871"
REGION="us-west-2"
BUCKET_NAME="inviter-event-images-staging-${ACCOUNT_ID}"
EB_SERVICE_ROLE="aws-elasticbeanstalk-service-role"
EB_INSTANCE_ROLE="eb-inviter-staging-role"

echo "Using AWS Account: ${ACCOUNT_ID}"
echo "Region: ${REGION}"
echo "S3 Bucket: ${BUCKET_NAME}"

# 1. Create S3 Bucket
echo "Creating S3 bucket: ${BUCKET_NAME}"
if aws s3 mb "s3://${BUCKET_NAME}" --region "${REGION}"; then
    echo "✓ S3 bucket created successfully"
else
    echo "⚠ S3 bucket may already exist or creation failed"
fi

# Configure S3 bucket CORS
echo "Configuring S3 bucket CORS..."
cat > /tmp/cors-config.json << EOF
{
    "CORSRules": [
        {
            "AllowedHeaders": ["*"],
            "AllowedMethods": ["GET", "PUT", "POST"],
            "AllowedOrigins": [
                "http://localhost:3000",
                "https://*.execute-api.us-west-2.amazonaws.com",
                "https://staging.inviter.app"
            ],
            "ExposeHeaders": []
        }
    ]
}
EOF

aws s3api put-bucket-cors --bucket "${BUCKET_NAME}" --cors-configuration file:///tmp/cors-config.json
echo "✓ S3 CORS configuration applied"

# 2. Create IAM roles for Elastic Beanstalk
echo "Creating IAM roles for Elastic Beanstalk..."

# Create service role if it doesn't exist
if ! aws iam get-role --role-name "${EB_SERVICE_ROLE}" >/dev/null 2>&1; then
    echo "Creating EB service role: ${EB_SERVICE_ROLE}"
    
    cat > /tmp/eb-service-trust-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "elasticbeanstalk.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
EOF

    aws iam create-role \
        --role-name "${EB_SERVICE_ROLE}" \
        --assume-role-policy-document file:///tmp/eb-service-trust-policy.json

    # Attach managed policies
    aws iam attach-role-policy \
        --role-name "${EB_SERVICE_ROLE}" \
        --policy-arn "arn:aws:iam::aws:policy/service-role/AWSElasticBeanstalkEnhancedHealth"
    
    aws iam attach-role-policy \
        --role-name "${EB_SERVICE_ROLE}" \
        --policy-arn "arn:aws:iam::aws:policy/AWSElasticBeanstalkManagedUpdatesCustomerRolePolicy"
    
    echo "✓ EB service role created"
else
    echo "✓ EB service role already exists"
fi

# Create instance role
if ! aws iam get-role --role-name "${EB_INSTANCE_ROLE}" >/dev/null 2>&1; then
    echo "Creating EB instance role: ${EB_INSTANCE_ROLE}"
    
    cat > /tmp/eb-instance-trust-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "ec2.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
EOF

    aws iam create-role \
        --role-name "${EB_INSTANCE_ROLE}" \
        --assume-role-policy-document file:///tmp/eb-instance-trust-policy.json

    # Attach managed policies
    aws iam attach-role-policy \
        --role-name "${EB_INSTANCE_ROLE}" \
        --policy-arn "arn:aws:iam::aws:policy/AWSElasticBeanstalkWebTier"
    
    aws iam attach-role-policy \
        --role-name "${EB_INSTANCE_ROLE}" \
        --policy-arn "arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess"
    
    aws iam attach-role-policy \
        --role-name "${EB_INSTANCE_ROLE}" \
        --policy-arn "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess"

    # Add custom S3 write policy for the staging bucket
    cat > /tmp/s3-write-policy.json << EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:PutObjectAcl",
                "s3:DeleteObject"
            ],
            "Resource": "arn:aws:s3:::${BUCKET_NAME}/*"
        }
    ]
}
EOF

    aws iam put-role-policy \
        --role-name "${EB_INSTANCE_ROLE}" \
        --policy-name "S3StagingBucketAccess" \
        --policy-document file:///tmp/s3-write-policy.json

    echo "✓ EB instance role created with DynamoDB and S3 permissions"
else
    echo "✓ EB instance role already exists"
fi

# Create instance profile
if ! aws iam get-instance-profile --instance-profile-name "${EB_INSTANCE_ROLE}" >/dev/null 2>&1; then
    echo "Creating instance profile: ${EB_INSTANCE_ROLE}"
    aws iam create-instance-profile --instance-profile-name "${EB_INSTANCE_ROLE}"
    aws iam add-role-to-instance-profile \
        --instance-profile-name "${EB_INSTANCE_ROLE}" \
        --role-name "${EB_INSTANCE_ROLE}"
    echo "✓ Instance profile created"
else
    echo "✓ Instance profile already exists"
fi

# 3. Upload predefined images to S3 (if they exist locally)
echo "Checking for predefined images to upload..."
if [ -d "predefined-images" ]; then
    echo "Uploading predefined images to S3..."
    aws s3 sync predefined-images/ "s3://${BUCKET_NAME}/predefined/" --delete
    echo "✓ Predefined images uploaded"
else
    echo "⚠ No predefined-images directory found. You'll need to upload these manually."
fi

# 4. Create Elastic Beanstalk application
echo "Creating Elastic Beanstalk application..."
if ! aws elasticbeanstalk describe-applications --application-names "inviter-app-staging" >/dev/null 2>&1; then
    aws elasticbeanstalk create-application \
        --application-name "inviter-app-staging" \
        --description "Inviter API Staging Environment"
    echo "✓ EB application created"
else
    echo "✓ EB application already exists"
fi

# Clean up temporary files
rm -f /tmp/cors-config.json /tmp/eb-service-trust-policy.json /tmp/eb-instance-trust-policy.json /tmp/s3-write-policy.json

echo ""
echo "🎉 Staging infrastructure setup completed!"
echo ""
echo "Next steps:"
echo "1. Switch to staging branch: git checkout staging"
echo "2. Build the application: ./gradlew clean build"
echo "3. Copy JAR: cp build/libs/inviter-0.0.1-SNAPSHOT.jar ./application.jar"
echo "4. Deploy to EB: AWS_PROFILE=inviter-staging eb create inviter-staging"
echo ""
echo "Resources created:"
echo "- S3 Bucket: ${BUCKET_NAME}"
echo "- IAM Role: ${EB_SERVICE_ROLE}"
echo "- IAM Role: ${EB_INSTANCE_ROLE}"
echo "- EB Application: inviter-app-staging"