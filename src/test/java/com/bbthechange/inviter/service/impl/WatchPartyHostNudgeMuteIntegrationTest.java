package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.SeriesNotificationPreference;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.SeriesNotificationPreferenceRepository;
import com.bbthechange.inviter.testutil.WatchPartyTestFixtures;
import com.bbthechange.inviter.util.NudgeTypes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test for the per-nudge-type mute behavior.
 *
 * <p>Uses a real {@link WatchPartyHostNudgeRecipientResolver} backed by an
 * in-memory {@link SeriesNotificationPreferenceRepository} that mirrors the
 * production repo's per-key semantics, then walks the lifecycle a real client
 * exercises: mute via the repository (the mute endpoint's terminal call), then
 * resolve recipients and assert filtering. The companion test confirms a mute
 * for a different nudge-type key does NOT affect HOST_NUDGE recipients —
 * proving per-nudge-type isolation lives in the data layer.</p>
 */
@ExtendWith(MockitoExtension.class)
class WatchPartyHostNudgeMuteIntegrationTest {

    private static final String GROUP_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SERIES_ID = "22222222-2222-2222-2222-222222222222";
    private static final String HANGOUT_ID = "33333333-3333-3333-3333-333333333333";
    private static final String OTHER_NUDGE = "OTHER_NUDGE"; // hypothetical future type

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private GroupRepository groupRepository;

    private InMemorySeriesNotificationPreferenceRepository preferenceRepository;
    private WatchPartyHostNudgeRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        preferenceRepository = new InMemorySeriesNotificationPreferenceRepository();
        resolver = new WatchPartyHostNudgeRecipientResolver(
                hangoutRepository, groupRepository, preferenceRepository, new SimpleMeterRegistry());
    }

    @Test
    void mutedHostNudgeUser_isExcludedFromResolvedRecipients() {
        EventSeries series = WatchPartyTestFixtures.inPersonSeries(SERIES_ID, GROUP_ID);
        series.setHangoutIds(new ArrayList<>());
        Hangout hangout = WatchPartyTestFixtures.inPersonHangout(HANGOUT_ID, SERIES_ID);

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
                .thenReturn(HangoutDetailData.builder()
                        .withAttendance(List.of(
                                level("u-muted", "GOING"),
                                level("u-unmuted", "GOING")))
                        .build());
        lenient().when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
                .thenReturn(Optional.empty());

        preferenceRepository.setMuted("u-muted", SERIES_ID, NudgeTypes.HOST_NUDGE, true);

        Set<String> recipients = resolver.resolve(series, hangout);

        assertThat(recipients).containsExactly("u-unmuted");
    }

    @Test
    void mutedForDifferentNudgeType_stillIncludedInHostNudgeRecipients() {
        EventSeries series = WatchPartyTestFixtures.inPersonSeries(SERIES_ID, GROUP_ID);
        series.setHangoutIds(new ArrayList<>());
        Hangout hangout = WatchPartyTestFixtures.inPersonHangout(HANGOUT_ID, SERIES_ID);

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
                .thenReturn(HangoutDetailData.builder()
                        .withAttendance(List.of(level("u-muted-for-other", "GOING")))
                        .build());
        lenient().when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
                .thenReturn(Optional.empty());

        // Muting an unrelated nudge type must not affect HOST_NUDGE recipients.
        preferenceRepository.setMuted("u-muted-for-other", SERIES_ID, OTHER_NUDGE, true);

        Set<String> recipients = resolver.resolve(series, hangout);

        assertThat(recipients).containsExactly("u-muted-for-other");
    }

    @Test
    void unmute_clearsTheOnlyMutedKey_andRowDisappears() {
        // Mute then unmute — proves the row vanishes when its last key is cleared,
        // so a stale row never silently keeps a user filtered out.
        preferenceRepository.setMuted("u-toggle", SERIES_ID, NudgeTypes.HOST_NUDGE, true);
        assertThat(preferenceRepository.find("u-toggle", SERIES_ID)).isPresent();

        preferenceRepository.setMuted("u-toggle", SERIES_ID, NudgeTypes.HOST_NUDGE, false);

        assertThat(preferenceRepository.find("u-toggle", SERIES_ID)).isEmpty();
    }

    private InterestLevel level(String userId, String status) {
        InterestLevel il = new InterestLevel();
        il.setUserId(userId);
        il.setStatus(status);
        return il;
    }

    /**
     * Simulates the production repository's semantics: per-key map updates,
     * row removal when the mute map empties. Concurrency-safe enough for tests.
     */
    private static final class InMemorySeriesNotificationPreferenceRepository
            implements SeriesNotificationPreferenceRepository {

        private final Map<String, SeriesNotificationPreference> store = new ConcurrentHashMap<>();

        @Override
        public Optional<SeriesNotificationPreference> find(String userId, String seriesId) {
            return Optional.ofNullable(store.get(key(userId, seriesId)));
        }

        @Override
        public Set<String> findMutedUsersForSeries(String seriesId,
                                                   String nudgeType,
                                                   Collection<String> candidateUserIds) {
            if (candidateUserIds == null || candidateUserIds.isEmpty() || nudgeType == null) {
                return Set.of();
            }
            Set<String> muted = new HashSet<>();
            for (String userId : new HashSet<>(candidateUserIds)) {
                SeriesNotificationPreference pref = store.get(key(userId, seriesId));
                if (pref == null || pref.getMutedNudgeTypes() == null) continue;
                if (Boolean.TRUE.equals(pref.getMutedNudgeTypes().get(nudgeType))) {
                    muted.add(userId);
                }
            }
            return muted;
        }

        @Override
        public void setMuted(String userId, String seriesId, String nudgeType, boolean muted) {
            String k = key(userId, seriesId);
            SeriesNotificationPreference pref = store.get(k);
            if (muted) {
                if (pref == null) {
                    pref = new SeriesNotificationPreference();
                    pref.setUserId(userId);
                    pref.setSeriesId(seriesId);
                    pref.setMutedNudgeTypes(new HashMap<>());
                    store.put(k, pref);
                }
                if (pref.getMutedNudgeTypes() == null) {
                    pref.setMutedNudgeTypes(new HashMap<>());
                }
                pref.getMutedNudgeTypes().put(nudgeType, true);
                return;
            }
            if (pref == null || pref.getMutedNudgeTypes() == null) {
                return;
            }
            pref.getMutedNudgeTypes().remove(nudgeType);
            if (pref.getMutedNudgeTypes().isEmpty()) {
                store.remove(k);
            }
        }

        @Override
        public void delete(String userId, String seriesId) {
            store.remove(key(userId, seriesId));
        }

        private static String key(String userId, String seriesId) {
            return userId + "|" + seriesId;
        }
    }
}
