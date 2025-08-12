package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Hangout;

/**
 * Service interface for authorization checks.
 * This service is used to break circular dependencies between services.
 */
public interface AuthorizationService {
    
    
    /**
     * Check if a user can view a hangout.
     */
    boolean canUserViewHangout(String userId, Hangout hangout);
    
    /**
     * Check if a user can edit a hangout.
     */
    boolean canUserEditHangout(String userId, Hangout hangout);
}