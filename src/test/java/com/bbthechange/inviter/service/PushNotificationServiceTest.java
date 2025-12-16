package com.bbthechange.inviter.service;

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
}