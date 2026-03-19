package com.bbthechange.inviter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for POST /places/enrich.
 * status: "CACHED" | "ENRICHED" | "FAILED"
 * data: null when status is FAILED
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaceEnrichResponse {

    private String status;
    private EnrichmentData data;

    public PlaceEnrichResponse() {}

    public static PlaceEnrichResponse from(EnrichmentResult result) {
        PlaceEnrichResponse resp = new PlaceEnrichResponse();
        resp.status = result.getStatus().name();
        resp.data = result.getData();
        return resp;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public EnrichmentData getData() { return data; }
    public void setData(EnrichmentData data) { this.data = data; }
}
