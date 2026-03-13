package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.AttributeSuggestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that auto-promotes eligible attribute suggestion polls after 24 hours
 * if there is a single unopposed option.
 *
 * Gated behind {@code attribute.suggestion.auto-promote.enabled} — disabled by default.
 * Enable in production with:
 * {@code attribute.suggestion.auto-promote.enabled=true}
 */
@Component
@ConditionalOnProperty(name = "attribute.suggestion.auto-promote.enabled", havingValue = "true")
public class AttributeSuggestionAutoPromotionTask {

    private static final Logger logger = LoggerFactory.getLogger(AttributeSuggestionAutoPromotionTask.class);

    private final AttributeSuggestionService attributeSuggestionService;

    @Autowired
    public AttributeSuggestionAutoPromotionTask(AttributeSuggestionService attributeSuggestionService) {
        this.attributeSuggestionService = attributeSuggestionService;
    }

    /**
     * Runs hourly to find suggestion polls past their 24h window with no opposition and promote them.
     * Cron: every hour at :00.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runAutoPromotion() {
        logger.info("AttributeSuggestionAutoPromotionTask: starting run");
        try {
            attributeSuggestionService.promoteEligibleSuggestions();
            logger.info("AttributeSuggestionAutoPromotionTask: completed");
        } catch (Exception e) {
            logger.error("AttributeSuggestionAutoPromotionTask: failed with error: {}", e.getMessage(), e);
        }
    }
}
