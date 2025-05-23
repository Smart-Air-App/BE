package com.example.smartair.entity.user;

import lombok.Getter;

@Getter
public enum Role {
    ADMIN("ROLE_ADMIN"),
    MANAGER("ROLE_MANAGER"),
    USER("ROLE_USER");

    Role(String value){
        this.value = value;
    }
    private String value;
}
