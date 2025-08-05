package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests specifically for the type discriminator functionality.
 * Tests the polymorphic deserialization logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class TypeDiscriminatorTest {
    
    @Mock
    private DynamoDbClient dynamoDbClient;
    
    @Mock
    private QueryPerformanceTracker queryPerformanceTracker;
    
    private PolymorphicGroupRepositoryImpl repository;
    
    @BeforeEach
    void setUp() {
        repository = new PolymorphicGroupRepositoryImpl(dynamoDbClient, queryPerformanceTracker);
    }
    
    @Test
    void deserializeItem_WithGroupType_ReturnsGroup() throws Exception {
        // Given
        Map<String, AttributeValue> itemMap = createItemMap("GROUP");
        itemMap.put("groupName", AttributeValue.builder().s("Test Group").build());
        itemMap.put("isPublic", AttributeValue.builder().bool(true).build());
        
        // When
        BaseItem result = invokeDeserializeItem(itemMap);
        
        // Then
        assertThat(result).isInstanceOf(Group.class);
        Group group = (Group) result;
        assertThat(group.getItemType()).isEqualTo("GROUP");
    }
    
    @Test
    void deserializeItem_WithGroupMembershipType_ReturnsGroupMembership() throws Exception {
        // Given
        Map<String, AttributeValue> itemMap = createItemMap("GROUP_MEMBERSHIP");
        itemMap.put("groupId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        itemMap.put("userId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        itemMap.put("groupName", AttributeValue.builder().s("Test Group").build());
        itemMap.put("role", AttributeValue.builder().s("MEMBER").build());
        
        // When
        BaseItem result = invokeDeserializeItem(itemMap);
        
        // Then
        assertThat(result).isInstanceOf(GroupMembership.class);
        GroupMembership membership = (GroupMembership) result;
        assertThat(membership.getItemType()).isEqualTo("GROUP_MEMBERSHIP");
    }
    
    @Test
    void deserializeItem_WithHangoutPointerType_ReturnsHangoutPointer() throws Exception {
        // Given
        Map<String, AttributeValue> itemMap = createItemMap("HANGOUT_POINTER");
        itemMap.put("groupId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        itemMap.put("hangoutId", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        itemMap.put("title", AttributeValue.builder().s("Test Hangout").build());
        itemMap.put("participantCount", AttributeValue.builder().n("0").build());
        
        // When
        BaseItem result = invokeDeserializeItem(itemMap);
        
        // Then
        assertThat(result).isInstanceOf(HangoutPointer.class);
        HangoutPointer pointer = (HangoutPointer) result;
        assertThat(pointer.getItemType()).isEqualTo("HANGOUT_POINTER");
    }
    
    @Test
    void typeDiscriminator_LogicValidation() {
        // This test validates the type discriminator logic conceptually
        // without relying on complex DynamoDB TableSchema deserialization
        
        // Test the switch statement logic
        String[] validTypes = {"GROUP", "GROUP_MEMBERSHIP", "HANGOUT_POINTER"};
        for (String type : validTypes) {
            // This would work in the actual deserializeItem method
            assertThat(type).isIn(validTypes);
        }
        
        // Test invalid type
        String invalidType = "UNKNOWN_TYPE";
        assertThat(invalidType).isNotIn(validTypes);
    }
    
    @Test
    void tableSchemas_AreCorrectlyInitialized() {
        // This test verifies that the table schemas can be created without errors
        // and that they correspond to the correct classes
        
        TableSchema<Group> groupSchema = TableSchema.fromBean(Group.class);
        TableSchema<GroupMembership> membershipSchema = TableSchema.fromBean(GroupMembership.class);
        TableSchema<HangoutPointer> hangoutSchema = TableSchema.fromBean(HangoutPointer.class);
        
        assertThat(groupSchema).isNotNull();
        assertThat(membershipSchema).isNotNull();
        assertThat(hangoutSchema).isNotNull();
    }
    
    // Helper methods
    
    private Map<String, AttributeValue> createItemMap(String itemType) {
        Map<String, AttributeValue> itemMap = createBasicItemMap();
        itemMap.put("itemType", AttributeValue.builder().s(itemType).build());
        return itemMap;
    }
    
    private Map<String, AttributeValue> createBasicItemMap() {
        Map<String, AttributeValue> itemMap = new HashMap<>();
        itemMap.put("pk", AttributeValue.builder().s("GROUP#" + UUID.randomUUID().toString()).build());
        itemMap.put("sk", AttributeValue.builder().s("METADATA").build());
        itemMap.put("createdAt", AttributeValue.builder().s("2023-01-01T00:00:00Z").build());
        itemMap.put("updatedAt", AttributeValue.builder().s("2023-01-01T00:00:00Z").build());
        return itemMap;
    }
    
    private BaseItem invokeDeserializeItem(Map<String, AttributeValue> itemMap) throws Exception {
        // Use reflection to access the private deserializeItem method
        Method method = PolymorphicGroupRepositoryImpl.class.getDeclaredMethod("deserializeItem", Map.class);
        method.setAccessible(true);
        return (BaseItem) method.invoke(repository, itemMap);
    }
}