package com.bbthechange.inviter.dto;

/**
 * Marker interface for items that can appear in a group feed.
 * This allows the feed to contain both standalone hangouts and multi-part series
 * in a single, heterogeneous list while maintaining type safety.
 * 
 * Implementations:
 * - HangoutSummaryDTO: Represents a standalone hangout event
 * - SeriesSummaryDTO: Represents a multi-part event series
 */
public interface FeedItem {
}