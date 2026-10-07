package com.hanalyser.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDTOs {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    public static class OtpVerifyRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "OTP is required")
        private String otp;
    }

    @Data
    public static class ResendOtpRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private String name;
        private String email;
        private String message;
        private boolean requiresOtp;

        public AuthResponse(String token, String name, String email) {
            this.token = token;
            this.name = name;
            this.email = email;
            this.message = "Login successful";
            this.requiresOtp = false;
        }

        public AuthResponse(String message, boolean requiresOtp) {
            this.message = message;
            this.requiresOtp = requiresOtp;
        }
    }

    @Data
    public static class MessageResponse {
        private String message;
        private boolean success;

        public MessageResponse(String message, boolean success) {
            this.message = message;
            this.success = success;
        }
    }
}

