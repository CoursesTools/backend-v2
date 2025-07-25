package com.winworld.coursestools.messaging.payment;

import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class FirstPaymentMessageBuilder extends MessageBuilder<SubscriptionChangeStatusEvent> {
    public FirstPaymentMessageBuilder(
            TemplateEngine templateEngine,
            @Value("${emails.first-payment.template}")
            String templateName,
            @Value("${emails.first-payment.subject}")
            String subject
    ) {
        super(templateEngine, templateName, subject);
    }

    @Override
    protected void setVariables(Context context, SubscriptionChangeStatusEvent event) {
    }

    @Override
    public boolean isApplicable(SubscriptionChangeStatusEvent event) {
        return event.getEventType().equals(SubscriptionEventType.CREATED);
    }
}
