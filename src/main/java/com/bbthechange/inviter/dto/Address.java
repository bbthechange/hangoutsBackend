package com.bbthechange.inviter.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import com.fasterxml.jackson.annotation.JsonCreator;


@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Address {
    private String streetAddress;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    
    @JsonCreator
    public Address(String streetAddress) {
        this.streetAddress = streetAddress;
    }
}