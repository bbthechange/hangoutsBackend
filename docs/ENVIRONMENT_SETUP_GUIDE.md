# Environment Setup Guide

**This is the public documentation for the Inviter project environment architecture.**
**For actual resource IDs and credentials, see `.claude/ENVIRONMENT_DETAILS.md` (gitignored).**

Last Updated: 2026-01-21

---

## Overview

The Inviter project uses a **three-tier environment architecture**:

1. **Local Development** - Developer workstations
2. **Staging** - Separate AWS account for integration testing
3. **Production** - Separate AWS account for live application

This separation provides complete isolation between environments with independent billing and resource management.

---

## AWS Account Structure

### Production Account
- **Account ID**: `{PROD_ACCOUNT_ID}`
- **AWS CLI Profile**: `inviter`
- **Region**: `us-west-2`
- **Purpose**: Live production application

### Staging Account
- **Account ID**: `{STAGING_ACCOUNT_ID}`
- **AWS CLI Profile**: `inviter-staging`
- **Region**: `us-west-2`
- **Purpose**: Pre-production testing and validation

---

## Local Development Environment

### Prerequisites
- Java 21 (Corretto distribution)
- Node.js and npm (for Angular frontend)
- Gradle (included via wrapper)
- AWS CLI configured with production profile
- Docker (for integration tests with TestContainers)

### Configuration
- **Backend API**: http://localhost:8080
- **Angular Web**: http://localhost:4200
- **Spring Profile**: Default (none specified)
- **Database**: Connects to production DynamoDB tables
- **S3 Bucket**: Uses production bucket for images

### Setup Instructions

#### 1. Backend Setup
```bash
cd hangouts/hangoutsBackend

# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Access Swagger UI
open http://localhost:8080/swagger-ui.html
```

#### 2. Frontend Setup
```bash
cd hangouts/web

# Install dependencies
npm install

# Start development server
npm start

# Access application
open http://localhost:4200
```

#### 3. Running Tests
```bash
# Unit tests
./gradlew test

# Integration tests (requires Docker)
./gradlew integrationTest
```

### Local Test Account
- **Phone**: `{LOCAL_TEST_PHONE}`
- **Password**: `{LOCAL_TEST_PASSWORD}`
- **Note**: This account exists in production DynamoDB

---

## Staging Environment

### Architecture Overview

Staging mirrors production architecture but uses cost-optimized settings:
- Smaller instance types (t3.micro)
- Auto-scaling 0-2 instances (can scale to zero)
- On-demand DynamoDB billing
- Separate data stores

### Backend Infrastructure

#### Elastic Beanstalk
- **Application**: `{STAGING_EB_APP_NAME}`
- **Environment**: `{STAGING_EB_ENV_NAME}`
- **CNAME**: `{STAGING_EB_CNAME}.elasticbeanstalk.com`
- **Platform**: Corretto 21 on Amazon Linux 2023
- **Instance Type**: t3.micro
- **Auto-scaling**: Min 0, Max 2

#### API Gateway
- **Name**: `{STAGING_API_NAME}`
- **Endpoint**: `https://{STAGING_API_ID}.execute-api.us-west-2.amazonaws.com/prod`
- **Type**: HTTP Proxy to Elastic Beanstalk

#### CloudFront Distributions
1. **Backend API**: `{STAGING_API_CLOUDFRONT}.cloudfront.net`
2. **Frontend Web**: `{STAGING_WEB_CLOUDFRONT}.cloudfront.net`
3. **Image Assets**: `{STAGING_IMG_CLOUDFRONT}.cloudfront.net`

### Storage

#### S3 Buckets
- **Event Images**: `inviter-event-images-staging-{STAGING_ACCOUNT_ID}`
- **Web Assets**: `hangout-web-staging-{STAGING_ACCOUNT_ID}`

#### DynamoDB Tables
- `Users`
- `Events`
- `Invites`
- `Devices`
- `VerificationCodes`
- `InviterTable`

