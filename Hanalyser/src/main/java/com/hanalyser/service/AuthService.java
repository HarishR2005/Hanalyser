package com.hanalyser.service;

import com.hanalyser.dto.AuthDTOs.*;
import com.hanalyser.entity.User;
import com.hanalyser.repository.UserRepository;
import com.hanalyser.security.JwtUtil;
import com.hanalyser.util.OtpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final OtpUtil otpUtil;

    @Value("${hanalyser.otp.expiry:5}")
    private int otpExpiryMinutes;

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered. Please login.");
        }

        String otp = otpUtil.generateOtp();

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(false)
                .verified(false)
                .otpCode(otp)
                .otpExpiry(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .build();

        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), user.getName(), otp);

        log.info("User registered: {}", user.getEmail());
        return new MessageResponse(
            "Registration successful! Please check your email for OTP verification.", true);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Invalid email or password.");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (!user.isVerified()) {
            // Resend OTP
            String otp = otpUtil.generateOtp();
            user.setOtpCode(otp);
            user.setOtpExpiry(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
            userRepository.save(user);
            emailService.sendOtpEmail(user.getEmail(), user.getName(), otp);
            return new AuthResponse("Email not verified. A new OTP has been sent to your email.", true);
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getName(), user.getEmail());
    }

    @Transactional
    public MessageResponse verifyOtp(OtpVerifyRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (user.getOtpCode() == null || !user.getOtpCode().equals(request.getOtp())) {
            throw new RuntimeException("Invalid OTP. Please try again.");
        }

        if (user.getOtpExpiry() == null || LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }

        user.setVerified(true);
        user.setEnabled(true);
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        log.info("User verified: {}", user.getEmail());
        return new MessageResponse("Email verified successfully! You can now login.", true);
    }

    @Transactional
    public MessageResponse resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found."));

        if (user.isVerified()) {
            throw new RuntimeException("Email is already verified.");
        }

        String otp = otpUtil.generateOtp();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        userRepository.save(user);
        emailService.sendOtpEmail(user.getEmail(), user.getName(), otp);

        return new MessageResponse("OTP sent to " + user.getEmail(), true);
    }
}

