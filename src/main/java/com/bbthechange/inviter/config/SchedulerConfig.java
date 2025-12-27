package com.bbthechange.inviter.config;

import com.amazonaws.xray.interceptors.TracingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.scheduler.SchedulerClient;

/**
 * Configuration for AWS EventBridge Scheduler client.
 * Used for scheduling hangout reminder notifications.
 */
@Configuration
public class SchedulerConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${xray.enabled:false}")
    private boolean xrayEnabled;

    @Value("${scheduler.enabled:false}")
    private boolean schedulerEnabled;

    @Value("${scheduler.target-arn:}")
    private String targetArn;

    @Value("${scheduler.role-arn:}")
    private String roleArn;

    @Value("${scheduler.dlq-arn:}")
    private String dlqArn;

    @Value("${scheduler.group-name:hangout-reminders}")
    private String groupName;

    @Value("${scheduler.flexible-window-minutes:5}")
    private int flexibleWindowMinutes;

    @Bean
    @ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
    public SchedulerClient schedulerClient() {
        var builder = SchedulerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        // Add X-Ray tracing if enabled
        if (xrayEnabled) {
            builder.overrideConfiguration(c -> c.addExecutionInterceptor(new TracingInterceptor()));
        }

        return builder.build();
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public String getTargetArn() {
        return targetArn;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public String getDlqArn() {
        return dlqArn;
    }

    public String getGroupName() {
        return groupName;
    }

    public int getFlexibleWindowMinutes() {
        return flexibleWindowMinutes;
    }
}
