package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.PasswordResetRequest;
import com.bbthechange.inviter.model.ResetMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetRequestRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private DynamoDbTable<PasswordResetRequest> resetRequestTable;

    @Mock
    private DynamoDbIndex<PasswordResetRequest> phoneNumberIndex;

    @Mock
    private DynamoDbIndex<PasswordResetRequest> emailIndex;

    @Mock
    private PageIterable<PasswordResetRequest> pageIterable;

    @Mock
    private Page<PasswordResetRequest> page;

    private PasswordResetRequestRepository repository;

    @BeforeEach
    void setUp() {
        when(dynamoDbEnhancedClient.table(eq("PasswordResetRequest"), any(TableSchema.class)))
            .thenReturn(resetRequestTable);
        when(resetRequestTable.index("PhoneNumberIndex")).thenReturn(phoneNumberIndex);
        when(resetRequestTable.index("EmailIndex")).thenReturn(emailIndex);

        repository = new PasswordResetRequestRepository(dynamoDbEnhancedClient);
    }

    @Test
    void save_CreatesNewRecord() {
        // Given
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUserId("user-123");
        request.setPhoneNumber("+19285251044");
        request.setMethod(ResetMethod.PHONE);
        request.setCodeVerified(false);
        request.setTokenUsed(false);

        // When
        PasswordResetRequest result = repository.save(request);

        // Then
        verify(resetRequestTable).putItem(request);
        assertThat(result).isEqualTo(request);
    }

    @Test
    void save_OverwritesExistingRecord() {
        // Given
        String userId = "user-123";

        PasswordResetRequest oldRequest = new PasswordResetRequest();
        oldRequest.setUserId(userId);
        oldRequest.setPhoneNumber("+19285251044");

        PasswordResetRequest newRequest = new PasswordResetRequest();
        newRequest.setUserId(userId); // Same userId - will overwrite
        newRequest.setPhoneNumber("+19285251044");
        newRequest.setCodeVerified(true); // Different state

        // When
        repository.save(oldRequest);
        repository.save(newRequest);

        // Then
        verify(resetRequestTable, times(2)).putItem(any(PasswordResetRequest.class));
    }

    @Test
    void findById_WithExistingUserId_ReturnsRequest() {
        // Given
        String userId = "user-123";
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUserId(userId);

        when(resetRequestTable.getItem(any(Key.class))).thenReturn(request);

        // When
        Optional<PasswordResetRequest> result = repository.findById(userId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(userId);
        verify(resetRequestTable).getItem(any(Key.class));
    }

    @Test
    void findById_WithNonExistentUserId_ReturnsEmpty() {
        // Given
        String userId = "non-existent";
        when(resetRequestTable.getItem(any(Key.class))).thenReturn(null);

        // When
        Optional<PasswordResetRequest> result = repository.findById(userId);

        // Then
        assertThat(result).isEmpty();
        verify(resetRequestTable).getItem(any(Key.class));
    }

    @Test
    void findByPhoneNumber_WithExistingPhone_ReturnsRequest() {
        // Given
        String phoneNumber = "+19285251044";
        PasswordResetRequest request = new PasswordResetRequest();
        request.setPhoneNumber(phoneNumber);

        when(phoneNumberIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(Stream.of(page));
        when(page.items()).thenReturn(List.of(request));

        // When
        Optional<PasswordResetRequest> result = repository.findByPhoneNumber(phoneNumber);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPhoneNumber()).isEqualTo(phoneNumber);
        verify(phoneNumberIndex).query(any(QueryConditional.class));
    }

    @Test
    void findByPhoneNumber_WithNonExistentPhone_ReturnsEmpty() {
        // Given
        String phoneNumber = "+15551234567";
        when(phoneNumberIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(Stream.empty());

        // When
        Optional<PasswordResetRequest> result = repository.findByPhoneNumber(phoneNumber);

        // Then
        assertThat(result).isEmpty();
        verify(phoneNumberIndex).query(any(QueryConditional.class));
    }

    @Test
    void findByEmail_WithExistingEmail_ReturnsRequest() {
        // Given
        String email = "test@example.com";
        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail(email);

        when(emailIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(Stream.of(page));
        when(page.items()).thenReturn(List.of(request));

        // When
        Optional<PasswordResetRequest> result = repository.findByEmail(email);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo(email);
        verify(emailIndex).query(any(QueryConditional.class));
    }

    @Test
    void findByEmail_WithNonExistentEmail_ReturnsEmpty() {
        // Given
        String email = "nonexistent@example.com";
        when(emailIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(Stream.empty());

        // When
        Optional<PasswordResetRequest> result = repository.findByEmail(email);

        // Then
        assertThat(result).isEmpty();
        verify(emailIndex).query(any(QueryConditional.class));
    }

    @Test
    void delete_RemovesRecord() {
        // Given
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUserId("user-123");

        // When
        repository.delete(request);

        // Then
        verify(resetRequestTable).deleteItem(request);
    }

    @Test
    void deleteById_RemovesRecord() {
        // Given
        String userId = "user-123";

        // When
        repository.deleteById(userId);

        // Then
        verify(resetRequestTable).deleteItem(any(Key.class));
    }
}
