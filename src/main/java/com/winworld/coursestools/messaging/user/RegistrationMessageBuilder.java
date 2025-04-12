package com.winworld.coursestools.messaging.user;

import com.winworld.coursestools.event.UserCreateEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class RegistrationMessageBuilder extends MessageBuilder<UserCreateEvent> {
    public RegistrationMessageBuilder(
            TemplateEngine templateEngine,
            @Value("${emails.registration.template}")
            String templateName,
            @Value("${emails.registration.subject}")
            String subject
    ) {
        super(templateEngine, templateName, subject);
    }

    @Override
    protected void setVariables(Context context, UserCreateEvent event) {
    }
}
