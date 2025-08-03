package com.winworld.coursestools.messaging.user;

import com.winworld.coursestools.messaging.MessageBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class SendEmailCodeMessageBuilder extends MessageBuilder<String> {
    public SendEmailCodeMessageBuilder(
            TemplateEngine templateEngine,
            @Value("${emails.change-email.template}")
            String templateName,
            @Value("${emails.change-email.subject}")
            String subject
    ) {
        super(templateEngine, templateName, subject);
    }

    @Override
    protected void setVariables(Context context, String code) {
        context.setVariable("code", code);
    }
}
