package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.User;
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
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private DynamoDbTable<User> userTable;

    @Mock
    private DynamoDbIndex<User> phoneNumberIndex;

    @Mock
    private PageIterable<User> pageIterable;

    @Mock
    private Page<User> page;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        when(dynamoDbEnhancedClient.table(eq("Users"), any(TableSchema.class))).thenReturn(userTable);
        when(userTable.index("PhoneNumberIndex")).thenReturn(phoneNumberIndex);
        
        userRepository = new UserRepository(dynamoDbEnhancedClient);
    }

    @Test
    void save_ShouldPutItemAndReturnUser() {
        User user = new User("1234567890", "testuser", "password");

        User result = userRepository.save(user);

        verify(userTable).putItem(user);
        assertEquals(user, result);
    }

    @Test
    void findById_WhenUserExists_ShouldReturnUser() {
        UUID userId = UUID.randomUUID();
        User user = new User("1234567890", "testuser", "password");
        user.setId(userId);

        when(userTable.getItem(any(Key.class))).thenReturn(user);

        Optional<User> result = userRepository.findById(userId);

        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(userTable).getItem(any(Key.class));
    }

    @Test
    void findById_WhenUserDoesNotExist_ShouldReturnEmpty() {
        UUID userId = UUID.randomUUID();

        when(userTable.getItem(any(Key.class))).thenReturn(null);

        Optional<User> result = userRepository.findById(userId);

        assertFalse(result.isPresent());
        verify(userTable).getItem(any(Key.class));
    }

    @Test
    void findByPhoneNumber_WhenUserExists_ShouldReturnUser() {
        String phoneNumber = "1234567890";
        User user = new User(phoneNumber, "testuser", "password");

        when(phoneNumberIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(Stream.of(page));
        when(page.items()).thenReturn(List.of(user));

        Optional<User> result = userRepository.findByPhoneNumber(phoneNumber);

        assertTrue(result.isPresent());
        assertEquals(user, result.get());
        verify(phoneNumberIndex).query(any(QueryConditional.class));
    }

    @Test
    void findByPhoneNumber_WhenUserDoesNotExist_ShouldReturnEmpty() {
        String phoneNumber = "1234567890";

        when(phoneNumberIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(Stream.of(page));
        when(page.items()).thenReturn(List.of());

        Optional<User> result = userRepository.findByPhoneNumber(phoneNumber);

        assertFalse(result.isPresent());
        verify(phoneNumberIndex).query(any(QueryConditional.class));
    }

    @Test
    void delete_ShouldDeleteUser() {
        User user = new User("1234567890", "testuser", "password");

        userRepository.delete(user);

        verify(userTable).deleteItem(user);
    }
}