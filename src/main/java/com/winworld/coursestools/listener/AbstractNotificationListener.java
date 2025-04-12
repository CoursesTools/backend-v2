package com.winworld.coursestools.listener;

import com.winworld.coursestools.messaging.MessageBuilder;
import com.winworld.coursestools.service.EmailService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public abstract class AbstractNotificationListener<T> {
    protected final List<MessageBuilder<T>> messageBuilders;
    protected final EmailService emailService;

    protected void sendEmails(String email, T event) {
        messageBuilders.stream()
                .filter(builder -> builder.isApplicable(event))
                .forEach(builder -> {
                    emailService.send(email, builder.buildMessage(event));
                });
    }
}
