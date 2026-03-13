package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.AttributeProposalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task that auto-adopts PENDING attribute proposals after 24 hours
 * if no alternatives have been submitted (silence = consent).
 *
 * Gated behind {@code attribute.proposal.auto-adopt.enabled} — disabled by default.
 * Enable in production with:
 * {@code attribute.proposal.auto-adopt.enabled=true}
 */
@Component
@ConditionalOnProperty(name = "attribute.proposal.auto-adopt.enabled", havingValue = "true")
public class AttributeProposalAutoAdoptionTask {

    private static final Logger logger = LoggerFactory.getLogger(AttributeProposalAutoAdoptionTask.class);

    private final AttributeProposalService attributeProposalService;

    @Autowired
    public AttributeProposalAutoAdoptionTask(AttributeProposalService attributeProposalService) {
        this.attributeProposalService = attributeProposalService;
    }

    /**
     * Runs hourly to find proposals past their 24h window with no alternatives and adopt them.
     * Cron: every hour at :00.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void runAutoAdoption() {
        logger.info("AttributeProposalAutoAdoptionTask: starting run");
        try {
            attributeProposalService.autoAdoptExpiredProposals();
            logger.info("AttributeProposalAutoAdoptionTask: completed");
        } catch (Exception e) {
            logger.error("AttributeProposalAutoAdoptionTask: failed with error: {}", e.getMessage(), e);
        }
    }
}
