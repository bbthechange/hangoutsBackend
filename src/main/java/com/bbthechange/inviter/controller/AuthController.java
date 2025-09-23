package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.RefreshRequest;
import com.bbthechange.inviter.dto.RefreshTokenPair;
import com.bbthechange.inviter.dto.VerifyRequest;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.AccountNotFoundException;
import com.bbthechange.inviter.exception.AccountAlreadyVerifiedException;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final RefreshTokenHashingService hashingService;
    private final RefreshTokenCookieService cookieService;
    private final RefreshTokenRotationService rotationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountService accountService;
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody User user) {
        // Check if user already exists
        Optional<User> existingUser = userRepository.findByPhoneNumber(user.getPhoneNumber());
        
        if (existingUser.isPresent()) {
            User existing = existingUser.get();
            
            // If user exists and has a password (full account), check their status
            if (existing.getPassword() != null) {
                // Treat null accountStatus as ACTIVE for backward compatibility
                AccountStatus status = existing.getAccountStatus();
                if (status == null) {
                    status = AccountStatus.ACTIVE;
                }
                
                // If they're ACTIVE (verified), return conflict per spec
                if (status == AccountStatus.ACTIVE) {
                    Map<String, String> error = new HashMap<>();
                    error.put("error", "ACCOUNT_ALREADY_EXISTS");
                    error.put("message", "A user with this phone number is already registered and verified.");
                    return new ResponseEntity<>(error, HttpStatus.CONFLICT);
                }
                // If they're UNVERIFIED but have a password, we can proceed to update and resend code
            }
            // If user exists but has no password (created via group invite), we proceed to give them a full account
        }
        
        // TODO: Add input validation for phone number format and password requirements
        
        // Create or update user with UNVERIFIED status
        User userToSave;
        if (existingUser.isPresent()) {
            userToSave = existingUser.get();
            userToSave.setUsername(user.getUsername());
            userToSave.setDisplayName(user.getDisplayName());
            userToSave.setPassword(passwordService.encryptPassword(user.getPassword()));
            userToSave.setAccountStatus(AccountStatus.UNVERIFIED); // Ensure it's UNVERIFIED
        } else {
            userToSave = new User(user.getPhoneNumber(), user.getUsername(), user.getDisplayName(), 
                                 passwordService.encryptPassword(user.getPassword()));
            // Constructor already sets AccountStatus.UNVERIFIED
        }
        
        userRepository.save(userToSave);
        
        // Send verification code
        try {
            accountService.sendVerificationCode(user.getPhoneNumber());
        } catch (Exception e) {
            logger.error("Failed to send verification code for phone number: {}", user.getPhoneNumber(), e);
            // Note: We don't fail registration if SMS fails, user can use resend-code
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "User registered successfully. Please check your phone for a verification code.");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest loginRequest, 
            HttpServletRequest request,
            HttpServletResponse response) {
        
        // Authenticate user (existing logic)
        Optional<User> userOpt = userRepository.findByPhoneNumber(loginRequest.getPhoneNumber());
        if (userOpt.isEmpty() || userOpt.get().getPassword() == null || 
            !passwordService.matches(loginRequest.getPassword(), userOpt.get().getPassword())) {
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }
        
        User user = userOpt.get();
        
        // Check account verification status
        AccountStatus status = user.getAccountStatus();
        if (status == null) {
            status = AccountStatus.ACTIVE; // Backward compatibility
        }
        
        if (status == AccountStatus.UNVERIFIED) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "ACCOUNT_NOT_VERIFIED");
            error.put("message", "This account is not verified. Please complete the verification process.");
            return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
        }
        
        // Generate tokens
        String accessToken = jwtService.generateToken(user.getId().toString());
        String refreshToken = hashingService.generateRefreshToken();
        
        // Create refresh token record
        String deviceId = extractDeviceId(request);
        String ipAddress = extractClientIP(request);
        
        // Generate hashes for the refresh token
        String tokenHash = hashingService.generateLookupHash(refreshToken);
        String securityHash = hashingService.generateSecurityHash(refreshToken);
        
        // Create and save refresh token
        RefreshToken refreshTokenRecord = new RefreshToken(
            user.getId().toString(), 
            tokenHash, 
            securityHash, 
            deviceId, 
            ipAddress
        );
        refreshTokenRepository.save(refreshTokenRecord);
        
        // Check client type
        String clientType = request.getHeader("X-Client-Type");
        boolean isMobile = "mobile".equals(clientType);
        
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("accessToken", accessToken);
        responseBody.put("expiresIn", 1800); // 30 minutes
        responseBody.put("tokenType", "Bearer");
        
        if (isMobile) {
            // Mobile: Return refresh token in JSON
            responseBody.put("refreshToken", refreshToken);
        } else {
            // Web: Set refresh token as HttpOnly cookie
            ResponseCookie refreshCookie = cookieService.createRefreshTokenCookie(refreshToken);
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        }
        
        return new ResponseEntity<>(responseBody, HttpStatus.OK);
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) RefreshRequest refreshRequest) {
        
        String clientType = request.getHeader("X-Client-Type");
        boolean isMobile = "mobile".equals(clientType);
        
        // Extract refresh token based on client type
        String refreshToken;
        if (isMobile) {
            if (refreshRequest == null || refreshRequest.getRefreshToken() == null) {
                return new ResponseEntity<>(Map.of("error", "Refresh token required"), HttpStatus.BAD_REQUEST);
            }
            refreshToken = refreshRequest.getRefreshToken();
        } else {
            refreshToken = cookieService.extractRefreshTokenFromCookies(request);
            if (refreshToken == null) {
                return new ResponseEntity<>(Map.of("error", "Refresh token not found"), HttpStatus.UNAUTHORIZED);
            }
        }
        
        try {
            // Perform token rotation
            String ipAddress = extractClientIP(request);
            String userAgent = request.getHeader("User-Agent");
            RefreshTokenPair newTokens = rotationService.refreshTokens(refreshToken, ipAddress, userAgent);
            
            // Prepare response
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("accessToken", newTokens.getAccessToken());
            responseBody.put("expiresIn", 1800);
            responseBody.put("tokenType", "Bearer");
            
            if (isMobile) {
                // Mobile: Return new refresh token in JSON
                responseBody.put("refreshToken", newTokens.getRefreshToken());
            } else {
                // Web: Set new refresh token as HttpOnly cookie
                ResponseCookie refreshCookie = cookieService.createRefreshTokenCookie(newTokens.getRefreshToken());
                response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
            }
            
            return new ResponseEntity<>(responseBody, HttpStatus.OK);
            
        } catch (UnauthorizedException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.UNAUTHORIZED);
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) RefreshRequest logoutRequest) {
        
        String clientType = request.getHeader("X-Client-Type");
        boolean isMobile = "mobile".equals(clientType);
        
        // Extract refresh token
        String refreshToken;
        if (isMobile) {
            if (logoutRequest == null || logoutRequest.getRefreshToken() == null) {
                return new ResponseEntity<>(Map.of("error", "Refresh token required"), HttpStatus.BAD_REQUEST);
            }
            refreshToken = logoutRequest.getRefreshToken();
        } else {
            refreshToken = cookieService.extractRefreshTokenFromCookies(request);
        }
        
        if (refreshToken != null) {
            // Revoke the specific refresh token
            rotationService.revokeRefreshToken(refreshToken);
        }
        
        // Clear cookie for web clients
        if (!isMobile) {
            ResponseCookie clearCookie = cookieService.clearRefreshTokenCookie();
            response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
        }
        
        return new ResponseEntity<>(Map.of("message", "Successfully logged out"), HttpStatus.OK);
    }
    
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(HttpServletRequest request) {
        // Extract user ID from access token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return new ResponseEntity<>(Map.of("error", "Access token required"), HttpStatus.UNAUTHORIZED);
        }
        
        String accessToken = authHeader.substring(7);
        if (!jwtService.isTokenValid(accessToken)) {
            return new ResponseEntity<>(Map.of("error", "Invalid access token"), HttpStatus.UNAUTHORIZED);
        }
        
        String userId = jwtService.extractUserId(accessToken);
        
        // Delete all refresh tokens for this user
        rotationService.revokeAllUserTokens(userId);
        
        return new ResponseEntity<>(Map.of("message", "Successfully logged out from all devices"), HttpStatus.OK);
    }
    
    @PostMapping("/resend-code")
    public ResponseEntity<Map<String, String>> resendCode(@RequestBody ResendCodeRequest request) {
        // TODO: Add rate limiting (429 Too Many Requests)
        
        try {
            accountService.sendVerificationCodeWithAccountCheck(request.getPhoneNumber());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Verification code sent successfully");
            return new ResponseEntity<>(response, HttpStatus.OK);
            
        } catch (AccountNotFoundException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "ACCOUNT_NOT_FOUND");
            error.put("message", "No account found for this phone number.");
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
            
        } catch (AccountAlreadyVerifiedException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "ACCOUNT_ALREADY_VERIFIED");
            error.put("message", "This account has already been verified.");
            return new ResponseEntity<>(error, HttpStatus.CONFLICT);
            
        } catch (Exception e) {
            logger.error("Failed to send verification code for phone number: {}", request.getPhoneNumber(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "INTERNAL_ERROR");
            error.put("message", "Failed to send verification code");
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestBody VerifyRequest request) {
        // TODO: Add rate limiting (429 Too Many Requests)
        
        VerificationResult result = accountService.verifyCode(request.getPhoneNumber(), request.getCode());
        
        Map<String, String> response = new HashMap<>();
        
        if (result.isSuccess()) {
            response.put("message", "Account verified successfully");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else {
            switch (result.getStatus()) {
                case CODE_EXPIRED:
                    response.put("error", "VERIFICATION_CODE_EXPIRED");
                    response.put("message", result.getMessage());
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                case INVALID_CODE:
                    response.put("error", "INVALID_CODE");
                    response.put("message", result.getMessage());
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                default:
                    response.put("error", "INTERNAL_ERROR");
                    response.put("message", "An unexpected error occurred");
                    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }
    
    // Helper methods
    private String extractClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
    
    private String extractDeviceId(HttpServletRequest request) {
        // Extract device ID from headers or generate fingerprint
        return request.getHeader("X-Device-ID");
    }
    
    public static class LoginRequest {
        private String phoneNumber;
        private String password;
        
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
    
    public static class ResendCodeRequest {
        private String phoneNumber;
        
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    }
}