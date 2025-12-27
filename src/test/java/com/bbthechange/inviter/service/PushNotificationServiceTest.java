package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Hangout;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private ApnsClient apnsClient;

    @Mock
    private PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> pushNotificationFuture;

    @Mock
    private PushNotificationResponse<SimpleApnsPushNotification> pushNotificationResponse;

    @Mock
    private NotificationTextGenerator textGenerator;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @InjectMocks
    private PushNotificationService pushNotificationService;

    private static final String TEST_DEVICE_TOKEN = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
    private static final String TEST_BUNDLE_ID = "com.bbthechange.inviter";
    private static final String TEST_EVENT_TITLE = "Test Event";
    private static final String TEST_HOST_NAME = "Test Host";
    private static final String TEST_UPDATE_MESSAGE = "Event has been updated";
    private static final String TEST_GROUP_ID = "group-123";
    private static final String TEST_GROUP_NAME = "Friends Group";
    private static final String TEST_ADDER_NAME = "John";

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        ReflectionTestUtils.setField(pushNotificationService, "bundleId", TEST_BUNDLE_ID);
    }

    @Test
    void testSendInviteNotification_ApnsClientNull_SkipsNotification() {
        // Arrange
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", null);

        // Act
        pushNotificationService.sendInviteNotification(TEST_DEVICE_TOKEN, TEST_EVENT_TITLE, TEST_HOST_NAME);

        // Assert
        // Should not throw exception and should skip notification
        verifyNoInteractions(apnsClient);
    }

    @Test
    void testSendInviteNotification_Success_SendsNotification() throws ExecutionException, InterruptedException {
        // Arrange
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenReturn(pushNotificationResponse);
        when(pushNotificationResponse.isAccepted()).thenReturn(true);

        // Act
        pushNotificationService.sendInviteNotification(TEST_DEVICE_TOKEN, TEST_EVENT_TITLE, TEST_HOST_NAME);

        // Assert
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(pushNotificationResponse).isAccepted();
    }

    @Test
    void testSendInviteNotification_RejectedResponse_LogsError() throws ExecutionException, InterruptedException {
        // Arrange
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenReturn(pushNotificationResponse);
        when(pushNotificationResponse.isAccepted()).thenReturn(false);
        when(pushNotificationResponse.getRejectionReason()).thenReturn(Optional.of("InvalidToken"));

        // Act
        pushNotificationService.sendInviteNotification(TEST_DEVICE_TOKEN, TEST_EVENT_TITLE, TEST_HOST_NAME);

        // Assert
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(pushNotificationResponse).isAccepted();
        verify(pushNotificationResponse).getRejectionReason();
    }

    @Test
    void testSendInviteNotification_ExecutionException_HandlesGracefully() throws ExecutionException, InterruptedException {
        // Arrange
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenThrow(new ExecutionException("Network error", new RuntimeException()));

        // Act - should not throw exception
        pushNotificationService.sendInviteNotification(TEST_DEVICE_TOKEN, TEST_EVENT_TITLE, TEST_HOST_NAME);

        // Assert
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
    }

    @Test
    void testSendEventUpdateNotification_Success_SendsNotification() throws ExecutionException, InterruptedException {
        // Arrange
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);

        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenReturn(pushNotificationResponse);
        when(pushNotificationResponse.isAccepted()).thenReturn(true);

        // Act
        pushNotificationService.sendEventUpdateNotification(TEST_DEVICE_TOKEN, TEST_EVENT_TITLE, TEST_UPDATE_MESSAGE);

        // Assert
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(pushNotificationResponse).isAccepted();
    }

    // ========== sendGroupMemberAddedNotification Tests ==========

    @Test
    void sendGroupMemberAddedNotification_WhenApnsClientNull_SkipsWithoutError() {
        // Given
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", null);

        // When
        pushNotificationService.sendGroupMemberAddedNotification(
                TEST_DEVICE_TOKEN, TEST_GROUP_ID, TEST_GROUP_NAME, TEST_ADDER_NAME);

        // Then - should not throw exception and should skip notification
        verifyNoInteractions(apnsClient);
        verifyNoInteractions(meterRegistry); // No metrics emitted when skipping
    }

    @Test
    void sendGroupMemberAddedNotification_WhenAccepted_IncrementsSuccessMetric() throws ExecutionException, InterruptedException {
        // Given
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        when(textGenerator.getGroupMemberAddedBody(TEST_ADDER_NAME, TEST_GROUP_NAME))
                .thenReturn("John added you to the group Friends Group");
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenReturn(pushNotificationResponse);
        when(pushNotificationResponse.isAccepted()).thenReturn(true);

        // When
        pushNotificationService.sendGroupMemberAddedNotification(
                TEST_DEVICE_TOKEN, TEST_GROUP_ID, TEST_GROUP_NAME, TEST_ADDER_NAME);

        // Then
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(pushNotificationResponse).isAccepted();
        verify(meterRegistry).counter("apns_notification_total", "status", "success", "type", "group_member_added");
    }

    @Test
    void sendGroupMemberAddedNotification_WhenRejected_IncrementsRejectedMetric() throws ExecutionException, InterruptedException {
        // Given
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        when(textGenerator.getGroupMemberAddedBody(TEST_ADDER_NAME, TEST_GROUP_NAME))
                .thenReturn("John added you to the group Friends Group");
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenReturn(pushNotificationResponse);
        when(pushNotificationResponse.isAccepted()).thenReturn(false);
        when(pushNotificationResponse.getRejectionReason()).thenReturn(Optional.of("BadDeviceToken"));

        // When
        pushNotificationService.sendGroupMemberAddedNotification(
                TEST_DEVICE_TOKEN, TEST_GROUP_ID, TEST_GROUP_NAME, TEST_ADDER_NAME);

        // Then
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(pushNotificationResponse).isAccepted();
        verify(pushNotificationResponse).getRejectionReason();
        verify(meterRegistry).counter("apns_notification_total",
                "status", "rejected", "type", "group_member_added",
                "reason", "BadDeviceToken", "category", "expected");
    }

    @Test
    void sendGroupMemberAddedNotification_WhenExecutionException_IncrementsErrorMetric() throws ExecutionException, InterruptedException {
        // Given
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        when(textGenerator.getGroupMemberAddedBody(TEST_ADDER_NAME, TEST_GROUP_NAME))
                .thenReturn("John added you to the group Friends Group");
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenThrow(new ExecutionException("Network error", new RuntimeException()));

        // When - should not throw exception
        pushNotificationService.sendGroupMemberAddedNotification(
                TEST_DEVICE_TOKEN, TEST_GROUP_ID, TEST_GROUP_NAME, TEST_ADDER_NAME);

        // Then
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(meterRegistry).counter("apns_notification_total",
                "status", "error", "type", "group_member_added",
                "error_type", "execution", "category", "transient");
    }

    // ========== sendHangoutReminderNotification Tests ==========

    private static final String TEST_HANGOUT_ID = "hangout-123";
    private static final String TEST_HANGOUT_TITLE = "Pizza Night";
    private static final String TEST_REMINDER_BODY = "Pizza Night starts in 2 hours";

    private Hangout createTestHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(TEST_HANGOUT_ID);
        hangout.setTitle(TEST_HANGOUT_TITLE);
        return hangout;
    }

    @Test
    void sendHangoutReminderNotification_ApnsClientNull_SkipsQuietly() {
        // Given: APNs not configured
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", null);
        Hangout hangout = createTestHangout();

        // When
        pushNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

        // Then: No exception, no interactions
        verifyNoInteractions(apnsClient);
        verifyNoInteractions(meterRegistry);
    }

    @Test
    void sendHangoutReminderNotification_Success_EmitsSuccessMetric() throws ExecutionException, InterruptedException {
        // Given
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        Hangout hangout = createTestHangout();
        when(textGenerator.getHangoutReminderBody(TEST_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenReturn(pushNotificationResponse);
        when(pushNotificationResponse.isAccepted()).thenReturn(true);

        // When
        pushNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

        // Then
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(pushNotificationResponse).isAccepted();
        verify(meterRegistry).counter("apns_notification_total", "status", "success", "type", "hangout_reminder");
    }

    @Test
    void sendHangoutReminderNotification_PayloadContainsCorrectData() throws ExecutionException, InterruptedException {
        // Given
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        Hangout hangout = createTestHangout();
        when(textGenerator.getHangoutReminderBody(TEST_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenReturn(pushNotificationResponse);
        when(pushNotificationResponse.isAccepted()).thenReturn(true);

        // When
        pushNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

        // Then: Verify text generator was called with correct title
        verify(textGenerator).getHangoutReminderBody(TEST_HANGOUT_TITLE);
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
    }

    @Test
    void sendHangoutReminderNotification_Rejected_EmitsRejectedMetric() throws ExecutionException, InterruptedException {
        // Given
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        Hangout hangout = createTestHangout();
        when(textGenerator.getHangoutReminderBody(TEST_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenReturn(pushNotificationResponse);
        when(pushNotificationResponse.isAccepted()).thenReturn(false);
        when(pushNotificationResponse.getRejectionReason()).thenReturn(Optional.of("BadDeviceToken"));

        // When
        pushNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

        // Then
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(pushNotificationResponse).isAccepted();
        verify(pushNotificationResponse).getRejectionReason();
        verify(meterRegistry).counter("apns_notification_total",
                "status", "rejected", "type", "hangout_reminder",
                "reason", "BadDeviceToken", "category", "expected");
    }

    @Test
    void sendHangoutReminderNotification_ExecutionException_EmitsErrorMetric() throws ExecutionException, InterruptedException {
        // Given
        ReflectionTestUtils.setField(pushNotificationService, "apnsClient", apnsClient);
        Hangout hangout = createTestHangout();
        when(textGenerator.getHangoutReminderBody(TEST_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);
        when(apnsClient.sendNotification(any(SimpleApnsPushNotification.class))).thenReturn(pushNotificationFuture);
        when(pushNotificationFuture.get()).thenThrow(new ExecutionException("Network error", new RuntimeException()));

        // When - should not throw exception
        pushNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

        // Then
        verify(apnsClient).sendNotification(any(SimpleApnsPushNotification.class));
        verify(meterRegistry).counter("apns_notification_total",
                "status", "error", "type", "hangout_reminder",
                "error_type", "execution", "category", "transient");
    }
}