package com.bbthechange.inviter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SmsNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(SmsNotificationService.class);

    private final SnsClient snsClient;
    private final List<String> allowlist;

    public SmsNotificationService(SnsClient snsClient, 
                                 @Value("${inviter.sms.allowlist:}") String allowlistString) {
        this.snsClient = snsClient;
        this.allowlist = allowlistString.isEmpty() ? 
            List.of() : 
            Arrays.stream(allowlistString.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        
        logger.info("SMS Service initialized with allowlist: {}", 
                   allowlist.isEmpty() ? "empty (production mode)" : allowlist.size() + " numbers");
    }

    public void sendVerificationCode(String phoneNumber, String code) {
        if (isInAllowlist(phoneNumber)) {
            sendTestVerificationCode(phoneNumber, code);
        } else {
            sendRealVerificationCode(phoneNumber, code);
        }
    }

    private boolean isInAllowlist(String phoneNumber) {
        return allowlist.contains(phoneNumber.trim());
    }

    private void sendTestVerificationCode(String phoneNumber, String code) {
        logger.info("[SMS Bypass] Verification code for {} is {}", phoneNumber, code);
        logger.info("[SMS Bypass] Code would expire in 15 minutes");
    }

    private void sendRealVerificationCode(String phoneNumber, String code) {
        try {
            String message = String.format(
                "Your Inviter code is %s. Do not share it with anyone. This code expires in 15 minutes.", 
                code
            );

            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("AWS.SNS.SMS.SMSType", 
                MessageAttributeValue.builder()
                    .stringValue("Transactional")
                    .dataType("String")
                    .build());

            PublishRequest request = PublishRequest.builder()
                .phoneNumber(phoneNumber)
                .message(message)
                .messageAttributes(messageAttributes)
                .build();

            PublishResponse response = snsClient.publish(request);
            
            logger.info("SMS sent successfully to {} with messageId: {}", phoneNumber, response.messageId());
            
        } catch (SnsException e) {
            logger.error("Failed to send SMS to {}: {}", phoneNumber, e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to send verification SMS", e);
        } catch (Exception e) {
            logger.error("Unexpected error sending SMS to {}: {}", phoneNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to send verification SMS", e);
        }
    }
}