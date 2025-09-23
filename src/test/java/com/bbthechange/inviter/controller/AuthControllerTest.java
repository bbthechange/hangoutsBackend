package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.controller.AuthController.LoginRequest;
import com.bbthechange.inviter.dto.VerifyRequest;
import com.bbthechange.inviter.model.RefreshToken;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.model.AccountStatus;
import com.bbthechange.inviter.repository.RefreshTokenRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.service.PasswordService;
import com.bbthechange.inviter.service.RefreshTokenHashingService;
import com.bbthechange.inviter.service.RefreshTokenCookieService;
import com.bbthechange.inviter.service.RefreshTokenRotationService;
import com.bbthechange.inviter.service.AccountService;
import com.bbthechange.inviter.service.VerificationResult;
import com.bbthechange.inviter.service.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthController
 * 
 * Test Coverage:
 * - POST /auth/register - User registration functionality
 * - POST /auth/login - User authentication functionality
 * - Various error scenarios and edge cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private RefreshTokenHashingService hashingService;

    @Mock
    private RefreshTokenCookieService cookieService;

    @Mock
    private RefreshTokenRotationService rotationService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse mockResponse;

    private AuthController authController;

    private User testUser;
    private User existingUser;
    private LoginRequest loginRequest;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        // Manually create AuthController with mocked dependencies
        authController = new AuthController(
            jwtService,
            userRepository,
            passwordService,
            hashingService,
            cookieService,
            rotationService,
            refreshTokenRepository,
            accountService,
            rateLimitingService
        );
        
        testUserId = UUID.randomUUID();
        
        testUser = new User("+1234567890", "testuser", "Test User", "password123");
        testUser.setId(testUserId);
        
        existingUser = new User("+1234567890", "existinguser", "Existing User", "hashedpassword");
        existingUser.setId(testUserId);
        existingUser.setAccountStatus(AccountStatus.ACTIVE); // Set as ACTIVE for conflict test
        
        loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("+1234567890");
        loginRequest.setPassword("password123");
    }
    
    // Helper method to allow rate limiting for tests
    private void allowRateLimiting(String phoneNumber) {
        when(rateLimitingService.isResendCodeAllowed(phoneNumber)).thenReturn(true);
        when(rateLimitingService.isVerifyAllowed(phoneNumber)).thenReturn(true);
    }
    
    // Helper method specifically for resend code tests
    private void allowResendCodeRateLimiting(String phoneNumber) {
        when(rateLimitingService.isResendCodeAllowed(phoneNumber)).thenReturn(true);
    }
    
    // Helper method specifically for verify tests
    private void allowVerifyRateLimiting(String phoneNumber) {
        when(rateLimitingService.isVerifyAllowed(phoneNumber)).thenReturn(true);
    }

    @Nested
    @DisplayName("POST /auth/register - Registration Tests")
    class RegistrationTests {

        @Test
        @DisplayName("Should register new user successfully")
        void register_Success_NewUser() {
            // Arrange
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.empty());
            when(passwordService.encryptPassword("password123")).thenReturn("hashedpassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);


            // Act
            ResponseEntity<Map<String, String>> response = authController.register(testUser);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("User registered successfully. Please check your phone for a verification code.", response.getBody().get("message"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(passwordService).encryptPassword("password123");
            verify(userRepository).save(argThat(user ->
                "+1234567890".equals(user.getPhoneNumber()) &&
                "testuser".equals(user.getUsername()) &&
                "Test User".equals(user.getDisplayName()) &&
                AccountStatus.UNVERIFIED.equals(user.getAccountStatus())
            ));
            verify(accountService).sendVerificationCode("+1234567890");
        }

        @Test
        @DisplayName("Should update existing user without password (group invite case)")
        void register_Success_UpdateExistingUserWithoutPassword() {
            // Arrange - User exists but has no password (created via group invite)
            User existingUserWithoutPassword = new User("+1234567890", "olduser", "Old User", null);
            existingUserWithoutPassword.setId(testUserId);
            existingUserWithoutPassword.setAccountStatus(AccountStatus.UNVERIFIED);
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(existingUserWithoutPassword));
            when(passwordService.encryptPassword("password123")).thenReturn("hashedpassword");
            when(userRepository.save(any(User.class))).thenReturn(existingUserWithoutPassword);

            // Act
            ResponseEntity<Map<String, String>> response = authController.register(testUser);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals("User registered successfully. Please check your phone for a verification code.", response.getBody().get("message"));
            
            verify(userRepository).save(argThat(user ->
                "testuser".equals(user.getUsername()) &&
                "Test User".equals(user.getDisplayName()) &&
                "hashedpassword".equals(user.getPassword()) &&
                AccountStatus.UNVERIFIED.equals(user.getAccountStatus())
            ));
            verify(accountService).sendVerificationCode("+1234567890");
        }

        @Test
        @DisplayName("Should update existing UNVERIFIED user with password")
        void register_Success_UpdateExistingUnverifiedUser() {
            // Arrange
            User existingUnverifiedUser = new User("+1234567890", "olduser", "Old User", "oldpassword");
            existingUnverifiedUser.setId(testUserId);
            existingUnverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(existingUnverifiedUser));
            when(passwordService.encryptPassword("password123")).thenReturn("hashedpassword");
            when(userRepository.save(any(User.class))).thenReturn(existingUnverifiedUser);

            // Act
            ResponseEntity<Map<String, String>> response = authController.register(testUser);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals("User registered successfully. Please check your phone for a verification code.", response.getBody().get("message"));
            
            verify(userRepository).save(argThat(user ->
                "testuser".equals(user.getUsername()) &&
                "Test User".equals(user.getDisplayName()) &&
                "hashedpassword".equals(user.getPassword()) &&
                AccountStatus.UNVERIFIED.equals(user.getAccountStatus())
            ));
            verify(accountService).sendVerificationCode("+1234567890");
        }

        @Test
        @DisplayName("Should return CONFLICT when user already exists and is ACTIVE")
        void register_Conflict_UserAlreadyExistsAndActive() {
            // Arrange - existingUser is already set as ACTIVE in setUp()
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(existingUser));

            // Act
            ResponseEntity<Map<String, String>> response = authController.register(testUser);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ACCOUNT_ALREADY_EXISTS", response.getBody().get("error"));
            assertEquals("A user with this phone number is already registered and verified.", response.getBody().get("message"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(userRepository, never()).save(any());
            verify(passwordService, never()).encryptPassword(any());
            verify(accountService, never()).sendVerificationCode(any());
        }

        @Test
        @DisplayName("Should return CONFLICT when user exists with null status (backward compatibility)")
        void register_Conflict_UserExistsWithNullStatus() {
            // Arrange - User with null status treated as ACTIVE for backward compatibility
            User userWithNullStatus = new User("+1234567890", "existinguser", "Existing User", "hashedpassword");
            userWithNullStatus.setId(testUserId);
            userWithNullStatus.setAccountStatus(null); // null status = backward compatibility
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(userWithNullStatus));

            // Act
            ResponseEntity<Map<String, String>> response = authController.register(testUser);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ACCOUNT_ALREADY_EXISTS", response.getBody().get("error"));
            assertEquals("A user with this phone number is already registered and verified.", response.getBody().get("message"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(userRepository, never()).save(any());
            verify(passwordService, never()).encryptPassword(any());
            verify(accountService, never()).sendVerificationCode(any());
        }

        @Test
        @DisplayName("Should handle null password in registration")
        void register_Success_NullPassword() {
            // Arrange
            User userWithNullPassword = new User("+1234567890", "testuser", "Test User", null);
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.empty());
            when(passwordService.encryptPassword(null)).thenReturn("hashedpassword");
            when(userRepository.save(any(User.class))).thenReturn(userWithNullPassword);


            // Act
            ResponseEntity<Map<String, String>> response = authController.register(userWithNullPassword);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            verify(passwordService).encryptPassword(null);
        }

        @Test
        @DisplayName("Should still succeed registration when SMS service fails")
        void register_Success_WhenSmsServiceFails() {
            // Arrange
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.empty());
            when(passwordService.encryptPassword("password123")).thenReturn("hashedpassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            doThrow(new RuntimeException("SMS service error")).when(accountService)
                    .sendVerificationCode("+1234567890");

            // Act
            ResponseEntity<Map<String, String>> response = authController.register(testUser);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("User registered successfully. Please check your phone for a verification code.", response.getBody().get("message"));
            
            verify(userRepository).save(argThat(user ->
                "+1234567890".equals(user.getPhoneNumber()) &&
                "testuser".equals(user.getUsername()) &&
                "Test User".equals(user.getDisplayName()) &&
                AccountStatus.UNVERIFIED.equals(user.getAccountStatus())
            ));
            verify(accountService).sendVerificationCode("+1234567890");
        }

        @Test
        @DisplayName("Should call services with correct parameters")
        void register_Success_CallsServicesWithCorrectParameters() {
            // Arrange
            User specificTestUser = new User("+19995550001", "specificuser", "Specific User", "specificpass");
            when(userRepository.findByPhoneNumber("+19995550001")).thenReturn(Optional.empty());
            when(passwordService.encryptPassword("specificpass")).thenReturn("hashedspecificpass");
            when(userRepository.save(any(User.class))).thenReturn(specificTestUser);

            // Act
            ResponseEntity<Map<String, String>> response = authController.register(specificTestUser);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            
            verify(userRepository).findByPhoneNumber("+19995550001");
            verify(passwordService).encryptPassword("specificpass");
            verify(userRepository).save(argThat(user ->
                "+19995550001".equals(user.getPhoneNumber()) &&
                "specificuser".equals(user.getUsername()) &&
                "Specific User".equals(user.getDisplayName()) &&
                "hashedspecificpass".equals(user.getPassword()) &&
                AccountStatus.UNVERIFIED.equals(user.getAccountStatus())
            ));
            verify(accountService).sendVerificationCode("+19995550001");
        }
    }

    @Nested
    @DisplayName("POST /auth/login - Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void login_Success_ValidCredentials() {
            // Arrange
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(existingUser));
            when(passwordService.matches("password123", "hashedpassword")).thenReturn(true);
            when(jwtService.generateToken(testUserId.toString())).thenReturn("jwt-token");
            when(hashingService.generateRefreshToken()).thenReturn("refresh-token");
            when(hashingService.generateLookupHash("refresh-token")).thenReturn("lookup-hash");
            when(hashingService.generateSecurityHash("refresh-token")).thenReturn("security-hash");
            when(request.getHeader("X-Device-ID")).thenReturn("device-123");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);  // No X-Forwarded-For header
            when(request.getHeader("X-Client-Type")).thenReturn(null);     // Web client (not mobile)
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            
            // Mock cookie service for web client
            ResponseCookie mockCookie = ResponseCookie.from("refresh_token", "refresh-token").build();
            when(cookieService.createRefreshTokenCookie("refresh-token")).thenReturn(mockCookie);

            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("jwt-token", response.getBody().get("accessToken"));
            assertEquals(1800, response.getBody().get("expiresIn"));
            assertEquals("Bearer", response.getBody().get("tokenType"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(passwordService).matches("password123", "hashedpassword");
            verify(jwtService).generateToken(testUserId.toString());
            verify(hashingService).generateRefreshToken();
            verify(hashingService).generateLookupHash("refresh-token");
            verify(hashingService).generateSecurityHash("refresh-token");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when user doesn't exist")
        void login_Unauthorized_UserNotFound() {
            // Arrange
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.empty());


            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Invalid credentials", response.getBody().get("error"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(passwordService, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when user has no password")
        void login_Unauthorized_UserHasNoPassword() {
            // Arrange
            User userWithoutPassword = new User("+1234567890", "testuser", "Test User", null);
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(userWithoutPassword));


            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("Invalid credentials", response.getBody().get("error"));
            
            verify(passwordService, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when password doesn't match")
        void login_Unauthorized_InvalidPassword() {
            // Arrange
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(existingUser));
            when(passwordService.matches("password123", "hashedpassword")).thenReturn(false);


            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("Invalid credentials", response.getBody().get("error"));
            
            verify(passwordService).matches("password123", "hashedpassword");
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("Should handle empty phone number")
        void login_Unauthorized_EmptyPhoneNumber() {
            // Arrange
            loginRequest.setPhoneNumber("");
            when(userRepository.findByPhoneNumber("")).thenReturn(Optional.empty());


            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("Invalid credentials", response.getBody().get("error"));
        }

        @Test
        @DisplayName("Should handle null password in login request")
        void login_Unauthorized_NullPassword() {
            // Arrange
            loginRequest.setPassword(null);
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(existingUser));
            when(passwordService.matches(null, "hashedpassword")).thenReturn(false);


            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("Invalid credentials", response.getBody().get("error"));
        }

        @Test
        @DisplayName("Should return FORBIDDEN when user account is unverified")
        void login_Forbidden_UnverifiedAccount() {
            // Arrange
            User unverifiedUser = new User("+1234567890", "unverifieduser", "Unverified User", "hashedpassword");
            unverifiedUser.setId(testUserId);
            unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(unverifiedUser));
            when(passwordService.matches("password123", "hashedpassword")).thenReturn(true);

            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ACCOUNT_NOT_VERIFIED", response.getBody().get("error"));
            assertEquals("This account is not verified. Please complete the verification process.", response.getBody().get("message"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(passwordService).matches("password123", "hashedpassword");
            verify(jwtService, never()).generateToken(any());
            verify(hashingService, never()).generateRefreshToken();
        }

        @Test
        @DisplayName("Should login successfully with null AccountStatus (backward compatibility)")
        void login_Success_NullAccountStatusTreatedAsActive() {
            // Arrange
            User userWithNullStatus = new User("+1234567890", "testuser", "Test User", "hashedpassword");
            userWithNullStatus.setId(testUserId);
            userWithNullStatus.setAccountStatus(null); // null status for backward compatibility
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(userWithNullStatus));
            when(passwordService.matches("password123", "hashedpassword")).thenReturn(true);
            when(jwtService.generateToken(testUserId.toString())).thenReturn("jwt-token");
            when(hashingService.generateRefreshToken()).thenReturn("refresh-token");
            when(hashingService.generateLookupHash("refresh-token")).thenReturn("lookup-hash");
            when(hashingService.generateSecurityHash("refresh-token")).thenReturn("security-hash");
            when(request.getHeader("X-Device-ID")).thenReturn("device-123");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Client-Type")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            
            ResponseCookie mockCookie = ResponseCookie.from("refresh_token", "refresh-token").build();
            when(cookieService.createRefreshTokenCookie("refresh-token")).thenReturn(mockCookie);

            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("jwt-token", response.getBody().get("accessToken"));
            assertEquals(1800, response.getBody().get("expiresIn"));
            assertEquals("Bearer", response.getBody().get("tokenType"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(passwordService).matches("password123", "hashedpassword");
            verify(jwtService).generateToken(testUserId.toString());
            verify(hashingService).generateRefreshToken();
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Should login successfully with explicit ACTIVE AccountStatus")
        void login_Success_ExplicitActiveAccountStatus() {
            // Arrange
            User activeUser = new User("+1234567890", "activeuser", "Active User", "hashedpassword");
            activeUser.setId(testUserId);
            activeUser.setAccountStatus(AccountStatus.ACTIVE);
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(activeUser));
            when(passwordService.matches("password123", "hashedpassword")).thenReturn(true);
            when(jwtService.generateToken(testUserId.toString())).thenReturn("jwt-token");
            when(hashingService.generateRefreshToken()).thenReturn("refresh-token");
            when(hashingService.generateLookupHash("refresh-token")).thenReturn("lookup-hash");
            when(hashingService.generateSecurityHash("refresh-token")).thenReturn("security-hash");
            when(request.getHeader("X-Device-ID")).thenReturn("device-123");
            when(request.getHeader("X-Forwarded-For")).thenReturn(null);
            when(request.getHeader("X-Client-Type")).thenReturn(null);
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            
            ResponseCookie mockCookie = ResponseCookie.from("refresh_token", "refresh-token").build();
            when(cookieService.createRefreshTokenCookie("refresh-token")).thenReturn(mockCookie);

            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("jwt-token", response.getBody().get("accessToken"));
            assertEquals(1800, response.getBody().get("expiresIn"));
            assertEquals("Bearer", response.getBody().get("tokenType"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(passwordService).matches("password123", "hashedpassword");
            verify(jwtService).generateToken(testUserId.toString());
            verify(hashingService).generateRefreshToken();
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED for invalid credentials even with unverified status")
        void login_Forbidden_VerificationCheckAfterAuthentication() {
            // Arrange
            User unverifiedUserWithWrongPassword = new User("+1234567890", "unverifieduser", "Unverified User", "hashedpassword");
            unverifiedUserWithWrongPassword.setId(testUserId);
            unverifiedUserWithWrongPassword.setAccountStatus(AccountStatus.UNVERIFIED);
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(unverifiedUserWithWrongPassword));
            when(passwordService.matches("password123", "hashedpassword")).thenReturn(false); // Wrong password

            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Invalid credentials", response.getBody().get("error"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(passwordService).matches("password123", "hashedpassword");
            verify(jwtService, never()).generateToken(any());
            verify(hashingService, never()).generateRefreshToken();
        }

        @Test
        @DisplayName("Should not generate tokens for unverified users")
        void login_Forbidden_NoTokenGenerationForUnverified() {
            // Arrange
            User unverifiedUser = new User("+1234567890", "unverifieduser", "Unverified User", "hashedpassword");
            unverifiedUser.setId(testUserId);
            unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(unverifiedUser));
            when(passwordService.matches("password123", "hashedpassword")).thenReturn(true);

            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ACCOUNT_NOT_VERIFIED", response.getBody().get("error"));
            assertEquals("This account is not verified. Please complete the verification process.", response.getBody().get("message"));
            
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(passwordService).matches("password123", "hashedpassword");
            verify(jwtService, never()).generateToken(any());
            verify(hashingService, never()).generateRefreshToken();
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should return correct error response format for unverified users")
        void login_Forbidden_CorrectErrorResponseFormat() {
            // Arrange
            User unverifiedUser = new User("+1234567890", "unverifieduser", "Unverified User", "hashedpassword");
            unverifiedUser.setId(testUserId);
            unverifiedUser.setAccountStatus(AccountStatus.UNVERIFIED);
            
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(unverifiedUser));
            when(passwordService.matches("password123", "hashedpassword")).thenReturn(true);

            // Act
            ResponseEntity<Map<String, Object>> response = authController.login(loginRequest, request, mockResponse);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNotNull(response.getBody());
            
            // Verify exact response format
            Map<String, Object> responseBody = response.getBody();
            assertEquals(2, responseBody.size()); // Only two fields in response
            assertEquals("ACCOUNT_NOT_VERIFIED", responseBody.get("error"));
            assertEquals("This account is not verified. Please complete the verification process.", responseBody.get("message"));
            
            // Verify no tokens or sensitive information
            assertFalse(responseBody.containsKey("accessToken"));
            assertFalse(responseBody.containsKey("refreshToken"));
            assertFalse(responseBody.containsKey("expiresIn"));
            assertFalse(responseBody.containsKey("tokenType"));
            assertFalse(responseBody.containsKey("userId"));
        }
    }

    @Nested
    @DisplayName("LoginRequest DTO Tests")
    class LoginRequestTests {

        @Test
        @DisplayName("Should set and get phone number correctly")
        void loginRequest_PhoneNumber() {
            // Arrange
            LoginRequest request = new LoginRequest();
            

            // Act
            request.setPhoneNumber("+1234567890");
            
            // Assert
            assertEquals("+1234567890", request.getPhoneNumber());
        }

        @Test
        @DisplayName("Should set and get password correctly")
        void loginRequest_Password() {
            // Arrange
            LoginRequest request = new LoginRequest();
            

            // Act
            request.setPassword("testpassword");
            
            // Assert
            assertEquals("testpassword", request.getPassword());
        }

        @Test
        @DisplayName("Should handle null values")
        void loginRequest_NullValues() {
            // Arrange
            LoginRequest request = new LoginRequest();
            

            // Act & Assert
            assertNull(request.getPhoneNumber());
            assertNull(request.getPassword());
            
            request.setPhoneNumber(null);
            request.setPassword(null);
            
            assertNull(request.getPhoneNumber());
            assertNull(request.getPassword());
        }
    }

    @Nested
    @DisplayName("POST /auth/resend-code - Resend Code Tests")
    class ResendCodeTests {

        @Test
        @DisplayName("Should send verification code successfully for unverified user")
        void resendCode_WithValidUnverifiedUser_Returns200WithSuccessMessage() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            allowResendCodeRateLimiting("+15551234567");

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Verification code sent successfully", response.getBody().get("message"));
        }

        @Test
        @DisplayName("Should call AccountService with correct phone number")
        void resendCode_WithValidPhoneNumber_CallsAccountService() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+19995550001");
            allowResendCodeRateLimiting("+19995550001");

            // Act
            authController.resendCode(request);

            // Assert
            verify(accountService).sendVerificationCodeWithAccountCheck("+19995550001");
        }

        @Test
        @DisplayName("Should return correct response format")
        void resendCode_WithValidRequest_ReturnsCorrectResponseFormat() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            allowResendCodeRateLimiting("+15551234567");

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertNotNull(response.getBody());
            assertTrue(response.getBody().containsKey("message"));
            assertEquals("Verification code sent successfully", response.getBody().get("message"));
            assertEquals(1, response.getBody().size()); // Only one key in response
        }

        @Test
        @DisplayName("Should handle null phone number gracefully")
        void resendCode_WithNullPhoneNumber_CallsServiceWithNull() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber(null);
            allowResendCodeRateLimiting(null);

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert: Current implementation doesn't validate, so it calls service with null
            // Service may handle this gracefully or return error - either way is valid behavior
            verify(accountService).sendVerificationCodeWithAccountCheck(null);
            // The response will depend on how AccountService handles null phone numbers
        }

        @Test
        @DisplayName("Should handle empty phone number gracefully")
        void resendCode_WithEmptyPhoneNumber_CallsServiceWithEmptyString() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("");
            allowResendCodeRateLimiting("");

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert: Current implementation passes empty string to service
            verify(accountService).sendVerificationCodeWithAccountCheck("");
            // The response will depend on how AccountService handles empty phone numbers
        }

        @Test
        @DisplayName("Should handle AccountService exception")
        void resendCode_WhenAccountServiceThrows_Returns500WithErrorMessage() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            allowResendCodeRateLimiting("+15551234567");
            doThrow(new RuntimeException("SMS service error")).when(accountService)
                .sendVerificationCodeWithAccountCheck("+15551234567");

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
            assertEquals("Failed to send verification code", response.getBody().get("message"));
        }

        @Test
        @DisplayName("Should handle unexpected errors")
        void resendCode_WhenUnexpectedErrorOccurs_Returns500() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            allowResendCodeRateLimiting("+15551234567");
            doThrow(new NullPointerException("Unexpected error")).when(accountService)
                .sendVerificationCodeWithAccountCheck("+15551234567");

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
            assertEquals("Failed to send verification code", response.getBody().get("message"));
        }

        @Test
        @DisplayName("Should allow access without authentication")
        void resendCode_WithoutAuthentication_AllowsAccess() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            allowResendCodeRateLimiting("+15551234567");

            // Act: No authentication headers or tokens needed
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert: Should succeed (endpoint is public)
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @DisplayName("Should handle ResendCodeRequest null values")
        void resendCodeRequest_NullValues() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();

            // Act & Assert
            assertNull(request.getPhoneNumber());

            request.setPhoneNumber(null);
            assertNull(request.getPhoneNumber());

            request.setPhoneNumber("+15551234567");
            assertEquals("+15551234567", request.getPhoneNumber());
        }

        @Test
        @DisplayName("Should verify AccountService is called exactly once")
        void resendCode_Always_CallsAccountServiceOnce() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+18885554321");
            allowResendCodeRateLimiting("+18885554321");

            // Act
            authController.resendCode(request);

            // Assert
            verify(accountService, times(1)).sendVerificationCodeWithAccountCheck("+18885554321");
            verifyNoMoreInteractions(accountService);
        }

        @Test
        @DisplayName("Should return 404 when account not found")
        void resendCode_WhenAccountNotFound_Returns404() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            allowResendCodeRateLimiting("+15551234567");
            doThrow(new com.bbthechange.inviter.exception.AccountNotFoundException("No account found for this phone number."))
                .when(accountService).sendVerificationCodeWithAccountCheck("+15551234567");

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ACCOUNT_NOT_FOUND", response.getBody().get("error"));
            assertEquals("No account found for this phone number.", response.getBody().get("message"));
        }

        @Test
        @DisplayName("Should return 409 when account already verified")
        void resendCode_WhenAccountAlreadyVerified_Returns409() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            allowResendCodeRateLimiting("+15551234567");
            doThrow(new com.bbthechange.inviter.exception.AccountAlreadyVerifiedException("This account has already been verified."))
                .when(accountService).sendVerificationCodeWithAccountCheck("+15551234567");

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ACCOUNT_ALREADY_VERIFIED", response.getBody().get("error"));
            assertEquals("This account has already been verified.", response.getBody().get("message"));
        }
    }

    @Nested
    @DisplayName("POST /auth/verify - Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("Should return 200 OK when verification is successful")
        void verify_WithSuccessfulVerification_Returns200WithSuccessMessage() {
            // Given
            VerifyRequest request = new VerifyRequest();
            request.setPhoneNumber("+15551234567");
            request.setCode("123456");
            allowVerifyRateLimiting("+15551234567");
            
            when(accountService.verifyCode("+15551234567", "123456"))
                    .thenReturn(VerificationResult.success());

            // When
            ResponseEntity<Map<String, String>> response = authController.verify(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Account verified successfully", response.getBody().get("message"));
            verify(accountService).verifyCode("+15551234567", "123456");
        }

        @Test
        @DisplayName("Should return 400 Bad Request when verification code is expired")
        void verify_WithExpiredCode_Returns400WithExpiredError() {
            // Given
            VerifyRequest request = new VerifyRequest();
            request.setPhoneNumber("+15551234567");
            request.setCode("123456");
            allowVerifyRateLimiting("+15551234567");
            
            when(accountService.verifyCode("+15551234567", "123456"))
                    .thenReturn(VerificationResult.codeExpired());

            // When
            ResponseEntity<Map<String, String>> response = authController.verify(request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("VERIFICATION_CODE_EXPIRED", response.getBody().get("error"));
            assertEquals("The verification code has expired. Please request a new one.", response.getBody().get("message"));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when verification code is invalid")
        void verify_WithInvalidCode_Returns400WithInvalidError() {
            // Given
            VerifyRequest request = new VerifyRequest();
            request.setPhoneNumber("+15551234567");
            request.setCode("123456");
            allowVerifyRateLimiting("+15551234567");
            
            when(accountService.verifyCode("+15551234567", "123456"))
                    .thenReturn(VerificationResult.invalidCode());

            // When
            ResponseEntity<Map<String, String>> response = authController.verify(request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("INVALID_CODE", response.getBody().get("error"));
            assertEquals("The verification code is incorrect.", response.getBody().get("message"));
        }

        @Test
        @DisplayName("Should call AccountService with correct parameters from request")
        void verify_WithValidRequestFormat_CallsServiceWithCorrectParameters() {
            // Given
            VerifyRequest request = new VerifyRequest();
            request.setPhoneNumber("+19995550001");
            request.setCode("654321");
            allowVerifyRateLimiting("+19995550001");
            
            when(accountService.verifyCode("+19995550001", "654321"))
                    .thenReturn(VerificationResult.success());

            // When
            authController.verify(request);

            // Then
            verify(accountService, times(1)).verifyCode("+19995550001", "654321");
            verifyNoMoreInteractions(accountService);
        }
    }

    @Nested
    @DisplayName("POST /auth/resend-code - Rate Limiting Integration Tests")
    class ResendCodeRateLimitingTests {

        @Test
        @DisplayName("Should return 429 when rate limit is exceeded")
        void test_resendCode_RateLimitExceeded_Returns429() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            when(rateLimitingService.isResendCodeAllowed("+15551234567")).thenReturn(false);

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            verify(rateLimitingService).isResendCodeAllowed("+15551234567");
        }

        @Test
        @DisplayName("Should return correct error format when rate limited")
        void test_resendCode_RateLimitExceeded_CorrectErrorFormat() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            when(rateLimitingService.isResendCodeAllowed("+15551234567")).thenReturn(false);

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("TOO_MANY_REQUESTS", response.getBody().get("error"));
            assertEquals("You have requested too many codes. Please try again later.", response.getBody().get("message"));
            assertEquals(2, response.getBody().size()); // Only error and message fields
        }

        @Test
        @DisplayName("Should process normally when rate limit is allowed")
        void test_resendCode_RateLimitAllowed_ProcessesNormally() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            when(rateLimitingService.isResendCodeAllowed("+15551234567")).thenReturn(true);

            // Act
            ResponseEntity<Map<String, String>> response = authController.resendCode(request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Verification code sent successfully", response.getBody().get("message"));
            verify(rateLimitingService).isResendCodeAllowed("+15551234567");
            verify(accountService).sendVerificationCodeWithAccountCheck("+15551234567");
        }

        @Test
        @DisplayName("Should check rate limit before calling AccountService")
        void test_resendCode_ChecksRateLimitBeforeAccountService() {
            // Arrange
            AuthController.ResendCodeRequest request = new AuthController.ResendCodeRequest();
            request.setPhoneNumber("+15551234567");
            when(rateLimitingService.isResendCodeAllowed("+15551234567")).thenReturn(false);

            // Act
            authController.resendCode(request);

            // Assert
            verify(rateLimitingService).isResendCodeAllowed("+15551234567");
            verify(accountService, never()).sendVerificationCodeWithAccountCheck(any());
        }
    }

    @Nested
    @DisplayName("POST /auth/verify - Rate Limiting Integration Tests")
    class VerifyRateLimitingTests {

        @Test
        @DisplayName("Should return 429 when rate limit is exceeded")
        void test_verify_RateLimitExceeded_Returns429() {
            // Arrange
            VerifyRequest request = new VerifyRequest();
            request.setPhoneNumber("+15551234567");
            request.setCode("123456");
            when(rateLimitingService.isVerifyAllowed("+15551234567")).thenReturn(false);

            // Act
            ResponseEntity<Map<String, String>> response = authController.verify(request);

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            verify(rateLimitingService).isVerifyAllowed("+15551234567");
        }

        @Test
        @DisplayName("Should return correct error format when rate limited")
        void test_verify_RateLimitExceeded_CorrectErrorFormat() {
            // Arrange
            VerifyRequest request = new VerifyRequest();
            request.setPhoneNumber("+15551234567");
            request.setCode("123456");
            when(rateLimitingService.isVerifyAllowed("+15551234567")).thenReturn(false);

            // Act
            ResponseEntity<Map<String, String>> response = authController.verify(request);

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("TOO_MANY_REQUESTS", response.getBody().get("error"));
            assertEquals("You have made too many verification attempts. Please try again later.", response.getBody().get("message"));
            assertEquals(2, response.getBody().size()); // Only error and message fields
        }

        @Test
        @DisplayName("Should process normally when rate limit is allowed")
        void test_verify_RateLimitAllowed_ProcessesNormally() {
            // Arrange
            VerifyRequest request = new VerifyRequest();
            request.setPhoneNumber("+15551234567");
            request.setCode("123456");
            when(rateLimitingService.isVerifyAllowed("+15551234567")).thenReturn(true);
            when(accountService.verifyCode("+15551234567", "123456")).thenReturn(VerificationResult.success());

            // Act
            ResponseEntity<Map<String, String>> response = authController.verify(request);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Account verified successfully", response.getBody().get("message"));
            verify(rateLimitingService).isVerifyAllowed("+15551234567");
            verify(accountService).verifyCode("+15551234567", "123456");
        }

        @Test
        @DisplayName("Should check rate limit before calling AccountService")
        void test_verify_ChecksRateLimitBeforeAccountService() {
            // Arrange
            VerifyRequest request = new VerifyRequest();
            request.setPhoneNumber("+15551234567");
            request.setCode("123456");
            when(rateLimitingService.isVerifyAllowed("+15551234567")).thenReturn(false);

            // Act
            authController.verify(request);

            // Assert
            verify(rateLimitingService).isVerifyAllowed("+15551234567");
            verify(accountService, never()).verifyCode(any(), any());
        }
    }

    @Nested
    @DisplayName("RateLimitExceededException Tests")
    class RateLimitExceptionTests {

        @Test
        @DisplayName("Should preserve message correctly")
        void test_RateLimitExceededException_MessagePreserved() {
            // Arrange
            String expectedMessage = "Rate limit exceeded for test";

            // Act
            com.bbthechange.inviter.exception.RateLimitExceededException exception = 
                new com.bbthechange.inviter.exception.RateLimitExceededException(expectedMessage);

            // Assert
            assertEquals(expectedMessage, exception.getMessage());
            assertTrue(exception instanceof RuntimeException);
        }
    }
}