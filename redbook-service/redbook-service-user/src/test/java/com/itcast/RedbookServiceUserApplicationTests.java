package com.itcast;

import com.itcast.util.JwtUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedbookServiceUserApplicationTests {

    @Test
    void testToken() {
        String token = JwtUtil.createToken(1);
        System.out.println(token);
        String userId = JwtUtil.parseToken(token);
        System.out.println(userId);
        assertEquals("1", userId);
    }

}
