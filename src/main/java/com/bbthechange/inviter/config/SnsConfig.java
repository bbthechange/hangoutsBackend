package com.bbthechange.inviter.config;

import com.amazonaws.xray.interceptors.TracingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;

@Configuration
public class SnsConfig {

    @Value("${aws.region:us-west-2}")
    private String region;

    @Value("${xray.enabled:false}")
    private boolean xrayEnabled;

    @Bean
    public SnsClient snsClient() {
        SnsClientBuilder builder = SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        // Add X-Ray tracing if enabled
        if (xrayEnabled) {
            builder.overrideConfiguration(c -> c.addExecutionInterceptor(new TracingInterceptor()));
        }

        return builder.build();
    }
}