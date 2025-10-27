package com.bbthechange.inviter.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.Set;

/**
 * Twilio Verify API-based implementation of SMS verification.
 * <p>
 * This implementation:
 * - Uses Twilio Verify API to generate and send verification codes
 * - Delegates code storage and expiration to Twilio's service
 * - Verifies codes using Twilio's verification check API
 * - Does not require local database storage for verification codes
 * - Supports test phone numbers that bypass Twilio API for testing
 * <p>
 * Benefits over AWS SNS implementation:
 * - No A2P 10DLC campaign registration required
 * - Better international SMS support and compliance
 * - Simplified code management (no local hashing/storage)
 * - Built-in rate limiting and fraud detection
 * <p>
 * Test phone numbers bypass Twilio API and use a hardcoded test code for easy testing
 * in all environments (dev, staging, production).
 * <p>
 * Note: Bean is created by {@link com.bbthechange.inviter.config.SmsValidationConfig}
 */
public class TwilioSmsValidationService implements SmsValidationService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsValidationService.class);

    /**
     * Test phone numbers that bypass Twilio API calls.
     * These numbers are not real and can be used for testing registration in any environment.
     */
    private static final Set<String> TEST_PHONE_NUMBERS = Set.of(
            "+11112223333",
            "+12223334444",
            "+13334445555",
            "+14445556666",
            "+15556667777"
    );

    /**
     * Hardcoded verification code for test phone numbers.
     * This code will always work for numbers in TEST_PHONE_NUMBERS.
     */
    private static final String TEST_VERIFICATION_CODE = "123456";

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
        // Check if this is a test phone number
        if (TEST_PHONE_NUMBERS.contains(phoneNumber)) {
            logger.info("[TEST MODE] Verification code for test number {}: {}", phoneNumber, TEST_VERIFICATION_CODE);
            logger.info("[TEST MODE] Use code '{}' to verify this test account", TEST_VERIFICATION_CODE);
            return;
        }

        // Real phone number - use Twilio API
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
        // Handle test phone numbers
        VerificationResult testResult = verifyTestPhoneNumber(phoneNumber, code);
        if (testResult != null) {
            return testResult;
        }

        // Real phone number - use Twilio API
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

    /**
     * Verifies a test phone number with the hardcoded test code.
     * Returns null if this is not a test phone number.
     */
    private VerificationResult verifyTestPhoneNumber(String phoneNumber, String code) {
        if (!TEST_PHONE_NUMBERS.contains(phoneNumber)) {
            return null;
        }

        if (TEST_VERIFICATION_CODE.equals(code)) {
            logger.info("[TEST MODE] Verification successful for test number {}", phoneNumber);
            return VerificationResult.success();
        } else {
            logger.info("[TEST MODE] Invalid code for test number {}: expected {}, got {}",
                       phoneNumber, TEST_VERIFICATION_CODE, code);
            return VerificationResult.invalidCode();
        }
    }
}
