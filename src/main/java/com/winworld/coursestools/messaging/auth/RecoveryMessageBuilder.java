package com.winworld.coursestools.messaging.auth;

import com.winworld.coursestools.dto.recovery.RecoveryEmailDto;
import com.winworld.coursestools.messaging.MessageBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class RecoveryMessageBuilder extends MessageBuilder<RecoveryEmailDto> {
    public RecoveryMessageBuilder(
            TemplateEngine templateEngine,
            @Value("${emails.recovery.template}")
            String templateName,
            @Value("${emails.recovery.subject}")
            String subject
    ) {
        super(templateEngine, templateName, subject);
    }

    @Override
    protected void setVariables(Context context, RecoveryEmailDto event) {
        context.setVariable("recoveryLink", event.getUrl());
    }
}
