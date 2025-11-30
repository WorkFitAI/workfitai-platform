package org.workfitai.cvservice.dto;

import lombok.Data;

@Data
public class UserDto {
    private String userId;
    private String username;
    private String email;
    private String fullName;
}