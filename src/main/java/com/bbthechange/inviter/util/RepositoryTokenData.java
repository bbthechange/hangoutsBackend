package com.bbthechange.inviter.util;

/**
 * Data class for repository pagination tokens.
 * Used for safe JSON serialization/deserialization with Jackson.
 */
public class RepositoryTokenData {
    
    private String gsi1pk;
    private String startTimestamp;
    private String pk;
    private String sk;
    
    public RepositoryTokenData() {}
    
    public RepositoryTokenData(String gsi1pk, String startTimestamp, String pk, String sk) {
        this.gsi1pk = gsi1pk;
        this.startTimestamp = startTimestamp;
        this.pk = pk;
        this.sk = sk;
    }
    
    public String getGsi1pk() {
        return gsi1pk;
    }
    
    public void setGsi1pk(String gsi1pk) {
        this.gsi1pk = gsi1pk;
    }
    
    public String getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(String startTimestamp) {
        this.startTimestamp = startTimestamp;
    }
    
    public String getPk() {
        return pk;
    }
    
    public void setPk(String pk) {
        this.pk = pk;
    }
    
    public String getSk() {
        return sk;
    }
    
    public void setSk(String sk) {
        this.sk = sk;
    }
}