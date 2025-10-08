package com.bbthechange.inviter.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Twilio Verify API-based implementation of SMS verification.
 * <p>
 * This implementation:
 * - Uses Twilio Verify API to generate and send verification codes
 * - Delegates code storage and expiration to Twilio's service
 * - Verifies codes using Twilio's verification check API
 * - Does not require local database storage for verification codes
 * <p>
 * Benefits over AWS SNS implementation:
 * - No A2P 10DLC campaign registration required
 * - Better international SMS support and compliance
 * - Simplified code management (no local hashing/storage)
 * - Built-in rate limiting and fraud detection
 * <p>
 * Note: Bean is created by {@link com.bbthechange.inviter.config.SmsValidationConfig}
 */
public class TwilioSmsValidationService implements SmsValidationService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsValidationService.class);

    private final String verifyServiceSid;

    public TwilioSmsValidationService(
            @Value("${twilio.account-sid}") String accountSid,
            @Value("${twilio.auth-token}") String authToken,
            @Value("${twilio.verify-service-sid}") String verifyServiceSid) {
        this.verifyServiceSid = verifyServiceSid;
        Twilio.init(accountSid, authToken);
        logger.info("Twilio Verify client initialized for service SID: {}", verifyServiceSid);
    }

    @Override
    public void sendVerificationCode(String phoneNumber) {
        try {
            Verification verification = Verification.creator(
                            verifyServiceSid,
                            phoneNumber,
                            "sms")
                    .create();
            logger.info("Started Twilio verification for {}: SID {}, status {}",
                       phoneNumber, verification.getSid(), verification.getStatus());
        } catch (ApiException e) {
            logger.error("Failed to start Twilio verification for {}: {} (code: {})",
                        phoneNumber, e.getMessage(), e.getCode(), e);
            throw new RuntimeException("Failed to send verification code via Twilio: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error starting Twilio verification for {}: {}",
                        phoneNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to send verification code via Twilio", e);
        }
    }

    @Override
    public VerificationResult verifyCode(String phoneNumber, String code) {
        try {
            VerificationCheck verificationCheck = VerificationCheck.creator(verifyServiceSid)
                    .setTo(phoneNumber)
                    .setCode(code)
                    .create();

            boolean approved = "approved".equals(verificationCheck.getStatus());
            logger.info("Twilio verification check for {} status: {}", phoneNumber, verificationCheck.getStatus());

            if (approved) {
                return VerificationResult.success();
            } else {
                // Status could be "pending" (wrong code but not expired) or other states
                logger.info("Twilio verification failed for {} with status: {}", phoneNumber, verificationCheck.getStatus());
                return VerificationResult.invalidCode();
            }
        } catch (ApiException e) {
            // Twilio returns a 404 if the verification attempt is not found or has expired
            // Twilio returns a 429 if too many attempts have been made
            int statusCode = e.getStatusCode() != null ? e.getStatusCode() : 0;

            if (statusCode == 404) {
                logger.info("Twilio verification not found or expired for {}: {}", phoneNumber, e.getMessage());
                return VerificationResult.codeExpired();
            } else if (statusCode == 429) {
                logger.warn("Too many verification attempts for {}: {}", phoneNumber, e.getMessage());
                return VerificationResult.codeExpired();
            } else {
                logger.warn("Failed to check Twilio verification for {}: {} (code: {})",
                           phoneNumber, e.getMessage(), statusCode, e);
                return VerificationResult.invalidCode();
            }
        } catch (Exception e) {
            logger.error("Unexpected error checking Twilio verification for {}: {}",
                        phoneNumber, e.getMessage(), e);
            return VerificationResult.invalidCode();
        }
    }
}
