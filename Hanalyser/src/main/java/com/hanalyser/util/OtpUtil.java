package com.hanalyser.util;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class OtpUtil {

    private final SecureRandom random = new SecureRandom();

    public String generateOtp() {
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}

