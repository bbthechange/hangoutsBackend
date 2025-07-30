package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.Invite;
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
class InviteRepositoryTest {

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private DynamoDbTable<Invite> inviteTable;

    @Mock
    private DynamoDbIndex<Invite> eventIndex;

    @Mock
    private DynamoDbIndex<Invite> userIndex;

    @Mock
    private PageIterable<Invite> pageIterable;

    @Mock
    private Page<Invite> page;

    private InviteRepository inviteRepository;

    @BeforeEach
    void setUp() {
        when(dynamoDbEnhancedClient.table(eq("Invites"), any(TableSchema.class))).thenReturn(inviteTable);
        when(inviteTable.index("EventIndex")).thenReturn(eventIndex);
        when(inviteTable.index("UserIndex")).thenReturn(userIndex);
        
        inviteRepository = new InviteRepository(dynamoDbEnhancedClient);
    }

    @Test
    void save_ShouldPutItemAndReturnInvite() {
        Invite invite = createTestInvite();

        Invite result = inviteRepository.save(invite);

        verify(inviteTable).putItem(invite);
        assertEquals(invite, result);
    }

    @Test
    void findById_WhenInviteExists_ShouldReturnInvite() {
        UUID inviteId = UUID.randomUUID();
        Invite invite = createTestInvite();
        invite.setId(inviteId);

        when(inviteTable.getItem(any(Key.class))).thenReturn(invite);

        Optional<Invite> result = inviteRepository.findById(inviteId);

        assertTrue(result.isPresent());
        assertEquals(invite, result.get());
        verify(inviteTable).getItem(any(Key.class));
    }

    @Test
    void findById_WhenInviteDoesNotExist_ShouldReturnEmpty() {
        UUID inviteId = UUID.randomUUID();

        when(inviteTable.getItem(any(Key.class))).thenReturn(null);

        Optional<Invite> result = inviteRepository.findById(inviteId);

        assertFalse(result.isPresent());
        verify(inviteTable).getItem(any(Key.class));
    }

    @Test
    void findByEventId_ShouldReturnInvitesForEvent() {
        UUID eventId = UUID.randomUUID();
        Invite invite1 = createTestInvite();
        Invite invite2 = createTestInvite();
        List<Invite> invites = List.of(invite1, invite2);

        when(eventIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(Stream.of(page));
        when(page.items()).thenReturn(invites);

        List<Invite> result = inviteRepository.findByEventId(eventId);

        assertEquals(invites, result);
        verify(eventIndex).query(any(QueryConditional.class));
    }

    @Test
    void findByUserId_ShouldReturnInvitesForUser() {
        UUID userId = UUID.randomUUID();
        Invite invite1 = createTestInvite();
        Invite invite2 = createTestInvite();
        List<Invite> invites = List.of(invite1, invite2);

        when(userIndex.query(any(QueryConditional.class))).thenReturn(pageIterable);
        when(pageIterable.stream()).thenReturn(Stream.of(page));
        when(page.items()).thenReturn(invites);

        List<Invite> result = inviteRepository.findByUserId(userId);

        assertEquals(invites, result);
        verify(userIndex).query(any(QueryConditional.class));
    }

    @Test
    void delete_ShouldDeleteInvite() {
        Invite invite = createTestInvite();

        inviteRepository.delete(invite);

        verify(inviteTable).deleteItem(invite);
    }

    private Invite createTestInvite() {
        Invite invite = new Invite();
        invite.setId(UUID.randomUUID());
        invite.setEventId(UUID.randomUUID());
        invite.setUserId(UUID.randomUUID());
        invite.setType(Invite.InviteType.GUEST);
        invite.setResponse(Invite.InviteResponse.NOT_RESPONDED);
        return invite;
    }
}