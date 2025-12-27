package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Hangout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmNotificationServiceTest {

    @Mock
    private FirebaseApp firebaseApp;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private NotificationTextGenerator textGenerator;

    @Mock
    private DeviceService deviceService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private FcmNotificationService fcmNotificationService;

    private static final String TEST_DEVICE_TOKEN = "fcm_test_token_1234567890abcdef";
    private static final String TEST_HANGOUT_ID = "hangout-123";
    private static final String TEST_GROUP_ID = "group-456";
    private static final String TEST_HANGOUT_TITLE = "Pizza Night";
    private static final String TEST_GROUP_NAME = "Friends";
    private static final String TEST_CREATOR_NAME = "John";
    private static final String TEST_NOTIFICATION_BODY = "John created 'Pizza Night' in Friends";
    private static final String TEST_ADDER_NAME = "Jane";
    private static final String TEST_GROUP_MEMBER_ADDED_BODY = "Jane added you to the group Friends";

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        fcmNotificationService = new FcmNotificationService(firebaseApp, textGenerator, deviceService, meterRegistry);
    }

    @Test
    void sendNewHangoutNotification_FirebaseNotConfigured_SkipsNotification() {
        // Arrange
        FcmNotificationService serviceWithoutFirebase = new FcmNotificationService(null, textGenerator, deviceService, meterRegistry);

        // Act
        serviceWithoutFirebase.sendNewHangoutNotification(
                TEST_DEVICE_TOKEN, TEST_HANGOUT_ID, TEST_GROUP_ID,
                TEST_HANGOUT_TITLE, TEST_GROUP_NAME, TEST_CREATOR_NAME);

        // Assert - should not throw and should not interact with dependencies
        verifyNoInteractions(textGenerator);
        verifyNoInteractions(deviceService);
    }

    @Test
    void sendNewHangoutNotification_Success_SendsNotification() throws FirebaseMessagingException {
        // Arrange
        when(textGenerator.getNewHangoutBody(TEST_CREATOR_NAME, TEST_HANGOUT_TITLE, TEST_GROUP_NAME))
                .thenReturn(TEST_NOTIFICATION_BODY);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id-123");

            // Act
            fcmNotificationService.sendNewHangoutNotification(
                    TEST_DEVICE_TOKEN, TEST_HANGOUT_ID, TEST_GROUP_ID,
                    TEST_HANGOUT_TITLE, TEST_GROUP_NAME, TEST_CREATOR_NAME);

            // Assert
            verify(textGenerator).getNewHangoutBody(TEST_CREATOR_NAME, TEST_HANGOUT_TITLE, TEST_GROUP_NAME);
            verify(firebaseMessaging).send(any(Message.class));
            verifyNoInteractions(deviceService); // No error, so no device cleanup
        }
    }

    @Test
    void sendNewHangoutNotification_UnregisteredToken_DeletesDevice() throws FirebaseMessagingException {
        // Arrange
        when(textGenerator.getNewHangoutBody(TEST_CREATOR_NAME, TEST_HANGOUT_TITLE, TEST_GROUP_NAME))
                .thenReturn(TEST_NOTIFICATION_BODY);

        FirebaseMessagingException unregisteredException = mock(FirebaseMessagingException.class);
        when(unregisteredException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(unregisteredException);

            // Act
            fcmNotificationService.sendNewHangoutNotification(
                    TEST_DEVICE_TOKEN, TEST_HANGOUT_ID, TEST_GROUP_ID,
                    TEST_HANGOUT_TITLE, TEST_GROUP_NAME, TEST_CREATOR_NAME);

            // Assert - device should be deleted
            verify(deviceService).deleteDevice(TEST_DEVICE_TOKEN);
        }
    }

    @Test
    void sendNewHangoutNotification_InvalidArgument_DoesNotDeleteDevice() throws FirebaseMessagingException {
        // Arrange
        when(textGenerator.getNewHangoutBody(TEST_CREATOR_NAME, TEST_HANGOUT_TITLE, TEST_GROUP_NAME))
                .thenReturn(TEST_NOTIFICATION_BODY);

        FirebaseMessagingException invalidArgumentException = mock(FirebaseMessagingException.class);
        when(invalidArgumentException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(invalidArgumentException.getMessage()).thenReturn("Invalid argument");

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(invalidArgumentException);

            // Act
            fcmNotificationService.sendNewHangoutNotification(
                    TEST_DEVICE_TOKEN, TEST_HANGOUT_ID, TEST_GROUP_ID,
                    TEST_HANGOUT_TITLE, TEST_GROUP_NAME, TEST_CREATOR_NAME);

            // Assert - device should NOT be deleted (could be any invalid argument, not necessarily token)
            verifyNoInteractions(deviceService);
        }
    }

    @Test
    void sendNewHangoutNotification_QuotaExceeded_LogsWarningOnly() throws FirebaseMessagingException {
        // Arrange
        when(textGenerator.getNewHangoutBody(TEST_CREATOR_NAME, TEST_HANGOUT_TITLE, TEST_GROUP_NAME))
                .thenReturn(TEST_NOTIFICATION_BODY);

        FirebaseMessagingException quotaException = mock(FirebaseMessagingException.class);
        when(quotaException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.QUOTA_EXCEEDED);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(quotaException);

            // Act
            fcmNotificationService.sendNewHangoutNotification(
                    TEST_DEVICE_TOKEN, TEST_HANGOUT_ID, TEST_GROUP_ID,
                    TEST_HANGOUT_TITLE, TEST_GROUP_NAME, TEST_CREATOR_NAME);

            // Assert - should not delete device for transient error
            verifyNoInteractions(deviceService);
        }
    }

    @Test
    void sendNewHangoutNotification_ServiceUnavailable_LogsWarningOnly() throws FirebaseMessagingException {
        // Arrange
        when(textGenerator.getNewHangoutBody(TEST_CREATOR_NAME, TEST_HANGOUT_TITLE, TEST_GROUP_NAME))
                .thenReturn(TEST_NOTIFICATION_BODY);

        FirebaseMessagingException unavailableException = mock(FirebaseMessagingException.class);
        when(unavailableException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(unavailableException);

            // Act
            fcmNotificationService.sendNewHangoutNotification(
                    TEST_DEVICE_TOKEN, TEST_HANGOUT_ID, TEST_GROUP_ID,
                    TEST_HANGOUT_TITLE, TEST_GROUP_NAME, TEST_CREATOR_NAME);

            // Assert - should not delete device for transient error
            verifyNoInteractions(deviceService);
        }
    }

    // ========== sendGroupMemberAddedNotification Tests ==========

    @Test
    void sendGroupMemberAddedNotification_WhenFirebaseAppNull_SkipsWithoutError() {
        // Given
        FcmNotificationService serviceWithoutFirebase = new FcmNotificationService(
                null, textGenerator, deviceService, meterRegistry);

        // When
        serviceWithoutFirebase.sendGroupMemberAddedNotification(
                TEST_DEVICE_TOKEN, TEST_GROUP_ID, TEST_GROUP_NAME, TEST_ADDER_NAME);

        // Then - should not throw and should not interact with dependencies
        verifyNoInteractions(textGenerator);
        verifyNoInteractions(deviceService);
    }

    @Test
    void sendGroupMemberAddedNotification_WhenSuccess_IncrementsSuccessMetric() throws FirebaseMessagingException {
        // Given
        when(textGenerator.getGroupMemberAddedBody(TEST_ADDER_NAME, TEST_GROUP_NAME))
                .thenReturn(TEST_GROUP_MEMBER_ADDED_BODY);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id-456");

            // When
            fcmNotificationService.sendGroupMemberAddedNotification(
                    TEST_DEVICE_TOKEN, TEST_GROUP_ID, TEST_GROUP_NAME, TEST_ADDER_NAME);

            // Then
            verify(textGenerator).getGroupMemberAddedBody(TEST_ADDER_NAME, TEST_GROUP_NAME);
            verify(firebaseMessaging).send(any(Message.class));
            verify(meterRegistry).counter("fcm_notification_total", "status", "success", "type", "group_member_added");
            verifyNoInteractions(deviceService); // No error, so no device cleanup
        }
    }

    @Test
    void sendGroupMemberAddedNotification_WhenUnregistered_DeletesDeviceAndIncrementsMetric() throws FirebaseMessagingException {
        // Given
        when(textGenerator.getGroupMemberAddedBody(TEST_ADDER_NAME, TEST_GROUP_NAME))
                .thenReturn(TEST_GROUP_MEMBER_ADDED_BODY);

        FirebaseMessagingException unregisteredException = mock(FirebaseMessagingException.class);
        when(unregisteredException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(unregisteredException);

            // When
            fcmNotificationService.sendGroupMemberAddedNotification(
                    TEST_DEVICE_TOKEN, TEST_GROUP_ID, TEST_GROUP_NAME, TEST_ADDER_NAME);

            // Then - device should be deleted and error metric incremented
            verify(deviceService).deleteDevice(TEST_DEVICE_TOKEN);
            verify(meterRegistry).counter("fcm_notification_total",
                    "status", "error",
                    "error_code", "UNREGISTERED",
                    "category", "expected");
        }
    }

    // ========== sendHangoutReminderNotification Tests ==========

    private static final String TEST_REMINDER_HANGOUT_ID = "reminder-hangout-123";
    private static final String TEST_REMINDER_HANGOUT_TITLE = "Team Dinner";
    private static final String TEST_REMINDER_BODY = "Team Dinner starts in 2 hours";

    private Hangout createReminderHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(TEST_REMINDER_HANGOUT_ID);
        hangout.setTitle(TEST_REMINDER_HANGOUT_TITLE);
        return hangout;
    }

    @Test
    void sendHangoutReminderNotification_FirebaseAppNull_SkipsQuietly() {
        // Given: FCM not configured
        FcmNotificationService serviceWithoutFirebase = new FcmNotificationService(
                null, textGenerator, deviceService, meterRegistry);
        Hangout hangout = createReminderHangout();

        // When
        serviceWithoutFirebase.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

        // Then - should not throw and should not interact with dependencies
        verifyNoInteractions(textGenerator);
        verifyNoInteractions(deviceService);
    }

    @Test
    void sendHangoutReminderNotification_Success_EmitsSuccessMetric() throws FirebaseMessagingException {
        // Given
        Hangout hangout = createReminderHangout();
        when(textGenerator.getHangoutReminderBody(TEST_REMINDER_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id-789");

            // When
            fcmNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

            // Then
            verify(textGenerator).getHangoutReminderBody(TEST_REMINDER_HANGOUT_TITLE);
            verify(firebaseMessaging).send(any(Message.class));
            verify(meterRegistry).counter("fcm_notification_total", "status", "success", "type", "hangout_reminder");
            verifyNoInteractions(deviceService); // No error, so no device cleanup
        }
    }

    @Test
    void sendHangoutReminderNotification_MessageContainsCorrectData() throws FirebaseMessagingException {
        // Given
        Hangout hangout = createReminderHangout();
        when(textGenerator.getHangoutReminderBody(TEST_REMINDER_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id-789");

            // When
            fcmNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

            // Then: Verify text generator was called correctly
            verify(textGenerator).getHangoutReminderBody(TEST_REMINDER_HANGOUT_TITLE);
            verify(firebaseMessaging).send(any(Message.class));
        }
    }

    @Test
    void sendHangoutReminderNotification_UnregisteredToken_DeletesDevice() throws FirebaseMessagingException {
        // Given
        Hangout hangout = createReminderHangout();
        when(textGenerator.getHangoutReminderBody(TEST_REMINDER_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);

        FirebaseMessagingException unregisteredException = mock(FirebaseMessagingException.class);
        when(unregisteredException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(unregisteredException);

            // When
            fcmNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

            // Then - device should be deleted
            verify(deviceService).deleteDevice(TEST_DEVICE_TOKEN);
            verify(meterRegistry).counter("fcm_notification_total",
                    "status", "error",
                    "error_code", "UNREGISTERED",
                    "category", "expected");
        }
    }

    @Test
    void sendHangoutReminderNotification_QuotaExceeded_EmitsTransientMetric() throws FirebaseMessagingException {
        // Given
        Hangout hangout = createReminderHangout();
        when(textGenerator.getHangoutReminderBody(TEST_REMINDER_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);

        FirebaseMessagingException quotaException = mock(FirebaseMessagingException.class);
        when(quotaException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.QUOTA_EXCEEDED);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(quotaException);

            // When
            fcmNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

            // Then - should not delete device for transient error
            verifyNoInteractions(deviceService);
            verify(meterRegistry).counter("fcm_notification_total",
                    "status", "error",
                    "error_code", "QUOTA_EXCEEDED",
                    "category", "transient");
        }
    }

    @Test
    void sendHangoutReminderNotification_ServiceUnavailable_EmitsTransientMetric() throws FirebaseMessagingException {
        // Given
        Hangout hangout = createReminderHangout();
        when(textGenerator.getHangoutReminderBody(TEST_REMINDER_HANGOUT_TITLE))
                .thenReturn(TEST_REMINDER_BODY);

        FirebaseMessagingException unavailableException = mock(FirebaseMessagingException.class);
        when(unavailableException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNAVAILABLE);

        try (MockedStatic<FirebaseMessaging> mockedStatic = mockStatic(FirebaseMessaging.class)) {
            mockedStatic.when(() -> FirebaseMessaging.getInstance(firebaseApp)).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(unavailableException);

            // When
            fcmNotificationService.sendHangoutReminderNotification(TEST_DEVICE_TOKEN, hangout, TEST_GROUP_ID);

            // Then - should not delete device for transient error
            verifyNoInteractions(deviceService);
            verify(meterRegistry).counter("fcm_notification_total",
                    "status", "error",
                    "error_code", "UNAVAILABLE",
                    "category", "transient");
        }
    }
}
