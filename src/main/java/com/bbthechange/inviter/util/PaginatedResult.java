package com.bbthechange.inviter.util;

import java.util.List;

/**
 * Generic utility class for paginated database query results.
 * Contains both the results for the current page and the token needed to fetch the next page.
 * 
 * @param <T> The type of items in the results list
 */
public class PaginatedResult<T> {
    
    private final List<T> results;
    private final String nextToken;
    
    public PaginatedResult(List<T> results, String nextToken) {
        this.results = results;
        this.nextToken = nextToken;
    }
    
    /**
     * @return The list of items for this page
     */
    public List<T> getResults() {
        return results;
    }
    
    /**
     * @return The token needed to fetch the next page, or null if no more pages exist
     */
    public String getNextToken() {
        return nextToken;
    }
    
    /**
     * @return True if there are more pages available
     */
    public boolean hasMore() {
        return nextToken != null;
    }
    
    /**
     * @return The number of items in this page
     */
    public int size() {
        return results != null ? results.size() : 0;
    }
    
    /**
     * @return True if this page contains no items
     */
    public boolean isEmpty() {
        return results == null || results.isEmpty();
    }
}