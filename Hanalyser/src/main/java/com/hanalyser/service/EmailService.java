package com.hanalyser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String name, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Hanalyser - Your OTP Verification Code");

            String htmlContent = buildOtpEmailHtml(name, otp);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("OTP email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildOtpEmailHtml(String name, String otp) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', sans-serif; background: #000000; margin: 0; padding: 0; }
                    .container { max-width: 480px; margin: 40px auto; background: #050505; border-radius: 16px;
                                 border: 1px solid #2a2a2a; overflow: hidden; }
                    .header { background: #ffffff; padding: 32px;
                              text-align: center; }
                    .header h1 { color: #000000; margin: 0; font-size: 28px; font-weight: 800; letter-spacing: 2px; }
                    .header p { color: #000000; margin: 4px 0 0; opacity: 0.8; font-size: 13px; }
                    .body { padding: 36px 32px; }
                    .greeting { color: #ffffff; font-size: 18px; margin-bottom: 12px; }
                    .message { color: #d7d7d7; font-size: 14px; line-height: 1.6; margin-bottom: 28px; }
                    .otp-box { background: #101010; border: 1px solid #2a2a2a; border-radius: 12px;
                               padding: 24px; text-align: center; margin: 24px 0; }
                    .otp-label { color: #8a8a8a; font-size: 12px; letter-spacing: 3px; text-transform: uppercase; margin-bottom: 12px; }
                    .otp-code { font-size: 42px; font-weight: 800; color: #ffffff; letter-spacing: 12px; font-family: monospace; }
                    .expiry { color: #8a8a8a; font-size: 12px; margin-top: 12px; }
                    .warning { background: rgba(255,255,255,0.08); border: 1px solid #2a2a2a; border-radius: 8px;
                               padding: 12px 16px; color: #ffffff; font-size: 12px; margin-top: 20px; }
                    .footer { border-top: 1px solid #2a2a2a; padding: 20px 32px; text-align: center;
                              color: #8a8a8a; font-size: 11px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>HANALYSER</h1>
                        <p>Intelligent Stock Valuation Platform</p>
                    </div>
                    <div class="body">
                        <div class="greeting">Hello, %s!</div>
                        <div class="message">
                            You requested to verify your account on Hanalyser. Use the OTP below
                            to complete your verification. This code expires in <strong style="color:#ffffff">5 minutes</strong>.
                        </div>
                        <div class="otp-box">
                            <div class="otp-label">Verification Code</div>
                            <div class="otp-code">%s</div>
                            <div class="expiry">Expires in 5 minutes</div>
                        </div>
                        <div class="warning">
                            Never share this OTP with anyone. Hanalyser will never ask for your OTP.
                        </div>
                    </div>
                    <div class="footer">
                        2024 Hanalyser | Built by Harish | Not a trading platform
                    </div>
                </div>
            </body>
            </html>
            """.formatted(name, otp);
    }
}