**Note**: Tables are auto-created by `DynamoDBTableInitializer` on first startup.

### Configuration (application-staging.properties)

Key staging-specific settings:
```properties
server.port=5000
aws.region=us-west-2
aws.s3.bucket=inviter-event-images-staging-{STAGING_ACCOUNT_ID}

# CloudFront endpoints
calendar.base-url=https://{STAGING_API_CLOUDFRONT}.cloudfront.net
app.base-url=https://{STAGING_WEB_CLOUDFRONT}.cloudfront.net

# Feature flags
apns.enabled=false              # Push notifications disabled
xray.enabled=true               # Tracing enabled
logging.level.com.bbthechange.inviter=DEBUG

# Development features
springdoc.swagger-ui.enabled=true
app.bypass-phone-verification=false
```

### Deployment

#### Web Frontend
```bash
cd hangouts/web
./deploy-staging.sh
```

This script:
1. Builds Angular app with staging configuration
2. Uploads to S3 with proper cache headers
3. Invalidates CloudFront distribution

#### Backend API
Automatically deployed via GitHub Actions when changes are pushed to `main` branch.

### Test Account (Staging)
- **Phone**: `{STAGING_TEST_PHONE}`
- **Password**: `{STAGING_TEST_PASSWORD}`
- **Note**: Separate from production test account

---

## Production Environment

### Architecture Overview

Production uses robust configuration for reliability and performance:
- Right-sized EC2 instances
- Auto-scaling based on load
- Standard DynamoDB billing
- APNs push notifications enabled
- X-Ray distributed tracing
- CloudWatch monitoring

### Backend Infrastructure

#### Elastic Beanstalk
- **Application**: `{PROD_EB_APP_NAME}`
- **Environment**: `{PROD_EB_ENV_NAME}`
- **CNAME**: `{PROD_EB_CNAME}.elasticbeanstalk.com`
- **Platform**: Corretto 21 on Amazon Linux 2023
- **Platform Version**: 4.6.1 (check for updates regularly)

#### API Gateway
- **Endpoint**: `https://{PROD_API_ID}.execute-api.us-west-2.amazonaws.com/prod`
- **Type**: HTTP Proxy to Elastic Beanstalk

#### CloudFront Distributions
1. **Backend API**: `{PROD_API_CLOUDFRONT}.cloudfront.net`
2. **Frontend Web**: `{PROD_WEB_CLOUDFRONT}.cloudfront.net`

### Storage

#### S3 Buckets
- **Event Images**: `inviter-event-images-{PROD_ACCOUNT_ID}`
- **Web Assets**: `hangout-web-prod`

#### DynamoDB Tables
- `Users`
- `Events`
- `Invites`
- `Devices`
- `VerificationCodes`
- `InviterTable`
- `PasswordResetRequest`

### Configuration (application-prod.properties)

