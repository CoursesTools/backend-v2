package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.EmailMessageDto;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String from;

    @SneakyThrows
    public void send(String email, EmailMessageDto emailMessageDto) {
        MimeMessage templateMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(templateMessage);
        helper.setSubject(emailMessageDto.getSubject());
        helper.setTo(email);
        helper.setFrom(new InternetAddress(from, "CoursesTools"));
        helper.setText(emailMessageDto.getMessage(), true);
        javaMailSender.send(templateMessage);
    }
}
