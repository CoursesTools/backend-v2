package com.winworld.coursestools.messaging.subscription;

import com.winworld.coursestools.enums.SubscriptionEventType;
import com.winworld.coursestools.event.SubscriptionChangeStatusEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class TrialEndMessageBuilder extends MessageBuilder<SubscriptionChangeStatusEvent> {
    public TrialEndMessageBuilder(
            TemplateEngine templateEngine,
            @Value("${emails.trial-end.subject}}")
            String templateName,
            @Value("${emails.trial-end.template}")
            String subject
    ) {
        super(templateEngine, templateName, subject);
    }

    @Override
    protected void setVariables(Context context, SubscriptionChangeStatusEvent event) {}

    @Override
    public boolean isApplicable(SubscriptionChangeStatusEvent event) {
        return event.getEventType().equals(SubscriptionEventType.TRIAL_ENDED);
    }
}
