package com.bbthechange.inviter.service;

import com.bbthechange.inviter.exception.InvalidCodeException;
import com.bbthechange.inviter.exception.InvalidResetRequestException;
import com.bbthechange.inviter.exception.InvalidTokenException;
import com.bbthechange.inviter.model.AccountStatus;
import com.bbthechange.inviter.model.PasswordResetRequest;
import com.bbthechange.inviter.model.ResetMethod;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.PasswordResetRequestRepository;
import com.bbthechange.inviter.repository.RefreshTokenRepository;
import com.bbthechange.inviter.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling password reset operations.
 *
 * <p>This service orchestrates the three-step password reset flow:
 * 1. Request reset → Send SMS verification code
 * 2. Verify code → Issue short-lived reset token
 * 3. Reset password → Update password and revoke all sessions
 * </p>
 *
 * <p>Security features:
 * - No account enumeration (always returns success)
 * - SMS code verification via Twilio
 * - Short-lived (15 min) single-use reset tokens
 * - All refresh tokens revoked on password change
 * - Comprehensive security logging
 * </p>
 */
@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRequestRepository;
    private final SmsValidationService smsValidationService;
    private final JwtService jwtService;
    private final PasswordService passwordService;
    private final RefreshTokenRotationService refreshTokenRotationService;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetRequestRepository resetRequestRepository,
            SmsValidationService smsValidationService,
            JwtService jwtService,
            PasswordService passwordService,
            RefreshTokenRotationService refreshTokenRotationService) {
        this.userRepository = userRepository;
        this.resetRequestRepository = resetRequestRepository;
        this.smsValidationService = smsValidationService;
        this.jwtService = jwtService;
        this.passwordService = passwordService;
        this.refreshTokenRotationService = refreshTokenRotationService;
    }

    /**
     * Step 1: Request password reset via phone number.
     *
     * <p>This method:
     * - Looks up the user by phone number
     * - Only sends SMS if user exists and is ACTIVE
     * - Creates a tracking record in PasswordResetRequest table
     * - Delegates SMS sending to Twilio
     * - Returns void to prevent account enumeration
     * </p>
     *
     * @param phoneNumber The phone number requesting password reset
     * @param request HTTP request for extracting IP address
     */
    public void requestPasswordReset(String phoneNumber, HttpServletRequest request) {
        // Look up user
        Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);

        if (userOpt.isEmpty()) {
            // Don't reveal account doesn't exist - just log for security monitoring
            logger.info("Password reset requested for non-existent phone: {}", phoneNumber);
            return;
        }

        User user = userOpt.get();

        // Only allow reset for ACTIVE accounts (not UNVERIFIED)
        AccountStatus status = user.getAccountStatus();
        if (status == null) {
            status = AccountStatus.ACTIVE; // Backward compatibility
        }

        if (status != AccountStatus.ACTIVE) {
            // Don't reveal account status - just log
            logger.info("Password reset requested for non-ACTIVE account: phone={}, status={}",
                       phoneNumber, status);
            return;
        }

        // Create or overwrite reset request (userId PK ensures only one active reset)
        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setUserId(user.getId().toString());
        resetRequest.setPhoneNumber(phoneNumber);
        resetRequest.setMethod(ResetMethod.PHONE);
        resetRequest.setCodeVerified(false);
        resetRequest.setTokenUsed(false);
        resetRequest.setIpAddress(extractClientIP(request));
        resetRequest.setTtlOneDay(); // Auto-delete after 1 day
        resetRequestRepository.save(resetRequest);

        // Send SMS verification code via Twilio
        smsValidationService.sendVerificationCode(phoneNumber);

        logger.info("Password reset SMS sent: userId={}, phone={}, ip={}",
                   user.getId(), phoneNumber, resetRequest.getIpAddress());
    }

    /**
     * Step 2: Verify SMS code and issue reset token.
     *
     * <p>This method:
     * - Looks up the active reset request by phone number
     * - Validates the SMS code with Twilio
     * - Generates a short-lived (15 min) JWT reset token
     * - Marks the request as code-verified
     * - Returns the reset token to the client
     * </p>
     *
     * @param phoneNumber The phone number that received the code
     * @param code The 6-digit SMS verification code
     * @return JWT reset token (15 min expiration)
     * @throws InvalidResetRequestException if no active reset request exists
     * @throws InvalidCodeException if the SMS code is invalid or expired
     */
    public String verifyResetCode(String phoneNumber, String code) {
        // Look up active reset request
        Optional<PasswordResetRequest> resetOpt =
            resetRequestRepository.findByPhoneNumber(phoneNumber);

        if (resetOpt.isEmpty()) {
            logger.warn("No password reset request found for phone: {}", phoneNumber);
            throw new InvalidResetRequestException("No password reset requested for this number");
        }

        PasswordResetRequest resetRequest = resetOpt.get();

        // Verify method is PHONE
        if (resetRequest.getMethod() != ResetMethod.PHONE) {
            logger.warn("Invalid reset method for phone-based verification: phone={}, method={}",
                       phoneNumber, resetRequest.getMethod());
            throw new InvalidResetRequestException("Invalid reset method");
        }

        // Verify SMS code with Twilio (Twilio handles expiration and rate limiting)
        VerificationResult result = smsValidationService.verifyCode(phoneNumber, code);

        if (!result.isSuccess()) {
            logger.warn("Invalid password reset code: phone={}, status={}",
                       phoneNumber, result.getStatus());
            throw new InvalidCodeException("The reset code is incorrect or expired");
        }

        // Mark as verified and generate reset token
        resetRequest.setCodeVerified(true);
        resetRequestRepository.save(resetRequest);

        // Generate JWT reset token (15 min expiration)
        String resetToken = jwtService.generatePasswordResetToken(resetRequest.getUserId());

        logger.info("Password reset code verified: userId={}, phone={}",
                   resetRequest.getUserId(), phoneNumber);

        return resetToken;
    }

    /**
     * Step 3: Reset password using verified reset token.
     *
     * <p>This method:
     * - Validates the JWT reset token
     * - Looks up the corresponding reset request
     * - Verifies the request is in valid state (verified, not used)
     * - Updates the user's password (BCrypt hashed)
     * - Marks the reset token as used (prevents replay)
     * - Revokes all refresh tokens (force logout all devices)
     * - Logs the password change for security monitoring
     * </p>
     *
     * @param resetToken The JWT reset token from step 2
     * @param newPassword The new password (will be BCrypt hashed)
     * @throws InvalidTokenException if token is invalid, expired, or wrong type
     * @throws InvalidResetRequestException if reset request is invalid or already used
     */
    public void resetPassword(String resetToken, String newPassword) {
        // Validate JWT token (signature, expiration, type)
        if (!jwtService.isPasswordResetTokenValid(resetToken)) {
            logger.warn("Invalid password reset token attempted");
            throw new InvalidTokenException("The reset token is invalid or has expired");
        }

        String userId = jwtService.extractUserIdFromResetToken(resetToken);

        // Look up reset request by userId (PK lookup - fast)
        Optional<PasswordResetRequest> resetOpt = resetRequestRepository.findById(userId);

        if (resetOpt.isEmpty()) {
            logger.warn("No reset request found for userId: {}", userId);
            throw new InvalidResetRequestException("Reset request not found");
        }

        PasswordResetRequest resetRequest = resetOpt.get();

        // Validate request state
        if (!resetRequest.getCodeVerified()) {
            logger.warn("Reset attempted with unverified code: userId={}", userId);
            throw new InvalidResetRequestException("Reset code not verified");
        }

        if (resetRequest.getTokenUsed()) {
            logger.warn("Reset token already used: userId={}", userId);
            throw new InvalidResetRequestException("Reset token already used");
        }

        // Look up user and update password
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new InvalidResetRequestException("User not found"));

        // Hash new password with BCrypt
        user.setPassword(passwordService.encryptPassword(newPassword));
        userRepository.save(user);

        // Mark token as used (prevents replay attacks)
        resetRequest.setTokenUsed(true);
        resetRequestRepository.save(resetRequest);

        // Security: Revoke all refresh tokens (force logout all devices)
        refreshTokenRotationService.revokeAllUserTokens(userId);

        logger.warn("Password reset completed: userId={}, method={}, ip={}",
                   userId, resetRequest.getMethod(), resetRequest.getIpAddress());
    }

    /**
     * Helper to extract client IP address from HTTP request.
     * Handles X-Forwarded-For header for proxied requests.
     */
    private String extractClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
