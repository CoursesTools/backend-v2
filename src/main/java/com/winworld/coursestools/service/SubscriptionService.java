package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.subscription.SubscriptionReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.SubscriptionMapper;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.subscription.SubscriptionPlanRepository;
import com.winworld.coursestools.repository.subscription.SubscriptionTypeRepository;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
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

import static com.winworld.coursestools.enums.PaymentMethod.STRIPE;
import static com.winworld.coursestools.enums.SubscriptionEventType.CREATED;
import static com.winworld.coursestools.enums.SubscriptionEventType.EXTENDED;
import static com.winworld.coursestools.enums.SubscriptionEventType.GRACE_PERIOD_END;
import static com.winworld.coursestools.enums.SubscriptionEventType.GRACE_PERIOD_START;
import static com.winworld.coursestools.enums.SubscriptionEventType.RESTORED;
import static com.winworld.coursestools.enums.SubscriptionEventType.TRIAL_CREATED;
import static com.winworld.coursestools.enums.SubscriptionEventType.TRIAL_ENDED;
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
    private final StripePaymentService stripePaymentService;

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

        var savedUserSubscription = userSubscriptionService.save(userSubscription);
        eventPublisher.publishEvent(subscriptionMapper.toEvent(user, TRIAL_CREATED, savedUserSubscription));
        return userMapper.toDto(savedUserSubscription);

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
            if (userSubscription.getPaymentMethod().equals(STRIPE)) {
                stripePaymentService.cancelSubscription(userSubscription);
            }
            log.info("User {} subscription expired", user.getId());
            eventPublisher.publishEvent(subscriptionMapper.toEvent(user, GRACE_PERIOD_START, userSubscription));
            //TODO Сделать напоминание о 3 днях, 7 и т.д.
        });
    }

    @Transactional
    public void deactivateExpiredTrialSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionService
                .findAllWithExpiredTrialSubscription();
        usersSubscriptions.forEach(userSubscription -> {
            userSubscription.setStatus(TERMINATED);
            log.info("User subscription {} trial expired", userSubscription.getId());
            eventPublisher.publishEvent(subscriptionMapper.toEvent(userSubscription.getUser(), TRIAL_ENDED, userSubscription));
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
            eventPublisher.publishEvent(subscriptionMapper.toEvent(userSubscription.getUser(), GRACE_PERIOD_END, userSubscription));
        });
    }

    public void updateUserSubscriptionAfterPayment(
            UserSubscription userSubscription,
            Order order,
            User user,
            Map<String, Object> paymentProviderData
    ) {
        if (userSubscription == null || userSubscription.getIsTrial()) {
            var newUserSubscription = createNewSubscription(userSubscription, order, user, paymentProviderData);
            eventPublisher.publishEvent(
                    subscriptionMapper.toEvent(newUserSubscription.getUser(), CREATED, newUserSubscription)
            );
        } else if (userSubscription.getStatus().equals(GRACE_PERIOD)) {
            updateGracePeriodSubscription(userSubscription, order, paymentProviderData);
            eventPublisher.publishEvent(subscriptionMapper.toEvent(userSubscription.getUser(), RESTORED, userSubscription));
        } else {
            extendExistingSubscription(userSubscription, order, paymentProviderData);
            eventPublisher.publishEvent(subscriptionMapper.toEvent(userSubscription.getUser(), EXTENDED, userSubscription));
        }
    }

    private UserSubscription createNewSubscription(
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
        return userSubscriptionService.save(newSubscription);
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
        subscription.setPrice(order.getOriginalPrice());
        subscription.setPaymentMethod(order.getPaymentMethod());
        subscription.setPaymentProviderData(paymentProviderData);
        subscription.setPlan(order.getPlan());
        subscription.setExpiredAt(expirationDate);
    }

    private void extendExistingSubscription(
            UserSubscription subscription,
            Order order,
            Map<String, Object> paymentProviderData
    ) {
        var plan = order.getPlan();
        LocalDateTime expirationDate = subscription.getExpiredAt()
                .plusDays(PAYMENT_GRACE_DAYS + plan.getDurationDays());

        if (subscription.getPaymentMethod().equals(STRIPE) && !order.getPaymentMethod().equals(STRIPE)) {
            stripePaymentService.cancelSubscription(subscription);
        }

        subscription.setPlan(plan);
        subscription.setPrice(order.getOriginalPrice());
        subscription.setPaymentMethod(order.getPaymentMethod());
        subscription.setPaymentProviderData(paymentProviderData);
        subscription.setExpiredAt(expirationDate);
    }

    private LocalDateTime getNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
