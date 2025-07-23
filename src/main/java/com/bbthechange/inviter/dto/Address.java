package com.bbthechange.inviter.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;


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
}