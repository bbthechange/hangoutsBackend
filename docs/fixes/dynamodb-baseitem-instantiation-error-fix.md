# DynamoDB BaseItem InstantiationError Fix

## Problem Description

### Error
```
java.lang.InstantiationError: com.bbthechange.inviter.model.BaseItem
    at software.amazon.awssdk.enhanced.dynamodb.mapper.StaticImmutableTableSchema.constructNewBuilder
```

### Root Cause
The DynamoDB Enhanced Client was attempting to instantiate the abstract `BaseItem` class directly during deserialization. This occurred because the repository was using:

```java
this.inviterTable = dynamoDbEnhancedClient.table("InviterTable", TableSchema.fromBean(BaseItem.class));
```

When DynamoDB returned data from a query/get operation, the Enhanced Client tried to create an instance of `BaseItem` to populate with the data, but abstract classes cannot be instantiated.

## Why This Happened

The application uses a single-table DynamoDB design where multiple entity types (Group, GroupMembership, HangoutPointer) all extend from a common abstract base class `BaseItem` and are stored in the same table. The original implementation incorrectly assumed that the Enhanced Client could handle polymorphic deserialization automatically.

## Solution: Type Discriminator Pattern

### 1. Added Type Discriminator to BaseItem

```java
// BaseItem.java
@DynamoDbBean
public abstract class BaseItem {
    // ... existing fields ...
    private String itemType;    // Type discriminator for polymorphic deserialization
    
    @DynamoDbAttribute("itemType")
    public String getItemType() {
        return itemType;
    }
    
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
}
```

### 2. Updated Concrete Classes to Set Their Type

```java
// Group.java
public Group() {
    super();
    setItemType("GROUP");
}

// GroupMembership.java
public GroupMembership() {
    super();
    setItemType("GROUP_MEMBERSHIP");
}

// HangoutPointer.java
public HangoutPointer() {
    super();
    setItemType("HANGOUT_POINTER");
}
```

### 3. Created PolymorphicGroupRepositoryImpl

The key innovation is creating separate `TableSchema` instances for each concrete type and using the type discriminator to determine which schema to use during deserialization:

```java
@Repository
@Primary
public class PolymorphicGroupRepositoryImpl implements GroupRepository {
    
    private final DynamoDbClient dynamoDbClient;
    private final TableSchema<Group> groupSchema;
    private final TableSchema<GroupMembership> membershipSchema;
    private final TableSchema<HangoutPointer> hangoutSchema;
    
    @Autowired
    public PolymorphicGroupRepositoryImpl(DynamoDbClient dynamoDbClient, QueryPerformanceTracker queryTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.groupSchema = TableSchema.fromBean(Group.class);
        this.membershipSchema = TableSchema.fromBean(GroupMembership.class);
        this.hangoutSchema = TableSchema.fromBean(HangoutPointer.class);
    }
    
    private BaseItem deserializeItem(Map<String, AttributeValue> itemMap) {
        AttributeValue typeAttr = itemMap.get("itemType");
        if (typeAttr == null || typeAttr.s() == null) {
            // Fallback to SK pattern matching for backward compatibility
            AttributeValue skAttr = itemMap.get("sk");
            if (skAttr != null) {
                String sk = skAttr.s();
                if (InviterKeyFactory.isMetadata(sk)) {
                    return groupSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isGroupMembership(sk)) {
                    return membershipSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isHangoutPointer(sk)) {
                    return hangoutSchema.mapToItem(itemMap);
                }
            }
            throw new IllegalStateException("Missing itemType discriminator");
        }
        
        String itemType = typeAttr.s();
        switch (itemType) {
            case "GROUP":
                return groupSchema.mapToItem(itemMap);
            case "GROUP_MEMBERSHIP":
                return membershipSchema.mapToItem(itemMap);
            case "HANGOUT_POINTER":
                return hangoutSchema.mapToItem(itemMap);
            default:
                throw new IllegalArgumentException("Unknown item type: " + itemType);
        }
    }
}
```

## Why This Solution Works

1. **No Abstract Class Instantiation**: The Enhanced Client never tries to instantiate `BaseItem` directly. Instead, it uses the concrete class schemas (Group, GroupMembership, etc.) which can be instantiated.

2. **Preserves Single-Table Design**: All items are still stored in the same DynamoDB table. The solution only changes how items are deserialized, not how they're stored.

3. **Type Safety**: Each item knows its concrete type via the `itemType` field, ensuring correct deserialization.

4. **Backward Compatibility**: The fallback logic using sort key patterns ensures existing data without the `itemType` field can still be read.

## Benefits of This Approach

- **Maintains Query Efficiency**: Can still query all related items in a single DynamoDB operation
- **Type-Safe Deserialization**: Each item is deserialized to its correct concrete type
- **Extensible**: Easy to add new entity types by adding new cases to the switch statement
- **No Data Migration Required**: Existing items work via the SK fallback mechanism

## Alternative Approaches Considered

1. **Making BaseItem Concrete**: Would violate object-oriented design principles and lose the benefits of abstraction
2. **Separate Tables**: Would break the single-table design pattern and require multiple queries
3. **Custom AttributeConverter**: More complex to implement and maintain

## Testing the Fix

The fix was verified by:
1. Successfully compiling the application
2. Starting the Spring Boot application without errors
3. Making API calls to endpoints that query the DynamoDB table
4. Confirming that queries return properly typed objects without InstantiationError

## Future Considerations

1. **Data Migration**: Consider adding a background job to update existing records with the `itemType` field
2. **Validation**: Add validation to ensure `itemType` is always set when creating new items
3. **Documentation**: Update developer documentation to explain the type discriminator pattern for new entity types