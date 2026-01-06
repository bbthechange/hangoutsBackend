# Metrics & Monitoring Context

## Overview
Application metrics are collected via Micrometer and scraped by Grafana Alloy, which forwards them to Grafana Cloud free tier for visualization and alerting.

## Architecture
```
Spring Boot App (/actuator/prometheus) → Grafana Alloy (localhost:5000) → Grafana Cloud
```

## Adding New Metrics

### Pattern: Inject MeterRegistry directly
Follow the pattern in `QueryPerformanceTracker.java`:

```java
@Service
public class MyService {
    private final MeterRegistry meterRegistry;

    public MyService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void doSomething() {
        // Counter
        meterRegistry.counter("my_operation_total", "status", "success").increment();

        // Timer
        Timer.Sample sample = Timer.start(meterRegistry);
        // ... do work ...
        sample.stop(Timer.builder("my_operation_duration")
            .tag("type", "query")
            .register(meterRegistry));
    }
}
```

### Metric Naming Conventions
- Use snake_case: `rate_limit_exceeded`, `fcm_notification_total`
- End counters with `_total`: `notification_sent_total`
- End timers with `_duration` or `_seconds`: `dynamodb_query_duration`
- Use tags for dimensions: `"endpoint", "/auth/verify"`

## Existing Metrics

### Already Instrumented
- **DynamoDB queries**: `dynamodb.query.duration` (via `QueryPerformanceTracker`)
- **JVM metrics**: memory, GC, threads (auto-configured)
- **HTTP requests**: request duration, status codes (auto-configured)
- **Rate limiting**: `rate_limit_exceeded_total{endpoint}` (via `RateLimitingService`)
  - Tracked endpoints: `/auth/resend-code`, `/auth/verify`, `/groups/invite/preview`, `/auth/request-password-reset`, `/auth/verify-reset-code`, `/auth/refresh`

### Candidates for Future Instrumentation
These are currently log-only, not tracked as metrics:

| Service | Current Logging | Suggested Metric |
|---------|----------------|------------------|
| `FcmNotificationService.handleFcmError()` | Error logging by type | `fcm_notification_total{status,error_code}` |
| `PushNotificationService` | Success/failure logs | `apns_notification_total{status,type}` |
| `TwilioSmsValidationService` | Verification result logs | `sms_verification_result_total{result}` |

## Infrastructure Configuration

### Dependencies
```groovy
// build.gradle
implementation 'io.micrometer:micrometer-core'
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

### Security
`/actuator/prometheus` is restricted to localhost only (for Grafana Alloy):
```java
// SecurityConfig.java
.requestMatchers("/actuator/prometheus").access((authentication, context) ->
    new AuthorizationDecision("127.0.0.1".equals(context.getRequest().getRemoteAddr())))
