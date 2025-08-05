package com.bbthechange.inviter;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification test to ensure the hangout backend implementation compiles and works correctly.
 * This test validates the core models and key generation without external dependencies.
 */
class HangoutImplementationVerificationTest {
    
    @Test
    void testGroupCreation() {
        // Test that Group model works correctly
        Group group = new Group("Test Group", true);
        
        assertThat(group.getGroupId()).isNotNull();
        assertThat(group.getGroupName()).isEqualTo("Test Group");
        assertThat(group.isPublic()).isTrue();
        assertThat(group.getPk()).startsWith("GROUP#");
        assertThat(group.getSk()).isEqualTo("METADATA");
    }
    
    @Test
    void testGroupMembershipWithValidUUIDs() {
        // Test GroupMembership with valid UUIDs
        String groupId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        
        assertThat(membership.getGroupId()).isEqualTo(groupId);
        assertThat(membership.getUserId()).isEqualTo(userId);
        assertThat(membership.getGroupName()).isEqualTo("Test Group");
        assertThat(membership.getRole()).isEqualTo(GroupRole.MEMBER);
        assertThat(membership.getPk()).isEqualTo(InviterKeyFactory.getGroupPk(groupId));
        assertThat(membership.getSk()).isEqualTo(InviterKeyFactory.getUserSk(userId));
    }
    
    @Test
    void testHangoutPointer() {
        // Test HangoutPointer creation
        String groupId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        
        HangoutPointer pointer = new HangoutPointer(groupId, eventId, "Event Title");
        
        assertThat(pointer.getGroupId()).isEqualTo(groupId);
        assertThat(pointer.getHangoutId()).isEqualTo(eventId);
        assertThat(pointer.getTitle()).isEqualTo("Event Title");
        assertThat(pointer.getPk()).isEqualTo(InviterKeyFactory.getGroupPk(groupId));
        assertThat(pointer.getSk()).isEqualTo(InviterKeyFactory.getHangoutSk(eventId));
    }
    
    @Test
    void testPollCreation() {
        // Test Poll model
        String eventId = UUID.randomUUID().toString();
        
        Poll poll = new Poll(eventId, "Test Poll", "Description", false);
        
        assertThat(poll.getEventId()).isEqualTo(eventId);
        assertThat(poll.getPollId()).isNotNull();
        assertThat(poll.getTitle()).isEqualTo("Test Poll");
        assertThat(poll.getDescription()).isEqualTo("Description");
        assertThat(poll.isMultipleChoice()).isFalse();
        assertThat(poll.getPk()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(poll.getSk()).startsWith("POLL#");
    }
    
    @Test
    void testCarCreation() {
        // Test Car model
        String eventId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();
        
        Car car = new Car(eventId, driverId, "John Doe", 4);
        
        assertThat(car.getEventId()).isEqualTo(eventId);
        assertThat(car.getDriverId()).isEqualTo(driverId);
        assertThat(car.getDriverName()).isEqualTo("John Doe");
        assertThat(car.getTotalCapacity()).isEqualTo(4);
        assertThat(car.getAvailableSeats()).isEqualTo(3); // Driver takes one seat
        assertThat(car.getPk()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(car.getSk()).isEqualTo(InviterKeyFactory.getCarSk(driverId));
    }
    
    @Test
    void testInterestLevel() {
        // Test InterestLevel (formerly AttendanceRecord)
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        InterestLevel interest = new InterestLevel(eventId, userId, "Jane Doe", "GOING");
        
        assertThat(interest.getEventId()).isEqualTo(eventId);
        assertThat(interest.getUserId()).isEqualTo(userId);
        assertThat(interest.getUserName()).isEqualTo("Jane Doe");
        assertThat(interest.getStatus()).isEqualTo("GOING");
        assertThat(interest.getPk()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(interest.getSk()).isEqualTo(InviterKeyFactory.getAttendanceSk(userId));
    }
    
    @Test
    void testKeyFactoryValidation() {
        // Test that key factory properly validates UUIDs
        String validUUID = UUID.randomUUID().toString();
        String invalidUUID = "not-a-uuid";
        
        // Valid UUID should work
        assertThat(InviterKeyFactory.getGroupPk(validUUID)).isEqualTo("GROUP#" + validUUID);
        
        // Invalid UUID should throw exception
        try {
            InviterKeyFactory.getGroupPk(invalidUUID);
            throw new AssertionError("Should have thrown InvalidKeyException");
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("Invalid Group ID format");
        }
    }
}