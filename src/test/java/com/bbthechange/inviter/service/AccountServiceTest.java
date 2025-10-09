package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.AccountStatus;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.exception.AccountNotFoundException;
import com.bbthechange.inviter.exception.AccountAlreadyVerifiedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private SmsValidationService mockSmsValidationService;

    @Mock
    private UserRepository mockUserRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(mockSmsValidationService, mockUserRepository);
    }

    // ========================================================================
    // Section 1: sendVerificationCode() Tests
    // ========================================================================

    @Test
    void sendVerificationCode_DelegatesToSmsValidationService() {
        // Given
        String phoneNumber = "+19995550001";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then
        verify(mockSmsValidationService).sendVerificationCode(phoneNumber);
    }

    @Test
    void sendVerificationCode_PropagatesExceptionsFromSmsValidationService() {
        // Given
        String phoneNumber = "+19995550001";
        RuntimeException smsError = new RuntimeException("SMS error");
        doThrow(smsError).when(mockSmsValidationService).sendVerificationCode(phoneNumber);

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCode(phoneNumber))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("SMS error");
    }

    // ========================================================================
    // Section 2: verifyCode() Tests
    // ========================================================================

    @Test
    void verifyCode_SuccessfulVerification_UpdatesUserStatusToActive() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        User unverifiedUser = new User();
        unverifiedUser.setPhoneNumber(phoneNumber);
        unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(VerificationResult.success());
        when(mockUserRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(unverifiedUser));

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(mockUserRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void verifyCode_FailedVerification_DoesNotUpdateUserStatus() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "999999";

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(VerificationResult.invalidCode());

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        verify(mockUserRepository, never()).save(any());
    }

    @Test
    void verifyCode_ExpiredCode_DoesNotUpdateUserStatus() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(VerificationResult.codeExpired());

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        verify(mockUserRepository, never()).save(any());
    }

    @Test
    void verifyCode_SuccessfulVerificationWithNoUserFound_StillReturnsSuccess() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(VerificationResult.success());
        when(mockUserRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.empty());

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
        verify(mockUserRepository, never()).save(any());
    }

    @Test
    void verifyCode_DelegatesPhoneNumberCorrectly() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(VerificationResult.success());
        when(mockUserRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.empty());

        // When
        accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSmsValidationService).verifyCode(phoneCaptor.capture(), codeCaptor.capture());
        assertThat(phoneCaptor.getValue()).isEqualTo(phoneNumber);
        assertThat(codeCaptor.getValue()).isEqualTo(submittedCode);
    }

    @Test
    void verifyCode_ReturnsVerificationResultFromDelegate_Success() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        VerificationResult expectedResult = VerificationResult.success();

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(expectedResult);
        when(mockUserRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.empty());

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
    }

    @Test
    void verifyCode_ReturnsVerificationResultFromDelegate_InvalidCode() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "999999";
        VerificationResult expectedResult = VerificationResult.invalidCode();

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(expectedResult);

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
    }

    @Test
    void verifyCode_ReturnsVerificationResultFromDelegate_CodeExpired() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        VerificationResult expectedResult = VerificationResult.codeExpired();

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(expectedResult);

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
    }

    @Test
    void verifyCode_UserRepositoryLookup_UsesCorrectPhoneNumber() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(VerificationResult.success());
        when(mockUserRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.empty());

        // When
        accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockUserRepository).findByPhoneNumber(phoneCaptor.capture());
        assertThat(phoneCaptor.getValue()).isEqualTo(phoneNumber);
    }

    @Test
    void verifyCode_UserSave_CalledAfterStatusUpdate() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        User unverifiedUser = new User();
        unverifiedUser.setPhoneNumber(phoneNumber);
        unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);

        when(mockSmsValidationService.verifyCode(phoneNumber, submittedCode))
                .thenReturn(VerificationResult.success());
        when(mockUserRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(unverifiedUser));

        // When
        accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        verify(mockUserRepository).findByPhoneNumber(phoneNumber);
        verify(mockUserRepository).save(any(User.class));

        // Verify order: find called before save
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(mockUserRepository);
        inOrder.verify(mockUserRepository).findByPhoneNumber(phoneNumber);
        inOrder.verify(mockUserRepository).save(any(User.class));
    }

    // =======================================================================
    // sendVerificationCodeWithAccountCheck Tests
    // =======================================================================

    // Happy Path Tests
    @Test
    void sendVerificationCodeWithAccountCheck_UnverifiedUser_SendsCode() {
        // Given
        String phoneNumber = "+15551234567";
        User unverifiedUser = new User();
        unverifiedUser.setPhoneNumber(phoneNumber);
        unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(unverifiedUser));

        // When
        accountService.sendVerificationCodeWithAccountCheck(phoneNumber);

        // Then
        verify(mockSmsValidationService).sendVerificationCode(phoneNumber);
    }

    @Test
    void sendVerificationCodeWithAccountCheck_UnverifiedUser_CallsRepositoryOnce() {
        // Given
        String phoneNumber = "+15551234567";
        User unverifiedUser = new User();
        unverifiedUser.setPhoneNumber(phoneNumber);
        unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(unverifiedUser));

        // When
        accountService.sendVerificationCodeWithAccountCheck(phoneNumber);

        // Then
        verify(mockUserRepository, times(1)).findByPhoneNumber(phoneNumber);
    }

    // Account Not Found Tests
    @Test
    void sendVerificationCodeWithAccountCheck_UserNotFound_ThrowsAccountNotFoundException() {
        // Given
        String phoneNumber = "+15551234567";
        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCodeWithAccountCheck(phoneNumber))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("No account found for this phone number.");
    }

    @Test
    void sendVerificationCodeWithAccountCheck_UserNotFound_DoesNotSendCode() {
        // Given
        String phoneNumber = "+15551234567";
        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.empty());

        // When
        try {
            accountService.sendVerificationCodeWithAccountCheck(phoneNumber);
        } catch (AccountNotFoundException e) {
            // Expected exception
        }

        // Then
        verifyNoInteractions(mockSmsValidationService);
    }

    @Test
    void sendVerificationCodeWithAccountCheck_NullPhoneNumber_HandlesGracefully() {
        // Given
        String phoneNumber = null;

        // When & Then - should not crash before repository call
        try {
            accountService.sendVerificationCodeWithAccountCheck(phoneNumber);
        } catch (Exception e) {
            // Any exception is acceptable, just shouldn't crash the JVM
        }
    }

    @Test
    void sendVerificationCodeWithAccountCheck_EmptyPhoneNumber_HandlesGracefully() {
        // Given
        String phoneNumber = "";
        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCodeWithAccountCheck(phoneNumber))
                .isInstanceOf(AccountNotFoundException.class);

        verify(mockUserRepository).findByPhoneNumber(phoneNumber);
    }

    // Account Already Verified Tests
    @Test
    void sendVerificationCodeWithAccountCheck_ActiveUser_ThrowsAccountAlreadyVerifiedException() {
        // Given
        String phoneNumber = "+15551234567";
        User activeUser = new User();
        activeUser.setPhoneNumber(phoneNumber);
        activeUser.setAccountStatus(AccountStatus.ACTIVE);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(activeUser));

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCodeWithAccountCheck(phoneNumber))
                .isInstanceOf(AccountAlreadyVerifiedException.class)
                .hasMessage("This account has already been verified.");
    }

    @Test
    void sendVerificationCodeWithAccountCheck_ActiveUser_DoesNotSendCode() {
        // Given
        String phoneNumber = "+15551234567";
        User activeUser = new User();
        activeUser.setPhoneNumber(phoneNumber);
        activeUser.setAccountStatus(AccountStatus.ACTIVE);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(activeUser));

        // When
        try {
            accountService.sendVerificationCodeWithAccountCheck(phoneNumber);
        } catch (AccountAlreadyVerifiedException e) {
            // Expected exception
        }

        // Then
        verifyNoInteractions(mockSmsValidationService);
    }

    // Backward Compatibility Tests
    @Test
    void sendVerificationCodeWithAccountCheck_NullAccountStatus_TreatedAsActive() {
        // Given
        String phoneNumber = "+15551234567";
        User userWithNullStatus = new User();
        userWithNullStatus.setPhoneNumber(phoneNumber);
        userWithNullStatus.setAccountStatus(null);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(userWithNullStatus));

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCodeWithAccountCheck(phoneNumber))
                .isInstanceOf(AccountAlreadyVerifiedException.class)
                .hasMessage("This account has already been verified.");
    }

    @Test
    void sendVerificationCodeWithAccountCheck_NullAccountStatus_DoesNotSendCode() {
        // Given
        String phoneNumber = "+15551234567";
        User userWithNullStatus = new User();
        userWithNullStatus.setPhoneNumber(phoneNumber);
        userWithNullStatus.setAccountStatus(null);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(userWithNullStatus));

        // When
        try {
            accountService.sendVerificationCodeWithAccountCheck(phoneNumber);
        } catch (AccountAlreadyVerifiedException e) {
            // Expected exception
        }

        // Then
        verifyNoInteractions(mockSmsValidationService);
    }

    // Error Propagation Tests
    @Test
    void sendVerificationCodeWithAccountCheck_SendVerificationCodeThrows_PropagatesException() {
        // Given
        String phoneNumber = "+15551234567";
        User unverifiedUser = new User();
        unverifiedUser.setPhoneNumber(phoneNumber);
        unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(unverifiedUser));

        RuntimeException smsException = new RuntimeException("SMS service error");
        doThrow(smsException).when(mockSmsValidationService)
                .sendVerificationCode(phoneNumber);

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCodeWithAccountCheck(phoneNumber))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("SMS service error");
    }

    @Test
    void sendVerificationCodeWithAccountCheck_RepositoryThrows_PropagatesException() {
        // Given
        String phoneNumber = "+15551234567";
        RuntimeException repositoryException = new RuntimeException("Database error");
        doThrow(repositoryException).when(mockUserRepository).findByPhoneNumber(phoneNumber);

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCodeWithAccountCheck(phoneNumber))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");
    }

    // Method Interaction Tests
    @Test
    void sendVerificationCodeWithAccountCheck_UnverifiedUser_CallsCorrectSendMethod() {
        // Given
        String phoneNumber = "+19995550001";
        User unverifiedUser = new User();
        unverifiedUser.setPhoneNumber(phoneNumber);
        unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(unverifiedUser));

        // When
        accountService.sendVerificationCodeWithAccountCheck(phoneNumber);

        // Then
        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSmsValidationService).sendVerificationCode(phoneCaptor.capture());
        assertThat(phoneCaptor.getValue()).isEqualTo(phoneNumber);
    }

    @Test
    void sendVerificationCodeWithAccountCheck_CallsRepositoryWithExactPhoneNumber() {
        // Given
        String phoneNumber = "+19995550001";
        User unverifiedUser = new User();
        unverifiedUser.setPhoneNumber(phoneNumber);
        unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);

        when(mockUserRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(unverifiedUser));

        // When
        accountService.sendVerificationCodeWithAccountCheck(phoneNumber);

        // Then
        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockUserRepository).findByPhoneNumber(phoneCaptor.capture());
        assertThat(phoneCaptor.getValue()).isEqualTo(phoneNumber);
    }

    // Edge Cases and Boundary Conditions
    @Test
    void sendVerificationCodeWithAccountCheck_VeryLongPhoneNumber_HandledCorrectly() {
        // Given
        String veryLongPhoneNumber = "+1" + "5".repeat(100);
        when(mockUserRepository.findByPhoneNumber(veryLongPhoneNumber)).thenReturn(Optional.empty());

        // When & Then - method should not crash
        assertThatThrownBy(() -> accountService.sendVerificationCodeWithAccountCheck(veryLongPhoneNumber))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void sendVerificationCodeWithAccountCheck_SpecialCharactersInPhoneNumber_HandledCorrectly() {
        // Given
        String phoneNumberWithSpecialChars = "+1 (555) 123-4567";
        when(mockUserRepository.findByPhoneNumber(phoneNumberWithSpecialChars)).thenReturn(Optional.empty());

        // When & Then - method should not crash
        assertThatThrownBy(() -> accountService.sendVerificationCodeWithAccountCheck(phoneNumberWithSpecialChars))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // Exception Tests
    @Test
    void AccountNotFoundException_MessagePreserved() {
        // Given
        String message = "Test message";

        // When
        AccountNotFoundException exception = new AccountNotFoundException(message);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void AccountNotFoundException_IsRuntimeException() {
        // Given
        AccountNotFoundException exception = new AccountNotFoundException("Test");

        // When & Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }

    @Test
    void AccountAlreadyVerifiedException_MessagePreserved() {
        // Given
        String message = "Test message";

        // When
        AccountAlreadyVerifiedException exception = new AccountAlreadyVerifiedException(message);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    void AccountAlreadyVerifiedException_IsRuntimeException() {
        // Given
        AccountAlreadyVerifiedException exception = new AccountAlreadyVerifiedException("Test");

        // When & Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
}
