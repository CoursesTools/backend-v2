package com.winworld.coursestools.messaging.user;

import com.winworld.coursestools.event.UserCreateEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class SendPasswordMessageBuilder extends MessageBuilder<UserCreateEvent> {
    public SendPasswordMessageBuilder(
            TemplateEngine templateEngine,
            @Value("${emails.send-password.template}")
            String templateName,
            @Value("${emails.send-password.subject}")
            String subject
    ) {
        super(templateEngine, templateName, subject);
    }

    @Override
    public boolean isApplicable(UserCreateEvent event) {
        return event.getGeneratedPassword() != null;
    }

    @Override
    protected void setVariables(Context context, UserCreateEvent event) {
        context.setVariable("password", event.getGeneratedPassword());
    }
}
