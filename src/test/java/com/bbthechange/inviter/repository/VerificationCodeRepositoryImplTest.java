package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.VerificationCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationCodeRepositoryImplTest {

    @Mock
    private DynamoDbEnhancedClient mockEnhancedClient;

    @Mock
    private DynamoDbTable<VerificationCode> mockDynamoDbTable;

    private VerificationCodeRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        when(mockEnhancedClient.table(eq("VerificationCodes"), any(TableSchema.class)))
                .thenReturn(mockDynamoDbTable);
        
        repository = new VerificationCodeRepositoryImpl(mockEnhancedClient);
    }

    @Test
    void save_WithValidVerificationCode_CallsDynamoDbTablePutItem() {
        // Given
        VerificationCode verificationCode = new VerificationCode(
                "+15551234567", 
                "hashedCode123", 
                Instant.now().getEpochSecond() + 900
        );

        // When
        repository.save(verificationCode);

        // Then
        verify(mockDynamoDbTable).putItem(verificationCode);
    }

    @Test
    void save_WithVerificationCode_PassesCorrectObjectToTable() {
        // Given
        VerificationCode verificationCode = new VerificationCode(
                "+19995550001", 
                "anotherHashedCode", 
                Instant.now().getEpochSecond() + 1200
        );

        // When
        repository.save(verificationCode);

        // Then
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockDynamoDbTable).putItem(verificationCodeCaptor.capture());

        VerificationCode savedCode = verificationCodeCaptor.getValue();
        assertThat(savedCode.getPhoneNumber()).isEqualTo("+19995550001");
        assertThat(savedCode.getHashedCode()).isEqualTo("anotherHashedCode");
        assertThat(savedCode.getFailedAttempts()).isEqualTo(0);
    }

    @Test
    void findByPhoneNumber_WithValidPhoneNumber_CallsDynamoDbTableGetItem() {
        // Given
        String phoneNumber = "+15551234567";
        VerificationCode mockResult = new VerificationCode(phoneNumber, "hashedCode", Instant.now().getEpochSecond() + 900);
        when(mockDynamoDbTable.getItem(any(Key.class))).thenReturn(mockResult);

        // When
        repository.findByPhoneNumber(phoneNumber);

        // Then
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(mockDynamoDbTable).getItem(keyCaptor.capture());

        Key usedKey = keyCaptor.getValue();
        assertThat(usedKey.partitionKeyValue().s()).isEqualTo(phoneNumber);
    }

    @Test
    void findByPhoneNumber_WhenTableReturnsItem_ReturnsOptionalWithItem() {
        // Given
        String phoneNumber = "+15551234567";
        VerificationCode expectedCode = new VerificationCode(phoneNumber, "hashedCode", Instant.now().getEpochSecond() + 900);
        when(mockDynamoDbTable.getItem(any(Key.class))).thenReturn(expectedCode);

        // When
        Optional<VerificationCode> result = repository.findByPhoneNumber(phoneNumber);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPhoneNumber()).isEqualTo(phoneNumber);
        assertThat(result.get().getHashedCode()).isEqualTo("hashedCode");
    }

    @Test
    void findByPhoneNumber_WhenTableReturnsNull_ReturnsEmptyOptional() {
        // Given
        String phoneNumber = "+15551234567";
        when(mockDynamoDbTable.getItem(any(Key.class))).thenReturn(null);

        // When
        Optional<VerificationCode> result = repository.findByPhoneNumber(phoneNumber);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void deleteByPhoneNumber_WithValidPhoneNumber_CallsDynamoDbTableDeleteItem() {
        // Given
        String phoneNumber = "+15551234567";

        // When
        repository.deleteByPhoneNumber(phoneNumber);

        // Then
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(mockDynamoDbTable).deleteItem(keyCaptor.capture());

        Key usedKey = keyCaptor.getValue();
        assertThat(usedKey.partitionKeyValue().s()).isEqualTo(phoneNumber);
    }

    @Test
    void deleteByPhoneNumber_WithPhoneNumber_BuildsCorrectKey() {
        // Given
        String phoneNumber = "+19995550001";

        // When
        repository.deleteByPhoneNumber(phoneNumber);

        // Then
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(mockDynamoDbTable).deleteItem(keyCaptor.capture());

        Key usedKey = keyCaptor.getValue();
        assertThat(usedKey.partitionKeyValue().s()).isEqualTo(phoneNumber);
        assertThat(usedKey.sortKeyValue()).isEmpty(); // Should only have partition key
    }

    @Test
    void constructor_WithEnhancedClient_InitializesTableWithCorrectParameters() {
        // Given & When: constructor is called in setUp()
        
        // Then
        verify(mockEnhancedClient).table(eq("VerificationCodes"), any(TableSchema.class));
    }

    @Test
    void findByPhoneNumber_WithDifferentPhoneNumbers_CreatesCorrectKeys() {
        // Given
        String phoneNumber1 = "+15551234567";
        String phoneNumber2 = "+19995550001";
        when(mockDynamoDbTable.getItem(any(Key.class))).thenReturn(null);

        // When
        repository.findByPhoneNumber(phoneNumber1);
        repository.findByPhoneNumber(phoneNumber2);

        // Then
        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(mockDynamoDbTable, times(2)).getItem(keyCaptor.capture());

        assertThat(keyCaptor.getAllValues().get(0).partitionKeyValue().s()).isEqualTo(phoneNumber1);
        assertThat(keyCaptor.getAllValues().get(1).partitionKeyValue().s()).isEqualTo(phoneNumber2);
    }

    @Test
    void save_WithCompleteVerificationCode_PreservesAllFields() {
        // Given
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setPhoneNumber("+18885554321");
        verificationCode.setHashedCode("complexHashedCode");
        verificationCode.setFailedAttempts(2);
        verificationCode.setExpiresAt(Instant.now().getEpochSecond() + 600);

        // When
        repository.save(verificationCode);

        // Then
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockDynamoDbTable).putItem(verificationCodeCaptor.capture());

        VerificationCode savedCode = verificationCodeCaptor.getValue();
        assertThat(savedCode.getPhoneNumber()).isEqualTo("+18885554321");
        assertThat(savedCode.getHashedCode()).isEqualTo("complexHashedCode");
        assertThat(savedCode.getFailedAttempts()).isEqualTo(2);
        assertThat(savedCode.getExpiresAt()).isNotNull();
    }
}