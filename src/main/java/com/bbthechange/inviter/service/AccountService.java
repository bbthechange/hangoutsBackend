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
import java.util.Optional;

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

    public VerificationResult verifyCode(String phoneNumber, String submittedCode) {
        Optional<VerificationCode> verificationCodeOpt = verificationCodeRepository.findByPhoneNumber(phoneNumber);
        
        // No record found
        if (verificationCodeOpt.isEmpty()) {
            logger.info("No verification code found for phone number: {}", phoneNumber);
            return VerificationResult.codeExpired();
        }
        
        VerificationCode verificationCode = verificationCodeOpt.get();
        
        // Check if expired
        long currentTime = Instant.now().getEpochSecond();
        if (currentTime > verificationCode.getExpiresAt()) {
            logger.info("Verification code expired for phone number: {}", phoneNumber);
            verificationCodeRepository.deleteByPhoneNumber(phoneNumber);
            return VerificationResult.codeExpired();
        }
        
        // Hash the submitted code and compare
        String hashedSubmittedCode = hashCode(submittedCode);
        if (!hashedSubmittedCode.equals(verificationCode.getHashedCode())) {
            // Increment failed attempts
            verificationCode.setFailedAttempts(verificationCode.getFailedAttempts() + 1);
            
            if (verificationCode.getFailedAttempts() > 10) {
                logger.info("Too many failed attempts for phone number: {}, deleting verification code", phoneNumber);
                verificationCodeRepository.deleteByPhoneNumber(phoneNumber);
                return VerificationResult.codeExpired(); // Per spec: return VERIFICATION_CODE_EXPIRED when too many attempts
            }
            
            verificationCodeRepository.save(verificationCode);
            logger.info("Invalid verification code for phone number: {}, failed attempts: {}", 
                       phoneNumber, verificationCode.getFailedAttempts());
            return VerificationResult.invalidCode();
        }
        
        // Code is valid - delete the verification record
        verificationCodeRepository.deleteByPhoneNumber(phoneNumber);
        logger.info("Verification code successfully verified for phone number: {}", phoneNumber);
        
        // TODO: Update user accountStatus to ACTIVE when User model is updated
        
        return VerificationResult.success();
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