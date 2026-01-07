package com.bbthechange.inviter.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Holds client application information extracted from request headers.
 *
 * Expected headers:
 * - X-App-Version: Semantic version (e.g., "1.2.3")
 * - X-Build-Number: Build number (e.g., "45")
 * - X-Client-Type: Client type ("ios", "android", "web")
 * - X-Device-ID: Unique device identifier
 * - User-Agent: Standard user agent string
 */
public record ClientInfo(
    String appVersion,
    String buildNumber,
    String clientType,
    String deviceId,
    String userAgent,
    String platform
) {
    public static final String REQUEST_ATTRIBUTE = "clientInfo";

    /**
     * Extract ClientInfo from request headers.
     */
    public static ClientInfo fromRequest(HttpServletRequest request) {
        String appVersion = request.getHeader("X-App-Version");
        String buildNumber = request.getHeader("X-Build-Number");
        String clientType = request.getHeader("X-Client-Type");
        String deviceId = request.getHeader("X-Device-ID");
        String userAgent = request.getHeader("User-Agent");

        // Derive platform from clientType or User-Agent
        String platform = derivePlatform(clientType, userAgent);

        return new ClientInfo(appVersion, buildNumber, clientType, deviceId, userAgent, platform);
    }

    /**
     * Get ClientInfo from request attribute (set by filter).
     */
    public static ClientInfo fromRequestAttribute(HttpServletRequest request) {
        return (ClientInfo) request.getAttribute(REQUEST_ATTRIBUTE);
    }

    /**
     * Check if this is a mobile client.
     */
    public boolean isMobile() {
        return "mobile".equals(clientType)
            || "ios".equals(clientType)
            || "android".equals(clientType);
    }

    /**
     * Check if this is an iOS client.
     */
    public boolean isIos() {
        return "ios".equals(clientType)
            || "ios".equals(platform)
            || (userAgent != null && userAgent.contains("iPhone"));
    }

    /**
     * Check if this is an Android client.
     */
    public boolean isAndroid() {
        return "android".equals(clientType)
            || "android".equals(platform)
            || (userAgent != null && userAgent.contains("Android"));
    }

    /**
     * Check if app version is at least the specified version.
     * Returns true if version is null (assumes latest).
     */
    public boolean isVersionAtLeast(String minVersion) {
        if (appVersion == null) {
            return true; // Assume latest if not specified
        }
        return compareVersions(appVersion, minVersion) >= 0;
    }

    /**
     * Get a short description for logging.
     */
    public String toLogString() {
        StringBuilder sb = new StringBuilder();
        if (clientType != null) {
            sb.append(clientType);
        } else if (platform != null) {
            sb.append(platform);
        } else {
            sb.append("unknown");
        }
        if (appVersion != null) {
            sb.append("/").append(appVersion);
            if (buildNumber != null) {
                sb.append(" (").append(buildNumber).append(")");
            }
        }
        return sb.toString();
    }

    private static String derivePlatform(String clientType, String userAgent) {
        if ("ios".equals(clientType)) return "ios";
        if ("android".equals(clientType)) return "android";
        if ("web".equals(clientType)) return "web";
        if ("mobile".equals(clientType)) {
            // Try to determine from User-Agent
            if (userAgent != null) {
                if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
                    return "ios";
                }
                if (userAgent.contains("Android")) {
                    return "android";
                }
            }
            return "mobile";
        }
        // Fallback to User-Agent parsing
        if (userAgent != null) {
            if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
                return "ios";
            }
            if (userAgent.contains("Android")) {
                return "android";
            }
        }
        return "web";
    }

    /**
     * Compare two semantic versions.
     * Returns: negative if v1 < v2, zero if equal, positive if v1 > v2
     */
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLength; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            // Handle versions like "1.2.3-beta" by taking only the numeric part
            String numericPart = part.split("-")[0];
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
