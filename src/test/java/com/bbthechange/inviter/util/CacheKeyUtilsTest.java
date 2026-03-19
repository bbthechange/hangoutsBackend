package com.bbthechange.inviter.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheKeyUtilsTest {

    @Test
    void normalize_SpacesInName_ReplacedWithHyphens() {
        String key = CacheKeyUtils.normalize("Sushi Nakazawa", 40.7295, -74.0028);
        assertThat(key).startsWith("sushi-nakazawa_");
    }

    @Test
    void normalize_UppercaseName_ConvertedToLowercase() {
        String key = CacheKeyUtils.normalize("THE COFFEE SHOP", 37.7749, -122.4194);
        assertThat(key).startsWith("the-coffee-shop_");
    }

    @Test
    void normalize_SpecialCharsStripped() {
        String key = CacheKeyUtils.normalize("O'Brien's Bar & Grill", 40.7128, -74.0060);
        assertThat(key).startsWith("obriens-bar--grill_");
    }

    @Test
    void normalize_CoordinatesRoundedTo4DecimalPlaces() {
        String key = CacheKeyUtils.normalize("TestPlace", 40.72951234, -74.00289999);
        assertThat(key).isEqualTo("testplace_40.7295_-74.0029");
    }

    @Test
    void normalize_CoordinatesExactly4Decimals_Unchanged() {
        String key = CacheKeyUtils.normalize("Cafe", 40.7295, -74.0028);
        assertThat(key).isEqualTo("cafe_40.7295_-74.0028");
    }

    @Test
    void normalize_NegativeCoordinates_PreservesSign() {
        String key = CacheKeyUtils.normalize("Place", -33.8688, 151.2093);
        assertThat(key).isEqualTo("place_-33.8688_151.2093");
    }

    @Test
    void normalize_EmptyName_ProducesKeyWithJustCoords() {
        String key = CacheKeyUtils.normalize("", 40.7295, -74.0028);
        assertThat(key).isEqualTo("_40.7295_-74.0028");
    }

    @Test
    void normalize_SameNameDifferentCoords_ProducesDifferentKeys() {
        String key1 = CacheKeyUtils.normalize("Starbucks", 40.7295, -74.0028);
        String key2 = CacheKeyUtils.normalize("Starbucks", 37.7749, -122.4194);
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void normalize_NearbyCoordinates_SameKeyWithin11Meters() {
        // Coordinates differing at 5th decimal place should round to same 4-decimal value
        String key1 = CacheKeyUtils.normalize("Cafe", 40.72951, -74.00281);
        String key2 = CacheKeyUtils.normalize("Cafe", 40.72954, -74.00284);
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void normalize_CoordinateRoundingUp() {
        // .72955 should round up to .7296
        String key = CacheKeyUtils.normalize("X", 40.72955, -74.0028);
        assertThat(key).isEqualTo("x_40.7296_-74.0028");
    }

    @Test
    void normalize_ZeroCoordinates() {
        String key = CacheKeyUtils.normalize("Test", 0.0, 0.0);
        assertThat(key).isEqualTo("test_0.0000_0.0000");
    }

    @Test
    void normalize_NullName_ThrowsNullPointerException() {
        // null name is not a valid input — callers must ensure name is non-null
        assertThatThrownBy(() -> CacheKeyUtils.normalize(null, 40.7295, -74.0028))
                .isInstanceOf(NullPointerException.class);
    }
}
