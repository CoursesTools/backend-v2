package com.winworld.coursestools.service;

import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserAlertId;
import com.winworld.coursestools.entity.user.UserSocial;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.repository.user.UserAlertRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private UserDataService userDataService;

    @Mock
    private UserAlertRepository userAlertRepository;

    @Mock
    private UserSubscriptionService userSubscriptionService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlertService alertService;

    @Test
    void removeAlertsIfNoActiveSubscription_keepsAlertsWhileAnotherSubscriptionIsActive() {
        SubscriptionType type = new SubscriptionType();
        type.setId(1);
        when(subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS)).thenReturn(type);
        when(userSubscriptionService.getCurrentUserSubBySubTypeId(77, 1))
                .thenReturn(Optional.of(UserSubscription.builder().id(9).build()));

        alertService.removeAlertsIfNoActiveSubscription(77);

        verify(userAlertRepository, never()).deleteAllByUser_Id(77);
    }

    @Test
    void removeAlertsIfNoActiveSubscription_deletesAlertsAfterFullExpiry() {
        SubscriptionType type = new SubscriptionType();
        type.setId(1);
        when(subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS)).thenReturn(type);
        when(userSubscriptionService.getCurrentUserSubBySubTypeId(78, 1)).thenReturn(Optional.empty());

        alertService.removeAlertsIfNoActiveSubscription(78);

        verify(userAlertRepository).deleteAllByUser_Id(78);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unSubscribeOnAllAlerts_deletesAlertsWithoutSendingSubscriptionConfirmation() {
        alertService.unSubscribeOnAllAlerts(3074);

        verify(userAlertRepository).deleteAllByUser_Id(3074);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unSubscribeOnAlerts_deletesSelectedAlertsWithoutSendingSubscriptionConfirmation() {
        User user = new User();
        user.setId(77);
        UserSocial social = new UserSocial();
        social.setTelegramId("123456");
        user.setSocial(social);
        SubscriptionType type = new SubscriptionType();
        type.setId(1);
        List<UserAlertId> ids = List.of(new UserAlertId(77, 5));

        when(subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS)).thenReturn(type);
        when(userSubscriptionService.getCurrentUserSubBySubTypeId(77, 1))
                .thenReturn(Optional.of(UserSubscription.builder().id(9).build()));
        when(userDataService.getUserById(77)).thenReturn(user);
        when(userAlertRepository.findIdsByUserIdAndAlertIds(77, List.of(5))).thenReturn(ids);

        alertService.unSubscribeOnAlerts(77, List.of(5));

        verify(userAlertRepository).deleteAllById(ids);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
