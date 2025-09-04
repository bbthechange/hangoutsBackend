package com.bbthechange.inviter.dto;

import java.util.List;

/**
 * Generic paginated result wrapper for repository queries.
 * Contains the result items and pagination token for next page.
 *
 * @param <T> The type of items in the result
 */
public class PaginatedResult<T> {
    
    private List<T> items;
    private String nextToken;
    
    public PaginatedResult() {
    }
    
    public PaginatedResult(List<T> items, String nextToken) {
        this.items = items;
        this.nextToken = nextToken;
    }
    
    public List<T> getItems() {
        return items;
    }
    
    public void setItems(List<T> items) {
        this.items = items;
    }
    
    public String getNextToken() {
        return nextToken;
    }
    
    public void setNextToken(String nextToken) {
        this.nextToken = nextToken;
    }
}