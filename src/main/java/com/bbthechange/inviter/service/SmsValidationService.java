package com.bbthechange.inviter.service;

/**
 * Service interface for SMS-based phone number verification.
 * <p>
 * Implementations of this interface handle the complete verification flow:
 * sending verification codes to phone numbers and validating submitted codes.
 * <p>
 * Implementations may use different underlying services (AWS SNS, Twilio, etc.)
 * but must provide consistent behavior and return values.
 */
public interface SmsValidationService {

    /**
     * Sends a verification code to the specified phone number.
     * <p>
     * The implementation is responsible for:
     * - Generating or requesting a verification code
     * - Storing any necessary verification state
     * - Sending the code via SMS to the phone number
     *
     * @param phoneNumber the phone number to send the verification code to (E.164 format recommended)
     * @throws RuntimeException if the code cannot be sent
     */
    void sendVerificationCode(String phoneNumber);

    /**
     * Verifies a submitted code against the expected code for the phone number.
     * <p>
     * The implementation is responsible for:
     * - Retrieving and validating the stored verification state
     * - Checking code expiration
     * - Handling rate limiting and failed attempts
     * - Cleaning up verification state on success or final failure
     *
     * @param phoneNumber the phone number associated with the verification
     * @param submittedCode the code submitted by the user
     * @return a {@link VerificationResult} indicating success or the specific failure reason
     */
    VerificationResult verifyCode(String phoneNumber, String submittedCode);
}
