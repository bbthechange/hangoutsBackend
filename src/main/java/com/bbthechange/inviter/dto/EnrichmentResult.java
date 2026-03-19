package com.bbthechange.inviter.dto;

public class EnrichmentResult {

    public enum Status { CACHED, ENRICHED, FAILED }

    private final Status status;
    private final EnrichmentData data;  // null when status == FAILED

    private EnrichmentResult(Status status, EnrichmentData data) {
        this.status = status;
        this.data = data;
    }

    public static EnrichmentResult cached(EnrichmentData data) {
        return new EnrichmentResult(Status.CACHED, data);
    }

    public static EnrichmentResult enriched(EnrichmentData data) {
        return new EnrichmentResult(Status.ENRICHED, data);
    }

    public static EnrichmentResult failed() {
        return new EnrichmentResult(Status.FAILED, null);
    }

    public Status getStatus() { return status; }
    public EnrichmentData getData() { return data; }
}