Key production settings:
```properties
server.port=5000
aws.region=us-west-2
aws.s3.bucket=inviter-event-images-{PROD_ACCOUNT_ID}

# CloudFront endpoints
calendar.base-url=https://{PROD_API_CLOUDFRONT}.cloudfront.net
app.base-url=https://{PROD_WEB_CLOUDFRONT}.cloudfront.net

# Feature flags
apns.enabled=true               # Push notifications enabled
apns.production=false           # Using sandbox APNs (update for production)
xray.enabled=true               # Tracing enabled

# Production logging (memory-optimized)
logging.level.com.bbthechange.inviter=INFO
logging.level.root=WARN

# Monitoring
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

### AWS Systems Manager Parameters

Secrets are stored in SSM Parameter Store:
- `/inviter/apns/auth-key` (SecureString)
- `/inviter/ticketmaster/api-key` (SecureString)
- `/inviter/twilio/account-sid` (SecureString)
- `/inviter/twilio/verify-service-sid` (SecureString)
- `/inviter/twilio/auth-token` (SecureString)
- `/inviter/scheduler/internal-api-key` (SecureString) - API key for EventBridge Scheduler

### EventBridge Scheduler Infrastructure

Used for scheduling hangout reminder notifications:

- **Schedule Group**: `hangout-reminders` - Contains one-time schedules for reminder notifications
- **SQS Queues**:
  - `hangout-reminders` - Main queue for scheduler to send reminder messages (VisibilityTimeout=60s, Retention=24hr, LongPolling=20s)
  - `hangout-reminder-dlq` - Dead letter queue for failed schedule invocations
- **EventBridge Connection**: `hangout-api-connection` - API key authentication for internal endpoints (legacy, will be removed after SQS migration)
- **API Destination**: `hangout-reminder-api` - HTTP endpoint for EventBridge to invoke (legacy, will be removed after SQS migration)
- **IAM Role**: `EventBridgeSchedulerRole` - Allows EventBridge Scheduler to send messages to SQS queues

The EB EC2 role (`aws-elasticbeanstalk-ec2-role`) has the `HangoutSchedulerManagement` policy attached, allowing the application to create, update, delete, and get schedules in the `hangout-reminders` group.

### TV Watch Party Infrastructure

Used for automatic TVMaze polling and episode update processing:

- **SQS Queues**:
  - `watch-party-tvmaze-updates` - Receives SHOW_UPDATED messages from polling (VisibilityTimeout=300s)
  - `watch-party-tvmaze-updates-dlq` - Dead letter queue for failed processing
  - `watch-party-episode-actions` - Receives NEW_EPISODE, UPDATE_TITLE, REMOVE_EPISODE messages
  - `watch-party-episode-actions-dlq` - Dead letter queue for failed processing
- **EventBridge Rule**: `watch-party-tvmaze-poll` - Triggers polling every 2 hours via API Destination
- **API Destination**: `watch-party-poll-api` - Invokes `/internal/watch-party/trigger-poll` endpoint
- **Connection**: Uses existing `hangout-api-connection` with X-Api-Key authentication

The EB EC2 role has `WatchPartyQueues` and `InternalApiKeyAccess` policies attached for SQS access and SSM parameter retrieval.

### IAM Roles
- `aws-elasticbeanstalk-ec2-role` - Instance profile for EC2 instances (includes HangoutSchedulerManagement policy)
- `aws-elasticbeanstalk-service-role` - Service role for EB operations
- `EventBridgeSchedulerRole` - Role for EventBridge Scheduler to invoke API destinations

### Deployment

#### Web Frontend
```bash
cd hangouts/web
./deploy-prod.sh
```

#### Backend API
Automatically deployed via GitHub Actions:
1. Push to `main` branch triggers workflow
2. Build and test phase
3. Deploy to staging with health checks
4. Deploy to production with health checks

---

## iOS Application Configuration

### Build Configurations

The iOS app uses Xcode configuration files (`.xcconfig`) to manage environment settings.

#### Debug Configuration (Staging)
**File**: `hangouts/ios/Debug.xcconfig`
```
S3_BUCKET = inviter-event-images-staging-{STAGING_ACCOUNT_ID}
S3_REGION = us-west-2
CLOUDFRONT_DOMAIN = {STAGING_IMG_CLOUDFRONT}.cloudfront.net
S3_ACCESS_KEY = "{STAGING_S3_ACCESS_KEY}"
S3_SECRET_KEY = "{STAGING_S3_SECRET_KEY}"
```

#### Release Configuration (Production)
**File**: `hangouts/ios/Release.xcconfig`
```
S3_BUCKET = inviter-event-images-{PROD_ACCOUNT_ID}
S3_REGION = us-west-2
CLOUDFRONT_DOMAIN = {PROD_IMG_CLOUDFRONT}.cloudfront.net
S3_ACCESS_KEY = "{PROD_S3_ACCESS_KEY}"
S3_SECRET_KEY = "{PROD_S3_SECRET_KEY}"
```

#### API Configuration
**File**: `hangouts/ios/api-config.txt`
```
API_BASE_URL=https://{PROD_API_ID}.execute-api.us-west-2.amazonaws.com/prod
```

### APNs Configuration
- **Key ID**: `{APNS_KEY_ID}`
- **Team ID**: `{APNS_TEAM_ID}`
- **Bundle ID**: `com.unmedia.hangouts`
- **Key Storage**: AWS SSM Parameter `/inviter/apns/auth-key`
- **Environment**: Sandbox (both staging and production use sandbox currently)

**Note**: For production release, update `APNS_PRODUCTION=true` in application-prod.properties.

---

## GitHub Actions CI/CD Pipeline

### Workflow Configuration

**File**: `.github/workflows/backend-ci-cd.yml`

### Pipeline Stages

#### 1. Build Job
- Checks out code
- Sets up JDK 21 (Temurin distribution)
- Runs `./gradlew build` (compiles and tests)
- Renames JAR: `inviter-0.0.1-SNAPSHOT.jar` → `application.jar`
- Uploads entire project as build artifact

#### 2. Deploy to Staging
**Triggers**: After successful build
**Environment**: GitHub environment named `staging`

Steps:
1. Download build artifacts
2. Create deployment zip package
3. Deploy to Elastic Beanstalk using `einaregilsson/beanstalk-deploy@v21`
4. Wait up to 60 seconds for environment recovery
5. Perform health check (3 retries with 10-second delays)

#### 3. Deploy to Production
**Triggers**: After successful staging deployment
**Condition**: Only on `main` branch
**Environment**: GitHub environment named `Prod`

Steps:
1. Download build artifacts
2. Create deployment zip package
3. Deploy to Elastic Beanstalk
4. Wait up to 60 seconds for environment recovery
5. Perform health check (3 retries with 10-second delays)

### Required GitHub Secrets

Configure these in your GitHub repository settings (Settings → Secrets and variables → Actions):

```
AWS_ACCESS_KEY_ID          # AWS credentials for deployment user
AWS_SECRET_ACCESS_KEY      # AWS credentials for deployment user
AWS_REGION                 # us-west-2

