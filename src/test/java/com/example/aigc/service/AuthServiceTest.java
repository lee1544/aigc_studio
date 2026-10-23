package com.example.aigc.service;

import com.example.aigc.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    @Test
    void userInfoWithoutAuthenticatedUserIsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> new AuthService().getUserInfo(null));
    }
}
