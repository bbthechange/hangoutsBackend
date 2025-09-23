package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.VerificationCode;
import com.bbthechange.inviter.repository.VerificationCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    private static final int CODE_EXPIRY_MINUTES = 15;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final VerificationCodeRepository verificationCodeRepository;
    private final SmsNotificationService smsNotificationService;

    public AccountService(VerificationCodeRepository verificationCodeRepository,
                         SmsNotificationService smsNotificationService) {
        this.verificationCodeRepository = verificationCodeRepository;
        this.smsNotificationService = smsNotificationService;
    }

    public void sendVerificationCode(String phoneNumber) {
        // TODO: Add verification that phone number belongs to existing user with UNVERIFIED status
        
        String code = generateSixDigitCode();
        String hashedCode = hashCode(code);
        long expiresAt = Instant.now().plusSeconds(CODE_EXPIRY_MINUTES * 60).getEpochSecond();

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCode, expiresAt);
        
        verificationCodeRepository.save(verificationCode);
        logger.info("Verification code saved for phone number: {}", phoneNumber);

        smsNotificationService.sendVerificationCode(phoneNumber, code);
        logger.info("Verification code sent for phone number: {}", phoneNumber);
    }

    private String generateSixDigitCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    private String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}