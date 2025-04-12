package com.winworld.coursestools.listener;

import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.event.UserCreateEvent;
import com.winworld.coursestools.messaging.MessageBuilder;
import com.winworld.coursestools.service.EmailService;
import com.winworld.coursestools.service.external.GeoLocationService;
import com.winworld.coursestools.service.user.UserDataService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
public class UserCreateListener extends AbstractNotificationListener<UserCreateEvent> {
    private final GeoLocationService geoLocationService;
    private final UserDataService userDataService;

    public UserCreateListener(
            List<MessageBuilder<UserCreateEvent>> messageBuilders,
            EmailService emailService,
            GeoLocationService geoLocationService,
            UserDataService userDataService
    ) {
        super(messageBuilders, emailService);
        this.geoLocationService = geoLocationService;
        this.userDataService = userDataService;
    }


    @Async //TODO Сделать свой pool
    @TransactionalEventListener
    public void setUserRegion(UserCreateEvent event) {
        String countryCode = geoLocationService.determineUserRegion(event.getForwardedFor());
        User user = userDataService.getUserById(event.getId());
        user.getProfile().setCountryCode(countryCode);
        userDataService.save(user);
    }

    @Async //TODO Сделать свой pool
    @TransactionalEventListener
    public void sendNotificationEmail(UserCreateEvent event) {
        sendEmails(event.getEmail(), event);
    }
}
