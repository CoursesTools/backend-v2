package com.winworld.coursestools.messaging.payment;

import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class PaymentRetrieveMessageBuilder extends MessageBuilder<SubscriptionChangeStatusEvent> {
    public PaymentRetrieveMessageBuilder(
            TemplateEngine templateEngine,
            @Value("${emails.payment-retrieve.template}")
            String templateName,
            @Value("${emails.payment-retrieve.subject}")
            String subject
    ) {
        super(templateEngine, templateName, subject);
    }

    @Override
    protected void setVariables(Context context, SubscriptionChangeStatusEvent event) {
    }

    @Override
    public boolean isApplicable(SubscriptionChangeStatusEvent event) {
        return event.getEventType().equals(SubscriptionEventType.EXTENDED);
    }
}
