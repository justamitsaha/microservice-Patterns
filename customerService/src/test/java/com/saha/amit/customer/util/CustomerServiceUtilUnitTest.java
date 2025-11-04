package com.saha.amit.customer.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerServiceUtilUnitTest {

    @Test
    void hashAndCompareWorks() {
        String salt = java.util.Base64.getEncoder().encodeToString("1234567890abcdef".getBytes());
        String h1 = CustomerServiceUtil.hashPassword("pass", salt);
        String h2 = CustomerServiceUtil.hashPassword("pass", salt);
        assertThat(h1).isEqualTo(h2);
        assertThat(CustomerServiceUtil.constantTimeEquals(h1, h2)).isTrue();
        assertThat(CustomerServiceUtil.constantTimeEquals(h1, h2 + "x")).isFalse();
    }

    @Test
    void jwtGenerateAndValidate() {
        CustomerServiceUtil util = new CustomerServiceUtil(
                "oycBHAYRCc8fMxKkRVx9FA4EC+pWAgmeRGxQFbLGb5Y=",
                60000,
                120000
        );
        String token = util.generateAccessToken("user-1", "u@e");
        Claims claims = util.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("email", String.class)).isEqualTo("u@e");
    }
}

