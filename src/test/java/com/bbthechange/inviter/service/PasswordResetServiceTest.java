package com.bbthechange.inviter.service;

import com.bbthechange.inviter.exception.InvalidCodeException;
import com.bbthechange.inviter.exception.InvalidResetRequestException;
import com.bbthechange.inviter.exception.InvalidTokenException;
import com.bbthechange.inviter.model.AccountStatus;
import com.bbthechange.inviter.model.PasswordResetRequest;
import com.bbthechange.inviter.model.ResetMethod;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.PasswordResetRequestRepository;
import com.bbthechange.inviter.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetRequestRepository resetRequestRepository;

    @Mock
    private SmsValidationService smsValidationService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordService passwordService;

    @Mock
    private RefreshTokenRotationService refreshTokenRotationService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private PasswordResetService passwordResetService;

    // ========== requestPasswordReset() Tests ==========

    @Test
    void requestPasswordReset_WithActiveUser_SendsSMS() {
        // Given
        String phoneNumber = "+19285251044";
        String userId = UUID.randomUUID().toString();
        String ipAddress = "192.168.1.1";

        User user = new User();
        user.setId(UUID.fromString(userId));
        user.setPhoneNumber(phoneNumber);
        user.setAccountStatus(AccountStatus.ACTIVE);

        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(user));
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn(ipAddress);

        // When
        passwordResetService.requestPasswordReset(phoneNumber, httpRequest);

        // Then
        ArgumentCaptor<PasswordResetRequest> captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(resetRequestRepository).save(captor.capture());
        verify(smsValidationService).sendVerificationCode(phoneNumber);

        PasswordResetRequest savedRequest = captor.getValue();
        assertThat(savedRequest.getUserId()).isEqualTo(userId);
        assertThat(savedRequest.getPhoneNumber()).isEqualTo(phoneNumber);
        assertThat(savedRequest.getMethod()).isEqualTo(ResetMethod.PHONE);
        assertThat(savedRequest.getCodeVerified()).isFalse();
        assertThat(savedRequest.getTokenUsed()).isFalse();
        assertThat(savedRequest.getIpAddress()).isEqualTo(ipAddress);
        assertThat(savedRequest.getTtl()).isNotNull();
    }

    @Test
    void requestPasswordReset_WithNonExistentUser_DoesNothing() {
        // Given
        String phoneNumber = "+15551234567";
        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.empty());

        // When
        passwordResetService.requestPasswordReset(phoneNumber, httpRequest);

        // Then
        verify(resetRequestRepository, never()).save(any());
        verify(smsValidationService, never()).sendVerificationCode(any());
    }

    @Test
    void requestPasswordReset_WithUnverifiedUser_DoesNothing() {
        // Given
        String phoneNumber = "+19285251044";
        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setAccountStatus(AccountStatus.UNVERIFIED);

        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(user));

        // When
        passwordResetService.requestPasswordReset(phoneNumber, httpRequest);

        // Then
        verify(resetRequestRepository, never()).save(any());
        verify(smsValidationService, never()).sendVerificationCode(any());
    }

    @Test
    void requestPasswordReset_SecondRequest_OverwritesPrevious() {
        // Given
        String phoneNumber = "+19285251044";
        String userId = UUID.randomUUID().toString();

        User user = new User();
        user.setId(UUID.fromString(userId));
        user.setPhoneNumber(phoneNumber);
        user.setAccountStatus(AccountStatus.ACTIVE);

        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(user));
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");

        // When - First request
        passwordResetService.requestPasswordReset(phoneNumber, httpRequest);

        // When - Second request
        passwordResetService.requestPasswordReset(phoneNumber, httpRequest);

        // Then - Should have been called twice (second overwrites first)
        verify(resetRequestRepository, times(2)).save(any(PasswordResetRequest.class));
        verify(smsValidationService, times(2)).sendVerificationCode(phoneNumber);
    }

    // ========== verifyResetCode() Tests ==========

    @Test
    void verifyResetCode_WithValidCode_ReturnsToken() {
        // Given
        String phoneNumber = "+19285251044";
        String code = "123456";
        String userId = UUID.randomUUID().toString();
        String resetToken = "eyJhbGci...";

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setUserId(userId);
        resetRequest.setPhoneNumber(phoneNumber);
        resetRequest.setMethod(ResetMethod.PHONE);
        resetRequest.setCodeVerified(false);

        when(resetRequestRepository.findByPhoneNumber(phoneNumber))
            .thenReturn(Optional.of(resetRequest));
        when(smsValidationService.verifyCode(phoneNumber, code))
            .thenReturn(VerificationResult.success());
        when(jwtService.generatePasswordResetToken(userId)).thenReturn(resetToken);

        // When
        String result = passwordResetService.verifyResetCode(phoneNumber, code);

        // Then
        assertThat(result).isEqualTo(resetToken);
        verify(smsValidationService).verifyCode(phoneNumber, code);
        verify(jwtService).generatePasswordResetToken(userId);

        ArgumentCaptor<PasswordResetRequest> captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(resetRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getCodeVerified()).isTrue();
    }

    @Test
    void verifyResetCode_WithNoResetRequest_ThrowsException() {
        // Given
        String phoneNumber = "+19285251044";
        String code = "123456";

        when(resetRequestRepository.findByPhoneNumber(phoneNumber))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> passwordResetService.verifyResetCode(phoneNumber, code))
            .isInstanceOf(InvalidResetRequestException.class)
            .hasMessage("No password reset requested for this number");
    }

    @Test
    void verifyResetCode_WithInvalidCode_ThrowsException() {
        // Given
        String phoneNumber = "+19285251044";
        String code = "999999";

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setPhoneNumber(phoneNumber);
        resetRequest.setMethod(ResetMethod.PHONE);

        when(resetRequestRepository.findByPhoneNumber(phoneNumber))
            .thenReturn(Optional.of(resetRequest));
        when(smsValidationService.verifyCode(phoneNumber, code))
            .thenReturn(VerificationResult.invalidCode());

        // When/Then
        assertThatThrownBy(() -> passwordResetService.verifyResetCode(phoneNumber, code))
            .isInstanceOf(InvalidCodeException.class)
            .hasMessage("The reset code is incorrect or expired");

        verify(resetRequestRepository, never()).save(any());
    }

    @Test
    void verifyResetCode_WithExpiredCode_ThrowsException() {
        // Given
        String phoneNumber = "+19285251044";
        String code = "123456";

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setPhoneNumber(phoneNumber);
        resetRequest.setMethod(ResetMethod.PHONE);

        when(resetRequestRepository.findByPhoneNumber(phoneNumber))
            .thenReturn(Optional.of(resetRequest));
        when(smsValidationService.verifyCode(phoneNumber, code))
            .thenReturn(VerificationResult.codeExpired());

        // When/Then
        assertThatThrownBy(() -> passwordResetService.verifyResetCode(phoneNumber, code))
            .isInstanceOf(InvalidCodeException.class)
            .hasMessage("The reset code is incorrect or expired");
    }

    @Test
    void verifyResetCode_WithWrongMethod_ThrowsException() {
        // Given
        String phoneNumber = "+19285251044";
        String code = "123456";

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setPhoneNumber(phoneNumber);
        resetRequest.setMethod(ResetMethod.EMAIL); // Wrong method

        when(resetRequestRepository.findByPhoneNumber(phoneNumber))
            .thenReturn(Optional.of(resetRequest));

        // When/Then
        assertThatThrownBy(() -> passwordResetService.verifyResetCode(phoneNumber, code))
            .isInstanceOf(InvalidResetRequestException.class)
            .hasMessage("Invalid reset method");
    }

    // ========== resetPassword() Tests ==========

    @Test
    void resetPassword_WithValidToken_UpdatesPassword() {
        // Given
        String resetToken = "eyJhbGci...";
        String newPassword = "newSecurePassword123";
        String userId = UUID.randomUUID().toString();
        String encryptedPassword = "$2a$10$...";

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setUserId(userId);
        resetRequest.setCodeVerified(true);
        resetRequest.setTokenUsed(false);

        User user = new User();
        user.setId(UUID.fromString(userId));

        when(jwtService.isPasswordResetTokenValid(resetToken)).thenReturn(true);
        when(jwtService.extractUserIdFromResetToken(resetToken)).thenReturn(userId);
        when(resetRequestRepository.findById(userId)).thenReturn(Optional.of(resetRequest));
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(passwordService.encryptPassword(newPassword)).thenReturn(encryptedPassword);

        // When
        passwordResetService.resetPassword(resetToken, newPassword);

        // Then
        verify(jwtService).isPasswordResetTokenValid(resetToken);
        verify(passwordService).encryptPassword(newPassword);
        verify(userRepository).save(user);
        verify(refreshTokenRotationService).revokeAllUserTokens(userId);

        ArgumentCaptor<PasswordResetRequest> captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(resetRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenUsed()).isTrue();
        assertThat(user.getPassword()).isEqualTo(encryptedPassword);
    }

    @Test
    void resetPassword_WithInvalidToken_ThrowsException() {
        // Given
        String resetToken = "invalid.token";
        String newPassword = "newPassword123";

        when(jwtService.isPasswordResetTokenValid(resetToken)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> passwordResetService.resetPassword(resetToken, newPassword))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessage("The reset token is invalid or has expired");

        verify(passwordService, never()).encryptPassword(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_WithExpiredToken_ThrowsException() {
        // Given
        String resetToken = "expired.token";
        String newPassword = "newPassword123";

        when(jwtService.isPasswordResetTokenValid(resetToken)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> passwordResetService.resetPassword(resetToken, newPassword))
            .isInstanceOf(InvalidTokenException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_WithWrongTokenType_ThrowsException() {
        // Given
        String resetToken = "access.token.notResetToken";
        String newPassword = "newPassword123";

        when(jwtService.isPasswordResetTokenValid(resetToken)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> passwordResetService.resetPassword(resetToken, newPassword))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void resetPassword_WithAlreadyUsedToken_ThrowsException() {
        // Given
        String resetToken = "eyJhbGci...";
        String newPassword = "newPassword123";
        String userId = UUID.randomUUID().toString();

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setUserId(userId);
        resetRequest.setCodeVerified(true);
        resetRequest.setTokenUsed(true); // Already used

        when(jwtService.isPasswordResetTokenValid(resetToken)).thenReturn(true);
        when(jwtService.extractUserIdFromResetToken(resetToken)).thenReturn(userId);
        when(resetRequestRepository.findById(userId)).thenReturn(Optional.of(resetRequest));

        // When/Then
        assertThatThrownBy(() -> passwordResetService.resetPassword(resetToken, newPassword))
            .isInstanceOf(InvalidResetRequestException.class)
            .hasMessage("Reset token already used");

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_WithUnverifiedCode_ThrowsException() {
        // Given
        String resetToken = "eyJhbGci...";
        String newPassword = "newPassword123";
        String userId = UUID.randomUUID().toString();

        PasswordResetRequest resetRequest = new PasswordResetRequest();
        resetRequest.setUserId(userId);
        resetRequest.setCodeVerified(false); // Not verified
        resetRequest.setTokenUsed(false);

        when(jwtService.isPasswordResetTokenValid(resetToken)).thenReturn(true);
        when(jwtService.extractUserIdFromResetToken(resetToken)).thenReturn(userId);
        when(resetRequestRepository.findById(userId)).thenReturn(Optional.of(resetRequest));

        // When/Then
        assertThatThrownBy(() -> passwordResetService.resetPassword(resetToken, newPassword))
            .isInstanceOf(InvalidResetRequestException.class)
            .hasMessage("Reset code not verified");

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_WithNonExistentRequest_ThrowsException() {
        // Given
        String resetToken = "eyJhbGci...";
        String newPassword = "newPassword123";
        String userId = UUID.randomUUID().toString();

        when(jwtService.isPasswordResetTokenValid(resetToken)).thenReturn(true);
        when(jwtService.extractUserIdFromResetToken(resetToken)).thenReturn(userId);
        when(resetRequestRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> passwordResetService.resetPassword(resetToken, newPassword))
            .isInstanceOf(InvalidResetRequestException.class)
            .hasMessage("Reset request not found");

        verify(userRepository, never()).save(any());
    }
}
