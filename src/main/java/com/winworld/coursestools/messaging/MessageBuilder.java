package com.winworld.coursestools.messaging;

import com.winworld.coursestools.dto.EmailMessageDto;
import lombok.RequiredArgsConstructor;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@RequiredArgsConstructor
public abstract class MessageBuilder<T> {
    protected final TemplateEngine templateEngine;
    protected final String templateName;
    protected final String subject;

    public EmailMessageDto buildMessage(T event) {
        Context context = new Context();
        setVariables(context, event);

        return new EmailMessageDto(
                templateEngine.process("emails/" + templateName, context),
                subject
        );
    }

    public boolean isApplicable(T event) {
        return true;
    }

    protected abstract void setVariables(Context context, T event);
}