package com.saha.amit.customer.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public class Test {
    public static void main(String[] args) {
        String secret = "0CBC458ACABBE3F02EA737C7DF416FA216FBA9B287932C38AB8AC61A69671CE4";
        byte[] decoded = Base64.getDecoder().decode(secret);
        System.out.println(decoded.length);
        System.out.println(Arrays.toString(decoded));




    }
}
