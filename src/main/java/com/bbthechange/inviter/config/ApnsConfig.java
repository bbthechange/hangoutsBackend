package com.bbthechange.inviter.config;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class ApnsConfig {

    @Value("${apns.key-file-path:}")
    private String keyFilePath;

    @Value("${apns.key-id:}")
    private String keyId;

    @Value("${apns.team-id:}")
    private String teamId;

    @Value("${apns.production:false}")
    private boolean production;

    @Bean
    @ConditionalOnProperty(name = "apns.enabled", havingValue = "true", matchIfMissing = false)
    public ApnsClient apnsClient() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(new File(keyFilePath), teamId, keyId);
        
        ApnsClientBuilder builder = new ApnsClientBuilder()
                .setApnsServer(production ? 
                    ApnsClientBuilder.PRODUCTION_APNS_HOST : 
                    ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                .setSigningKey(signingKey);
        
        return builder.build();
    }
}