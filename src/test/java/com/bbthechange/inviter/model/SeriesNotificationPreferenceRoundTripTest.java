package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.NudgeTypes;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips a SeriesNotificationPreference through the DynamoDB Enhanced
 * Client's TableSchema (the same mapping used by the live client) to verify
 * the map field survives serialization intact.
 */
class SeriesNotificationPreferenceRoundTripTest {

    @Test
    void mapField_RoundTripsThroughTableSchema() {
        String userId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();
        SeriesNotificationPreference pref = new SeriesNotificationPreference(userId, seriesId);
        pref.getMutedNudgeTypes().put(NudgeTypes.HOST_NUDGE, true);
        pref.getMutedNudgeTypes().put("LOCATION_NUDGE", false);

        TableSchema<SeriesNotificationPreference> schema =
            TableSchema.fromBean(SeriesNotificationPreference.class);

        Map<String, AttributeValue> serialized = schema.itemToMap(pref, true);

        // Keys preserved
        assertThat(serialized.get("pk").s()).isEqualTo(InviterKeyFactory.getUserPk(userId));
        assertThat(serialized.get("sk").s()).isEqualTo(InviterKeyFactory.getSeriesPrefSk(seriesId));
        // itemType discriminator
        assertThat(serialized.get("itemType").s()).isEqualTo("SERIES_NOTIFICATION_PREFERENCE");
        // Map field shape
        AttributeValue mapAttr = serialized.get("mutedNudgeTypes");
        assertThat(mapAttr).isNotNull();
        assertThat(mapAttr.m()).containsOnlyKeys(NudgeTypes.HOST_NUDGE, "LOCATION_NUDGE");
        assertThat(mapAttr.m().get(NudgeTypes.HOST_NUDGE).bool()).isTrue();
        assertThat(mapAttr.m().get("LOCATION_NUDGE").bool()).isFalse();

        // Deserialize back
        SeriesNotificationPreference roundTripped = schema.mapToItem(serialized);
        assertThat(roundTripped.getUserId()).isEqualTo(userId);
        assertThat(roundTripped.getSeriesId()).isEqualTo(seriesId);
        assertThat(roundTripped.getMutedNudgeTypes())
            .containsEntry(NudgeTypes.HOST_NUDGE, true)
            .containsEntry("LOCATION_NUDGE", false);
    }
}
