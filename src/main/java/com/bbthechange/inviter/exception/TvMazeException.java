package com.bbthechange.inviter.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when TVMaze API operations fail.
 * Maps to HTTP 404 (not found) or 503 (service unavailable) based on error type.
 */
public class TvMazeException extends RuntimeException {

    private final ErrorType errorType;
    private final Integer seasonId;

    public enum ErrorType {
        /**
         * Season not found on TVMaze (HTTP 404).
         */
        SEASON_NOT_FOUND(HttpStatus.NOT_FOUND),

        /**
         * TVMaze API unavailable or rate limited (HTTP 503).
         */
        SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

        /**
         * No includable episodes found for the season (HTTP 400).
         */
        NO_EPISODES(HttpStatus.BAD_REQUEST);

        private final HttpStatus httpStatus;

        ErrorType(HttpStatus httpStatus) {
            this.httpStatus = httpStatus;
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }
    }

    public TvMazeException(ErrorType errorType, Integer seasonId, String message) {
        super(message);
        this.errorType = errorType;
        this.seasonId = seasonId;
    }

    public TvMazeException(ErrorType errorType, Integer seasonId, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.seasonId = seasonId;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public Integer getSeasonId() {
        return seasonId;
    }

    public HttpStatus getHttpStatus() {
        return errorType.getHttpStatus();
    }

    /**
     * Factory method for season not found.
     */
    public static TvMazeException seasonNotFound(Integer seasonId) {
        return new TvMazeException(
                ErrorType.SEASON_NOT_FOUND,
                seasonId,
                "TVMaze season not found: " + seasonId
        );
    }

    /**
     * Factory method for service unavailable.
     */
    public static TvMazeException serviceUnavailable(Integer seasonId, Throwable cause) {
        return new TvMazeException(
                ErrorType.SERVICE_UNAVAILABLE,
                seasonId,
                "TVMaze API is unavailable. Please try again later.",
                cause
        );
    }

    /**
     * Factory method for no episodes found.
     */
    public static TvMazeException noEpisodes(Integer seasonId) {
        return new TvMazeException(
                ErrorType.NO_EPISODES,
                seasonId,
                "No includable episodes found for TVMaze season: " + seasonId
        );
    }
}