```

### Grafana Alloy (EB Platform Hooks)
- **Install**: `.platform/hooks/prebuild/03_install_grafana_alloy.sh`
- **Configure**: `.platform/hooks/postdeploy/01_configure_grafana_alloy.sh`

### AWS Parameter Store (per account)
```
/inviter/grafana/remote-write-url     (String)
/inviter/grafana/remote-write-user    (String)
/inviter/grafana/remote-write-password (SecureString)
```

### EB Environment Variable
- `ENVIRONMENT`: `staging` or `production` (used as metric label)

### IAM Requirements
EB instance role needs `ssm:GetParameter` for `/inviter/grafana/*`

## Local Testing
```bash
./gradlew bootRun
curl http://localhost:8080/actuator/prometheus | head -50
# Should show metrics in Prometheus format
```

## Grafana Cloud
- **URL**: https://unmediahangouts.grafana.net/
- **Prometheus datasource**: `grafanacloud-prom`
- **Free tier**: 10,000 metric series, 14-day retention
- **Scrape interval**: 15 seconds

### Dashboard Variable: $aggregation_interval

All dashboard queries should use `[$aggregation_interval]` instead of hardcoded time windows.
This allows adjusting the aggregation period (1m, 5m, 15m, 1h) from a dropdown.

**Setup** (already configured in main dashboard):
1. Dashboard Settings → Variables → Add variable
2. Configure:
   - **Name**: `aggregation_interval`
   - **Type**: Custom
   - **Values**: `1m,5m,15m,1h`
   - **Label**: "Aggregation"
3. In each panel's Query options, set **Min interval**: `$aggregation_interval`

### Example PromQL Queries

All queries use `[$aggregation_interval]` for consistent, adjustable time windows.

```promql
# Request count for specific endpoint (use increase() + round() for discrete counts)
round(sum(increase(http_server_requests_seconds_count{
  method="GET",
  uri="/groups/{groupId}/feed",
  environment="production"
}[$aggregation_interval])))

# Average latency for endpoint
sum(rate(http_server_requests_seconds_sum{uri="/groups/{groupId}/feed"}[$aggregation_interval]))
/ sum(rate(http_server_requests_seconds_count{uri="/groups/{groupId}/feed"}[$aggregation_interval]))

# Success rate (2xx responses)
sum(increase(http_server_requests_seconds_count{uri="/groups/{groupId}/feed", status=~"2.."}[$aggregation_interval]))
/ sum(increase(http_server_requests_seconds_count{uri="/groups/{groupId}/feed"}[$aggregation_interval])) * 100

# HTTP request count by status (all endpoints)
round(sum(increase(http_server_requests_seconds_count{environment="production"}[$aggregation_interval])) by (status))

# DynamoDB query duration p99
histogram_quantile(0.99, rate(dynamodb_query_duration_seconds_bucket[$aggregation_interval]))

# JVM memory usage (instant, no window needed)
jvm_memory_used_bytes{area="heap"}
```

### rate() vs increase()

| Function | Returns | Use for |
|----------|---------|---------|
| `rate()` | Per-second average | Throughput, latency calculations |
| `increase()` | Total count in window | Request counts, discrete events |

**Note**: `increase()` does boundary interpolation, so counts may be approximate (e.g., 5.33 instead of 7).
Use `round()` to get integer values. For exact counts, check logs.

## Troubleshooting

### Metrics not appearing in Grafana
1. Check Alloy is running: `systemctl status alloy`
2. Check Alloy logs: `journalctl -u alloy -n 50`
3. Check Parameter Store access: hook logs in `/var/log/eb-hooks.log`
4. Verify prometheus endpoint: `curl localhost:5000/actuator/prometheus`

### Hook not executing
- Ensure `.ebignore` includes `!.platform/hooks/**/*.sh`
- Check EB engine log for "The dir .platform/hooks/* does not exist"

### rate() showing phantom traffic (ghost requests)

**Problem**: `rate()` shows non-zero values even when the counter isn't increasing.

**Root Cause**: Multiple EC2 instances reporting metrics with the same `instance` label.

When Elastic Beanstalk scales or deploys, each instance reports as `localhost:5000`.
Grafana Cloud merges these into a single series, causing counter values to interleave
(e.g., 15 → 7 → 8), which Prometheus interprets as "counter resets" and compensates.

**Solution** (implemented Dec 2025): Each instance now has a unique `instance` and
`instance_id` label using the EC2 Instance ID. The Alloy config uses IMDSv2 token-based
authentication (required for EC2 instances created after Dec 2023):

```bash
# Get IMDSv2 token first (required for modern EC2)
IMDS_TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 300")

# Use token to get instance ID
INSTANCE_ID=$(curl -s -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" \
  http://169.254.169.254/latest/meta-data/instance-id)

# Applied to Alloy config
targets = [{"__address__" = "localhost:5000", "instance" = "${INSTANCE_ID}"}]
external_labels = { instance_id = "${INSTANCE_ID}", ... }
```

**Verification**:
```promql
# Check for multiple instance_id values (should see distinct EC2 IDs like i-0abc123...)
count by (instance_id) (up{environment="production"})

# Check for resets (should be 0 or very low)
sum(resets(http_server_requests_seconds_count{uri="/path", environment="production"}[1h]))
```

### rate() vs increase() best practices

**Key Points**:
- Always use `[$aggregation_interval]` in queries (see Dashboard Variable section above)
- Minimum reliable window is 4x scrape interval (15s × 4 = 60s), so 1m is the minimum option
- Use `increase()` for request counts, `rate()` for latency calculations
- Never use `irate()` for dashboards - it's too noisy; use for alerting only
