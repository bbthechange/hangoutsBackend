package com.bbthechange.inviter.config;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.jakarta.servlet.AWSXRayServletFilter;
import com.amazonaws.xray.plugins.EC2Plugin;
import com.amazonaws.xray.plugins.ElasticBeanstalkPlugin;
import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.Filter;
import java.net.URL;

@Configuration
public class XRayConfig {

    private static final Logger logger = LoggerFactory.getLogger(XRayConfig.class);

    @Value("${xray.enabled:false}")
    private boolean xrayEnabled;

    @Value("${xray.tracing-name:inviter-backend}")
    private String tracingName;

    @Bean
    @ConditionalOnProperty(name = "xray.enabled", havingValue = "true", matchIfMissing = false)
    public Filter TracingFilter() {
        logger.info("Initializing AWS X-Ray with service name: {}", tracingName);

        // Configure X-Ray recorder with plugins for AWS environment metadata
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard()
                .withPlugin(new EC2Plugin())
                .withPlugin(new ElasticBeanstalkPlugin());

        // Use sampling rules from classpath or default sampling
        URL samplingRulesURL = getClass().getResource("/xray-sampling-rules.json");
        if (samplingRulesURL != null) {
            builder.withSamplingStrategy(new LocalizedSamplingStrategy(samplingRulesURL));
            logger.info("Loaded X-Ray sampling rules from classpath");
        } else {
            logger.info("Using default X-Ray sampling (traces all requests)");
        }

        AWSXRay.setGlobalRecorder(builder.build());
        logger.info("AWS X-Ray recorder initialized successfully");

        return new AWSXRayServletFilter(tracingName);
    }
}
