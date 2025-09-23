package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.VerificationCode;

import java.util.Optional;

public interface VerificationCodeRepository {
    
    void save(VerificationCode verificationCode);
    
    Optional<VerificationCode> findByPhoneNumber(String phoneNumber);
    
    void deleteByPhoneNumber(String phoneNumber);
}