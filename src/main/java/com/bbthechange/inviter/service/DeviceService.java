package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Device;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeviceService {
    
    private final DynamoDbTable<Device> deviceTable;
    private final DynamoDbIndex<Device> userIndex;
    
    @Autowired
    public DeviceService(DynamoDbEnhancedClient dynamoDbClient) {
        this.deviceTable = dynamoDbClient.table("Devices", TableSchema.fromBean(Device.class));
        this.userIndex = deviceTable.index("UserIndex");
    }
    
    public Device registerDevice(String token, UUID userId, Device.Platform platform) {
        Device device = new Device(token, userId, platform);
        deviceTable.putItem(device);
        return device;
    }
    
    public void deactivateDevice(String token) {
        Optional<Device> existingDevice = getDeviceByToken(token);
        if (existingDevice.isPresent()) {
            Device device = existingDevice.get();
            device.setActive(false);
            device.setUpdatedAt(Instant.now());
            deviceTable.putItem(device);
        }
    }
    
    public void deleteDevice(String token) {
        Key key = Key.builder()
                .partitionValue(token)
                .build();
        deviceTable.deleteItem(key);
    }
    
    public Optional<Device> getDeviceByToken(String token) {
        Key key = Key.builder()
                .partitionValue(token)
                .build();
        Device device = deviceTable.getItem(key);
        return Optional.ofNullable(device);
    }
    
    public List<Device> getActiveDevicesForUser(UUID userId) {
        return userIndex.query(QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(userId.toString())
                .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .filter(Device::isActive)
                .collect(Collectors.toList());
    }
    
    public List<Device> getAllDevicesForUser(UUID userId) {
        return userIndex.query(QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(userId.toString())
                .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }
}