# Staging
EB_APP_NAME_STAGING        # Elastic Beanstalk application name
EB_ENV_NAME_STAGING        # Elastic Beanstalk environment name
STAGING_URL                # Health check URL (EB or API Gateway)

# Production
EB_APP_NAME_PROD           # Elastic Beanstalk application name
EB_ENV_NAME_PROD           # Elastic Beanstalk environment name
```

### Version Labeling

Versions follow the format: `v{run_number}-{commit_sha}`

Example: `v95-6f8eb403db02cb9154ff5136279227d9b178f939`

This allows tracking which git commit is deployed in each environment.

---

## Service Availability Matrix

| Service | Local | Staging | Production |
|---------|-------|---------|------------|
| **Backend API** | ✅ Port 8080 | ✅ EB + API Gateway | ✅ EB + API Gateway |
| **Web Frontend** | ✅ Port 4200 | ✅ CloudFront | ✅ CloudFront |
| **DynamoDB** | ✅ Uses prod | ✅ Separate tables | ✅ Separate tables |
| **S3 Images** | ✅ Uses prod | ✅ Staging bucket | ✅ Prod bucket |
| **CloudFront CDN** | ❌ | ✅ 3 distributions | ✅ 3 distributions |
| **APNs Push** | ❌ Disabled | ❌ Disabled | ✅ Enabled (sandbox) |
| **X-Ray Tracing** | ❌ Disabled | ✅ Enabled | ✅ Enabled |
| **Swagger UI** | ✅ Enabled | ✅ Enabled | ⚠️ Should disable |
| **Actuator** | ✅ All endpoints | ❌ Limited | ✅ Selected |
| **Twilio SMS** | ✅ | ✅ | ✅ |
| **Ticketmaster API** | ✅ | ✅ | ✅ |
| **EventBridge Scheduler** | ❌ | ✅ Configured | ✅ Configured |
| **Watch Party SQS** | ❌ | ✅ 4 queues | ✅ 4 queues |
| **Watch Party Polling** | ❌ | ✅ EventBridge Rule (2hr) | ✅ EventBridge Rule (2hr) |
| **Watch Party Host Check** | ❌ | ✅ @Scheduled (10am UTC) | ✅ @Scheduled (10am UTC) |

---

## Environment Setup Procedures

### Setting Up a New Staging Environment

If you need to recreate the staging environment from scratch:

#### 1. AWS Account Setup
1. Create new AWS account for staging
2. Enable MFA on root account
3. Create IAM admin user
4. Configure AWS CLI profile: `aws configure --profile inviter-staging`

#### 2. Infrastructure Setup
1. Create S3 buckets:
   - `inviter-event-images-staging-{ACCOUNT_ID}`
   - `hangout-web-staging-{ACCOUNT_ID}`

2. Create IAM roles:
   - `aws-elasticbeanstalk-ec2-role`
   - `aws-elasticbeanstalk-service-role`

3. Create Elastic Beanstalk application and environment
4. Set up API Gateway (HTTP proxy to EB)
5. Create CloudFront distributions
6. Configure SSM parameters for secrets

7. Set up EventBridge Scheduler infrastructure:
   ```bash
   # Create schedule group
   aws scheduler create-schedule-group --name hangout-reminders

   # Create SQS queues
   aws sqs create-queue --queue-name hangout-reminders \
     --attributes '{
       "VisibilityTimeout": "60",
       "MessageRetentionPeriod": "86400",
       "ReceiveMessageWaitTimeSeconds": "20"
     }'

   aws sqs create-queue --queue-name hangout-reminder-dlq

   # Create EventBridgeSchedulerRole IAM role with policy:
   # {
   #   "Version": "2012-10-17",
   #   "Statement": [
   #     {
   #       "Sid": "SendToSQS",
   #       "Effect": "Allow",
   #       "Action": "sqs:SendMessage",
   #       "Resource": [
   #         "arn:aws:sqs:us-west-2:{ACCOUNT_ID}:hangout-reminders",
   #         "arn:aws:sqs:us-west-2:{ACCOUNT_ID}:hangout-reminder-dlq"
   #       ]
   #     }
   #   ]
   # }

   # Add HangoutSchedulerManagement policy to aws-elasticbeanstalk-ec2-role
   ```

8. Set up TV Watch Party infrastructure:
   ```bash
   # Create SQS queues for watch party
   aws sqs create-queue --queue-name watch-party-tvmaze-updates \
     --attributes '{
       "VisibilityTimeout": "300",
       "MessageRetentionPeriod": "345600",
       "ReceiveMessageWaitTimeSeconds": "0"
     }'
   aws sqs create-queue --queue-name watch-party-tvmaze-updates-dlq
   aws sqs create-queue --queue-name watch-party-episode-actions \
     --attributes '{
       "VisibilityTimeout": "120",
       "MessageRetentionPeriod": "345600"
     }'
   aws sqs create-queue --queue-name watch-party-episode-actions-dlq

   # Configure redrive policies for DLQs
   # (See .claude/ENVIRONMENT_DETAILS.md for specific ARNs)

   # Create API Destination for polling endpoint
   aws events create-api-destination \
     --name watch-party-poll-api \
     --connection-arn "{CONNECTION_ARN}" \
     --invocation-endpoint "https://{API_GATEWAY}/internal/watch-party/trigger-poll" \
     --http-method POST \
     --invocation-rate-limit-per-second 1

   # Create EventBridge Rule for 2-hour polling
   aws events put-rule \
     --name watch-party-tvmaze-poll \
     --schedule-expression "rate(2 hours)" \
     --state ENABLED \
     --description "Triggers TVMaze polling every 2 hours"

   # Attach API Destination target to rule
   aws events put-targets \
     --rule watch-party-tvmaze-poll \
     --targets '[{
       "Id": "watch-party-poll-target",
       "Arn": "{API_DESTINATION_ARN}",
       "RoleArn": "{EVENTBRIDGE_ROLE_ARN}",
       "RetryPolicy": {
         "MaximumEventAgeInSeconds": 3600,
         "MaximumRetryAttempts": 3
       }
     }]'

   # Update IAM trust policy to allow events.amazonaws.com
   # Update IAM permissions to allow InvokeApiDestination on watch-party-poll-api
   # Add WatchPartyQueues policy to aws-elasticbeanstalk-ec2-role
   # Add InternalApiKeyAccess policy to aws-elasticbeanstalk-ec2-role
   ```

#### 3. Application Deployment
1. Update `application-staging.properties` with correct resource names
2. Deploy backend: Push to `main` triggers GitHub Actions
3. Deploy frontend: Run `./deploy-staging.sh`

#### 4. Validation
1. Check EB environment health: `aws elasticbeanstalk describe-environments`
2. Test health endpoint: `curl https://{API_GATEWAY}/health`
3. Verify DynamoDB tables were auto-created
4. Test S3 image uploads

