# AWS API Gateway Setup Guide

This guide covers setting up AWS API Gateway to provide HTTPS endpoints for the Inviter Spring Boot application.

## Overview

API Gateway will act as a reverse proxy, providing:
- Automatic HTTPS with AWS-managed SSL certificates
- Custom domain support
- Rate limiting and throttling
- Request/response transformation
- Centralized logging and monitoring

## Architecture

```
Client (HTTPS) → API Gateway → Elastic Beanstalk (HTTP) → Spring Boot App
```

The Spring Boot application remains on HTTP (port 5000) behind API Gateway, which handles SSL termination.

## Setup Steps

### 1. Create API Gateway REST API

1. **Create API**:
   ```bash
   aws apigateway create-rest-api \
     --name "inviter-api" \
     --description "Inviter REST API Gateway" \
     --region us-west-2 \
     --profile inviter
   ```

2. **Get Root Resource ID**:
   ```bash
   aws apigateway get-resources \
     --rest-api-id YOUR_API_ID \
     --region us-west-2 \
     --profile inviter
   ```

### 2. Configure Proxy Resource

1. **Create Proxy Resource**:
   ```bash
   aws apigateway create-resource \
     --rest-api-id YOUR_API_ID \
     --parent-id ROOT_RESOURCE_ID \
     --path-part "{proxy+}" \
     --region us-west-2 \
     --profile inviter
   ```

2. **Create ANY Method**:
   ```bash
   aws apigateway put-method \
     --rest-api-id YOUR_API_ID \
     --resource-id PROXY_RESOURCE_ID \
     --http-method ANY \
     --authorization-type NONE \
     --region us-west-2 \
     --profile inviter
   ```

3. **Configure Integration**:
   ```bash
   aws apigateway put-integration \
     --rest-api-id YOUR_API_ID \
     --resource-id PROXY_RESOURCE_ID \
     --http-method ANY \
     --type HTTP_PROXY \
     --integration-http-method ANY \
     --uri "http://inviter-test.eba-meudu6bv.us-west-2.elasticbeanstalk.com/{proxy}" \
     --region us-west-2 \
     --profile inviter
   ```

### 3. Deploy API

1. **Create Deployment**:
   ```bash
   aws apigateway create-deployment \
     --rest-api-id YOUR_API_ID \
     --stage-name prod \
     --region us-west-2 \
     --profile inviter
   ```

2. **Test Default Endpoint**:
   ```
   https://YOUR_API_ID.execute-api.us-west-2.amazonaws.com/prod/
   ```

### 4. Custom Domain Setup

1. **Request SSL Certificate** (if not already done):
   ```bash
   aws acm request-certificate \
     --domain-name api.inviter.app \
     --validation-method DNS \
     --region us-west-2 \
     --profile inviter
   ```

2. **Create Domain Name**:
   ```bash
   aws apigateway create-domain-name \
     --domain-name api.inviter.app \
     --certificate-arn arn:aws:acm:us-west-2:ACCOUNT:certificate/CERT_ID \
     --region us-west-2 \
     --profile inviter
   ```

3. **Create Base Path Mapping**:
   ```bash
   aws apigateway create-base-path-mapping \
     --domain-name api.inviter.app \
     --rest-api-id YOUR_API_ID \
     --stage prod \
     --region us-west-2 \
     --profile inviter
   ```

4. **Update DNS** (Route 53 or your DNS provider):
   - Create CNAME record pointing `api.inviter.app` to the API Gateway domain name

### 5. Update Application Configuration

The following changes have already been made to prepare for API Gateway:

1. **CORS Configuration** (`SecurityConfig.java`):
   ```java
   configuration.setAllowedOrigins(Arrays.asList(
       "http://localhost:3000", // Local development
       "http://localhost:8080", // Swagger UI
       "https://d3lm7si4v7xvcj.cloudfront.net", // Production CloudFront domain
       "https://api.inviter.app" // API Gateway domain
   ));
   ```

2. **Application Properties**:
   - Removed HTTPS configuration
   - Application runs on HTTP (port 5000 in production)

## Testing

1. **Health Check**:
   ```bash
   curl https://api.inviter.app/health
   ```

2. **API Endpoints**:
   ```bash
   # Login
   curl -X POST https://api.inviter.app/auth/login \
     -H "Content-Type: application/json" \
     -d '{"phoneNumber": "YOUR_PHONE", "password": "YOUR_PASSWORD"}'
   
   # Get events (with JWT token)
   curl https://api.inviter.app/events \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"
   ```

## Configuration Options

### Rate Limiting
```bash
aws apigateway create-usage-plan \
  --name "inviter-usage-plan" \
  --throttle burst-limit=100,rate-limit=50 \
  --quota limit=10000,period=MONTH \
  --region us-west-2 \
  --profile inviter
```

### Request Validation
Add request validation templates to validate incoming requests before they reach your backend.

### Response Transformation
Configure response templates to modify API responses if needed.

## Monitoring

- **CloudWatch Logs**: API Gateway automatically logs requests
- **CloudWatch Metrics**: Monitor request count, latency, and errors
- **X-Ray Tracing**: Enable for detailed request tracing

## Security Considerations

1. **API Keys**: Consider requiring API keys for additional security
2. **WAF Integration**: Add AWS WAF for additional protection
3. **VPC Link**: For enhanced security, consider using VPC Link to keep traffic within AWS network
4. **Throttling**: Configure appropriate throttling limits

## Cost Optimization

- **Caching**: Enable response caching to reduce backend calls
- **Edge Optimized**: Use edge-optimized endpoints for global distribution
- **Regional**: Use regional endpoints if traffic is primarily from one region

## Troubleshooting

### Common Issues

1. **502 Bad Gateway**: Check Elastic Beanstalk health and application logs
2. **CORS Errors**: Verify CORS configuration in Spring Boot application
3. **SSL Certificate**: Ensure certificate is validated and deployed
4. **DNS**: Verify CNAME record is correctly configured

### Debugging Commands

```bash
# Check API Gateway logs
aws logs describe-log-groups \
  --log-group-name-prefix API-Gateway-Execution-Logs \
  --region us-west-2 \
  --profile inviter

# Test backend directly
curl http://inviter-test.eba-meudu6bv.us-west-2.elasticbeanstalk.com/health
```

## Next Steps

1. Set up API Gateway as described above
2. Update your frontend application to use `https://api.inviter.app` instead of the direct Elastic Beanstalk URL
3. Configure monitoring and alerting
4. Consider implementing API versioning for future updates