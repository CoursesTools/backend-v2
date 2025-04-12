package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.subscription.SubscriptionReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.SubscriptionMapper;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.subscription.SubscriptionPlanRepository;
import com.winworld.coursestools.repository.subscription.SubscriptionTypeRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.winworld.coursestools.enums.SubscriptionEventType.TRIAL;
import static com.winworld.coursestools.enums.SubscriptionStatus.GRACE_PERIOD;
import static com.winworld.coursestools.enums.SubscriptionStatus.GRANTED;
import static com.winworld.coursestools.enums.SubscriptionStatus.PENDING;
import static com.winworld.coursestools.enums.SubscriptionStatus.TERMINATED;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    public static final int PAYMENT_GRACE_DAYS = 1;

    private final UserDataService userDataService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionTypeRepository subscriptionTypeRepository;
    private final UserSubscriptionService userSubscriptionService;

    @Value("${subscription.ct-pro.trial.days}")
    private int ctProTrialDays;

    public SubscriptionReadDto getSubscription(SubscriptionName name) {
        SubscriptionType subscriptionType = getSubscriptionTypeByName(name);
        return subscriptionMapper.toDto(subscriptionType);
    }

    public SubscriptionPlan getSubscriptionPlan(int planId) {
        return subscriptionPlanRepository.findById(planId).orElseThrow(
                () -> new EntityNotFoundException("Subscription plan not found")
        );
    }

    public SubscriptionType getSubscriptionTypeByName(SubscriptionName name) {
        return subscriptionTypeRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Subscription type not found"));
    }

    @Transactional
    public UserSubscriptionReadDto activateCtProTrialForUser(int userId) {
        User user = userDataService.getUserById(userId);
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO);
        if (userSubscriptionService.hasEverHadSubscriptionOfType(userId, subscriptionType.getId())) {
            throw new ConflictException("You can`t use anymore trial subscription");
        }

        LocalDateTime expiredAt = getNow()
                .plusDays(ctProTrialDays);
        UserSubscription userSubscription = UserSubscription.builder()
                .user(user)
                .status(PENDING)
                .plan(subscriptionType.getPlans().get(0))
                .price(BigDecimal.ZERO)
                .isTrial(true)
                .expiredAt(expiredAt)
                .build();
        user.addSubscription(userSubscription);

        eventPublisher.publishEvent(subscriptionMapper.toEvent(user, TRIAL));
        return userMapper.toDto(userSubscriptionService.save(userSubscription));

    }

    @Transactional
    public void deactivateExpiredSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionService
                .findAllExpiredSubscriptionsByStatus(GRANTED);
        usersSubscriptions.forEach(userSubscription -> {
            userSubscription.setStatus(GRACE_PERIOD);
            User user = userSubscription.getUser();
            Referral referred = user.getReferred();
            if (referred != null) {
                referred.setActive(false);
            }
            log.info("User {} subscription expired", user.getId());
            //TODO Отправить письмецо
            //TODO Сделать напоминание о 3 днях, 7 и т.д.
        });
    }

    @Transactional
    public void deactivateExpiredTrialSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionService
                .findAllWithExpiredTrialSubscription();
        usersSubscriptions.forEach(userSubscription -> {
            userSubscription.setStatus(TERMINATED);
            //TODO Ивент о деактиваиции подписки
        });
    }

    @Transactional
    public void deactivateExpiredGracePeriodSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionService
                .findAllExpiredSubscriptionsByStatus(GRACE_PERIOD);
        usersSubscriptions.forEach(userSubscription -> {
            userSubscription.setStatus(TERMINATED);
            User user = userSubscription.getUser();
            log.info("User {} subscription grace period expired", user.getId());
            //TODO Отправить письмецо
        });
    }

    public void updateUserSubscriptionAfterPayment(
            UserSubscription userSubscription,
            Order order,
            User user,
            Map<String, Object> paymentProviderData
    ) {
        if (userSubscription == null || userSubscription.getIsTrial()) {
            createNewSubscription(userSubscription, order, user, paymentProviderData);
            //TODO Сделать публикацию события
        } else if (userSubscription.getStatus().equals(GRACE_PERIOD)) {
            updateGracePeriodSubscription(userSubscription, order, paymentProviderData);
        } else {
            extendExistingSubscription(userSubscription, order, paymentProviderData);
        }
    }

    private void createNewSubscription(
            UserSubscription currentSubscription,
            Order order,
            User user,
            Map<String, Object> paymentProviderData
    ) {
        LocalDateTime expirationDate;
        UserSubscription newSubscription = new UserSubscription();
        SubscriptionPlan subscriptionPlan = order.getPlan();

        if (currentSubscription != null && currentSubscription.getIsTrial()) {
            expirationDate = currentSubscription.getExpiredAt();
            currentSubscription.setStatus(TERMINATED);
        } else {
            expirationDate = getNow();
        }

        expirationDate = expirationDate
                .plusDays(PAYMENT_GRACE_DAYS + subscriptionPlan.getDurationDays());

        newSubscription.setPlan(subscriptionPlan);
        newSubscription.setPrice(subscriptionPlan.getPrice());
        newSubscription.setPaymentMethod(order.getPaymentMethod());
        newSubscription.setStatus(PENDING);
        newSubscription.setPaymentProviderData(paymentProviderData);
        newSubscription.setIsTrial(false);
        newSubscription.setExpiredAt(expirationDate);

        user.addSubscription(newSubscription);
        userSubscriptionService.save(newSubscription);
    }

    private void updateGracePeriodSubscription(
            UserSubscription subscription,
            Order order,
            Map<String, Object> paymentProviderData
    ) {
        var plan = order.getPlan();
        LocalDateTime expirationDate = getNow()
                .plusDays(PAYMENT_GRACE_DAYS + plan.getDurationDays());

        subscription.setStatus(PENDING);
        subscription.setPaymentMethod(order.getPaymentMethod());
        subscription.setPaymentProviderData(paymentProviderData);
        subscription.setPlan(order.getPlan());
        subscription.setExpiredAt(expirationDate);

        //TODO Сделать отмену подписки страйп если сменился способ оплаты
    }

    private void extendExistingSubscription(
            UserSubscription subscription,
            Order order,
            Map<String, Object> paymentProviderData
    ) {
        var plan = order.getPlan();
        LocalDateTime expirationDate = subscription.getExpiredAt()
                .plusDays(PAYMENT_GRACE_DAYS + plan.getDurationDays());

        subscription.setPlan(order.getPlan());
        subscription.setPaymentMethod(order.getPaymentMethod());
        subscription.setPaymentProviderData(paymentProviderData);
        subscription.setExpiredAt(expirationDate);
    }

    private LocalDateTime getNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
