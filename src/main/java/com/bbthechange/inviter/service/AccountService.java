package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.model.AccountStatus;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.exception.AccountNotFoundException;
import com.bbthechange.inviter.exception.AccountAlreadyVerifiedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    private final SmsValidationService smsValidationService;
    private final UserRepository userRepository;

    public AccountService(SmsValidationService smsValidationService,
                         UserRepository userRepository) {
        this.smsValidationService = smsValidationService;
        this.userRepository = userRepository;
    }

    public void sendVerificationCode(String phoneNumber) {
        smsValidationService.sendVerificationCode(phoneNumber);
    }

    public void sendVerificationCodeWithAccountCheck(String phoneNumber) {
        // Check if user exists
        Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
        if (userOpt.isEmpty()) {
            throw new AccountNotFoundException("No account found for this phone number.");
        }

        User user = userOpt.get();
        
        // Check account status (treat null as ACTIVE for backward compatibility)
        AccountStatus status = user.getAccountStatus();
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
        
        if (status == AccountStatus.ACTIVE) {
            throw new AccountAlreadyVerifiedException("This account has already been verified.");
        }
        
        // User exists and is UNVERIFIED, proceed with sending code
        sendVerificationCode(phoneNumber);
    }

    public VerificationResult verifyCode(String phoneNumber, String submittedCode) {
        // Delegate verification to the strategy
        VerificationResult result = smsValidationService.verifyCode(phoneNumber, submittedCode);

        // If verification successful, update user account status to ACTIVE
        if (result.isSuccess()) {
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setAccountStatus(AccountStatus.ACTIVE);
                userRepository.save(user);
                logger.info("User account status updated to ACTIVE for phone number: {}", phoneNumber);
            } else {
                logger.warn("User not found for verified phone number: {}", phoneNumber);
            }
        }

        return result;
    }

}