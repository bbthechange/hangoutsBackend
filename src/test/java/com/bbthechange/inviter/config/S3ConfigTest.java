package com.bbthechange.inviter.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.*;

class S3ConfigTest {

    @Test
    void s3Client_WithDefaultConfiguration_ShouldCreateClient() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "region", "us-west-2");
        ReflectionTestUtils.setField(config, "endpoint", "");

        S3Client client = config.s3Client();

        assertNotNull(client);
    }

    @Test
    void s3Client_WithLocalStackEndpoint_ShouldCreateClientWithEndpointOverride() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "region", "us-west-2");
        ReflectionTestUtils.setField(config, "endpoint", "http://localhost:4566");

        S3Client client = config.s3Client();

        assertNotNull(client);
    }

    @Test
    void s3Client_WithNullEndpoint_ShouldCreateClient() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "region", "us-west-2");
        ReflectionTestUtils.setField(config, "endpoint", null);

        S3Client client = config.s3Client();

        assertNotNull(client);
    }

    @Test
    void s3Presigner_WithDefaultConfiguration_ShouldCreatePresigner() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "region", "us-west-2");
        ReflectionTestUtils.setField(config, "endpoint", "");

        S3Presigner presigner = config.s3Presigner();

        assertNotNull(presigner);
    }

    @Test
    void s3Presigner_WithLocalStackEndpoint_ShouldCreatePresignerWithEndpointOverride() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "region", "us-west-2");
        ReflectionTestUtils.setField(config, "endpoint", "http://localhost:4566");

        S3Presigner presigner = config.s3Presigner();

        assertNotNull(presigner);
    }

    @Test
    void s3Presigner_WithNullEndpoint_ShouldCreatePresigner() {
        S3Config config = new S3Config();
        ReflectionTestUtils.setField(config, "region", "us-west-2");
        ReflectionTestUtils.setField(config, "endpoint", null);

        S3Presigner presigner = config.s3Presigner();

        assertNotNull(presigner);
    }
}