### Setting Up Local Development

#### 1. Prerequisites
```bash
# Install Java 21
brew install openjdk@21

# Install Node.js
brew install node

# Install AWS CLI
brew install awscli

# Configure AWS profile for production
aws configure --profile inviter
```

#### 2. Clone and Build
```bash
# Clone repository
git clone {REPO_URL}
cd hangouts/hangoutsBackend

# Build
./gradlew build

# Run tests
./gradlew test
```

#### 3. Environment Configuration
1. Ensure AWS credentials are configured (uses production resources)
2. No local `.env` file needed (uses application.properties defaults)
3. Start backend: `./gradlew bootRun`
4. Start frontend: `cd ../web && npm start`

---

## Common Operations

### Checking Environment Status

```bash
# Production
aws elasticbeanstalk describe-environments \
  --environment-names {PROD_EB_ENV_NAME} \
  --profile inviter \
  --region us-west-2

# Staging
aws elasticbeanstalk describe-environments \
  --environment-names {STAGING_EB_ENV_NAME} \
  --profile inviter-staging \
  --region us-west-2
```

### Viewing Logs

```bash
# Using EB CLI
eb logs --profile inviter                    # Production
eb logs --profile inviter-staging            # Staging

# Using AWS CLI
aws elasticbeanstalk request-environment-info \
  --environment-name {ENV_NAME} \
  --info-type tail \
  --profile {PROFILE}
```

