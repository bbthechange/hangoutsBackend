package com.bbthechange.inviter.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CacheKeyUtils {

    private CacheKeyUtils() {}

    /**
     * Generates a normalized cache key: lowercase(name)_round(lat,4)_round(lng,4)
     * Replaces spaces with hyphens, strips non-alphanumeric chars except hyphens.
     */
    public static String normalize(String name, double latitude, double longitude) {
        String normalizedName = name.toLowerCase().trim()
            .replaceAll("\\s+", "-")
            .replaceAll("[^a-z0-9\\-]", "");
        String roundedLat = BigDecimal.valueOf(latitude)
            .setScale(4, RoundingMode.HALF_UP).toPlainString();
        String roundedLng = BigDecimal.valueOf(longitude)
            .setScale(4, RoundingMode.HALF_UP).toPlainString();
        return normalizedName + "_" + roundedLat + "_" + roundedLng;
    }
}
