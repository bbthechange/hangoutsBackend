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

### Candidates for Future Instrumentation
These are currently log-only, not tracked as metrics:

| Service | Current Logging | Suggested Metric |
|---------|----------------|------------------|
| `RateLimitingService.publishRateLimitMetric()` | `logger.warn("CloudWatch Metric...")` | `rate_limit_exceeded{endpoint}` |
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
- **Dashboard**: Create queries using PromQL
- **Free tier**: 10,000 metric series, 14-day retention
- **Scrape interval**: 15 seconds

### Example PromQL Queries
```promql
# DynamoDB query duration p99
histogram_quantile(0.99, rate(dynamodb_query_duration_seconds_bucket[5m]))

# HTTP request rate by status
sum(rate(http_server_requests_seconds_count[5m])) by (status)

# JVM memory usage
jvm_memory_used_bytes{area="heap"}
```

## Troubleshooting

### Metrics not appearing in Grafana
1. Check Alloy is running: `systemctl status alloy`
2. Check Alloy logs: `journalctl -u alloy -n 50`
3. Check Parameter Store access: hook logs in `/var/log/eb-hooks.log`
4. Verify prometheus endpoint: `curl localhost:5000/actuator/prometheus`

### Hook not executing
- Ensure `.ebignore` includes `!.platform/hooks/**/*.sh`
- Check EB engine log for "The dir .platform/hooks/* does not exist"