### Testing Health Endpoints

```bash
# Production
curl https://{PROD_API_ID}.execute-api.us-west-2.amazonaws.com/prod/health

# Staging
curl https://{STAGING_API_ID}.execute-api.us-west-2.amazonaws.com/prod/health

# Local
curl http://localhost:8080/health
```

### Manual Deployment

#### Backend (if not using GitHub Actions)
```bash
# Build
./gradlew clean build

# Copy JAR
cp build/libs/inviter-0.0.1-SNAPSHOT.jar ./application.jar

# Deploy to production
eb deploy --profile inviter

# Deploy to staging
eb deploy --profile inviter-staging
```

#### Frontend
```bash
cd hangouts/web

# Staging
./deploy-staging.sh

# Production
./deploy-prod.sh
```

### Invalidating CloudFront Cache

```bash
aws cloudfront create-invalidation \
  --distribution-id {DISTRIBUTION_ID} \
  --paths "/*" \
  --profile {PROFILE}
```

---

## Known Issues & Considerations

### Configuration Gaps
1. **Staging Missing Table**: `PasswordResetRequest` table doesn't exist in staging DynamoDB
   - **Impact**: Password reset functionality may fail in staging
   - **Fix**: Manually create table or trigger auto-creation via API call

2. **Platform Version**: EB platform version has updates available
   - **Impact**: Missing latest security patches
   - **Fix**: Update platform version during maintenance window

3. **Web Frontend CI/CD**: No automated deployment for frontend
   - **Impact**: Manual deployment required
   - **Fix**: Consider adding GitHub Actions workflow for web deployments

### Security Considerations

1. **Swagger UI in Production**: Should be disabled
   - Check `springdoc.swagger-ui.enabled` in application-prod.properties
   - Exposes API documentation publicly

2. **S3 Access Keys in iOS Config**: Hardcoded credentials
   - Consider using IAM roles with AWS Cognito Identity Pools
   - Or use STS temporary credentials

3. **APNs Sandbox Mode**: Both environments use sandbox
   - Production should use production APNs certificates
   - Update `APNS_PRODUCTION=true` when ready

### Cost Optimization (Staging)

Staging environment includes cost-saving features:
- **Auto-scaling 0-2 instances**: Can scale to zero when not in use
- **t3.micro instances**: Smallest production-capable instance type
- **On-demand DynamoDB**: Pay only for actual usage
- **S3 lifecycle policies**: Auto-delete test data after 30 days

To minimize costs:
```bash
# Scale staging to zero instances
aws elasticbeanstalk update-environment \
  --environment-name {STAGING_EB_ENV_NAME} \
  --option-settings \
    Namespace=aws:autoscaling:asg,OptionName=MinSize,Value=0 \
    Namespace=aws:autoscaling:asg,OptionName=MaxSize,Value=0 \
  --profile inviter-staging
```

---

## Troubleshooting

### Deployment Failures

**Symptom**: GitHub Actions deployment fails with timeout
**Cause**: Application taking too long to start
**Solution**:
1. Check EB logs: `eb logs --profile {PROFILE}`
2. Look for startup errors in application logs
3. Verify all SSM parameters are set
4. Check security group allows EB health checks

### Database Connection Issues

**Symptom**: Application can't connect to DynamoDB
**Cause**: IAM role missing permissions or tables don't exist
**Solution**:
1. Verify IAM role has DynamoDB permissions
2. Check tables exist: `aws dynamodb list-tables --profile {PROFILE}`
3. Check application logs for specific error messages

### S3 Upload Failures

**Symptom**: Image uploads fail with access denied
**Cause**: Bucket policy or IAM permissions
**Solution**:
1. Verify bucket exists: `aws s3 ls --profile {PROFILE}`
2. Check bucket policy allows EC2 role access
3. Verify presigned URL generation is working

### CloudFront Cache Issues

**Symptom**: Old content still being served
**Cause**: CloudFront cache not invalidated
**Solution**:
```bash
aws cloudfront create-invalidation \
  --distribution-id {DISTRIBUTION_ID} \
  --paths "/*" \
  --profile {PROFILE}
```

---

## Additional Documentation

### Project Documentation
- **Feature Context Files**: See `context/` directory for feature-specific documentation
- **API Documentation**: Available at `/swagger-ui.html` in each environment
- **Database Architecture**: See `DATABASE_ARCHITECTURE_CRITICAL.md`
- **Attribute Addition Guide**: See `context/ATTRIBUTE_ADDITION_GUIDE.md`

### External Resources
- [AWS Elastic Beanstalk Documentation](https://docs.aws.amazon.com/elasticbeanstalk/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Angular Documentation](https://angular.io/docs)
- [DynamoDB Best Practices](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/best-practices.html)

---

**For actual resource identifiers, account numbers, and credentials, refer to:**
`.claude/ENVIRONMENT_DETAILS.md` (gitignored, not in version control)

**Last Updated**: 2026-01-21
