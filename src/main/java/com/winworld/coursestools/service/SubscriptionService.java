package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.subscription.PlanSubscriptionCount;
import com.winworld.coursestools.dto.subscription.SubscriptionActivateDto;
import com.winworld.coursestools.dto.subscription.SubscriptionReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.SubscriptionMapper;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.TrialActivationRepository;
import com.winworld.coursestools.repository.subscription.SubscriptionPlanRepository;
import com.winworld.coursestools.repository.subscription.SubscriptionTypeRepository;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.service.external.ActivatingSubscriptionService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.winworld.coursestools.service.payment.impl.StripePaymentService.CURRENT_PERIOD_END;

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
    public static final int GRACE_PERIOD_DAYS = 7;

    private final UserDataService userDataService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionTypeRepository subscriptionTypeRepository;
    private final UserSubscriptionService userSubscriptionService;
    private final StripePaymentService stripePaymentService;
    private final ActivatingSubscriptionService activatingSubscriptionService;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final TrialActivationRepository trialActivationRepository;
    private final SubscriptionDeactivationService subscriptionDeactivationService;

    @Value("${subscription.ct-pro.trial.days}")
    private int ctProTrialDays;

    public SubscriptionReadDto getSubscription(SubscriptionName name) {
        SubscriptionType subscriptionType = getSubscriptionTypeByName(name);
        return subscriptionMapper.toDto(subscriptionType);
    }

    public SubscriptionPlan getSubscriptionPlan(int planId) {
        return subscriptionPlanRepository.findByIdJoinSubscriptionType(planId).orElseThrow(
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
        String tradingViewName = user.getSocial().getTradingViewName();
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO);

        if (trialActivationRepository.existsByTradingviewUsername(tradingViewName)
                || userSubscriptionService.hasEverHadSubscriptionOfType(userId, subscriptionType.getId())) {
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
        trialActivationRepository.createTrialActivation(userId, tradingViewName);
        eventPublisher.publishEvent(subscriptionMapper.toEvent(user, TRIAL_CREATED, savedUserSubscription));
        return userMapper.toDto(savedUserSubscription);

    }

    @Transactional
    public List<Integer> deactivateExpiredSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionService
                .findAllExpiredSubscriptionsByStatus(GRANTED);
        List<Integer> userIds = new java.util.ArrayList<>();

        usersSubscriptions.forEach(userSubscription -> {
            try {
                subscriptionDeactivationService.deactivateSingleSubscription(userSubscription);
                userIds.add(userSubscription.getUser().getId());
            } catch (Exception e) {
                log.error("Failed to deactivate subscription {} for user {}",
                    userSubscription.getId(), userSubscription.getUser().getId(), e);
            }
        });
        return userIds;
    }

    @Transactional
    public List<Integer> deactivateExpiredTrialSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionService
                .findAllWithExpiredTrialSubscription();
        usersSubscriptions.forEach(userSubscription -> {
            userSubscription.setStatus(TERMINATED);
            log.info("User subscription {} trial expired", userSubscription.getId());
            eventPublisher.publishEvent(subscriptionMapper.toEvent(userSubscription.getUser(), TRIAL_ENDED, userSubscription));
        });
        return usersSubscriptions.stream().map(UserSubscription::getId).collect(Collectors.toList());
    }

    @Transactional
    public void deactivateExpiredGracePeriodSubscriptions() {
        var cutoffDate = getNow().minusDays(GRACE_PERIOD_DAYS);
        List<UserSubscription> usersSubscriptions = userSubscriptionService
                .findExpiredSubscriptionsOlderThanDate(cutoffDate, GRACE_PERIOD);
        usersSubscriptions.forEach(userSubscription -> {
            userSubscription.setStatus(TERMINATED);
            User user = userSubscription.getUser();
            log.info("User {} subscription grace period expired", user.getId());
            eventPublisher.publishEvent(subscriptionMapper.toEvent(userSubscription.getUser(), GRACE_PERIOD_END, userSubscription));
        });
    }

    @Transactional
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
        LocalDateTime baseDate;
        UserSubscription newSubscription = new UserSubscription();
        SubscriptionPlan subscriptionPlan = order.getPlan();

        if (currentSubscription != null && currentSubscription.getIsTrial()) {
            baseDate = currentSubscription.getExpiredAt();
            currentSubscription.setStatus(TERMINATED);
        } else {
            baseDate = getNow();
        }

        LocalDateTime expirationDate = calculateExpirationDate(baseDate, subscriptionPlan, paymentProviderData);

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

    @Transactional
    public UserSubscription createNewSubscription(
            User user,
            boolean isTrial,
            LocalDate expiredAt
    ) {
        UserSubscription newSubscription = new UserSubscription();

        var plan = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO).getPlans().get(0);

        newSubscription.setPlan(plan);
        newSubscription.setPrice(plan.getPrice());
        newSubscription.setPaymentMethod(PaymentMethod.MANUAL);
        newSubscription.setPaymentProviderData(null);
        newSubscription.setIsTrial(isTrial);
        newSubscription.setExpiredAt(expiredAt.atStartOfDay());

        user.addSubscription(newSubscription);
        ActivateTradingViewAccessDto dto = new ActivateTradingViewAccessDto(
                user.getEmail(), user.getSocial().getTradingViewName(), newSubscription.getExpiredAt());
        activatingSubscriptionService.activateTradingViewAccess(dto);
        newSubscription.setStatus(SubscriptionStatus.GRANTED);
        return userSubscriptionService.save(newSubscription);
    }

    private void updateGracePeriodSubscription(
            UserSubscription subscription,
            Order order,
            Map<String, Object> paymentProviderData
    ) {
        var plan = order.getPlan();
        LocalDateTime expirationDate = calculateExpirationDate(getNow(), plan, paymentProviderData);

        subscription.setStatus(PENDING);
        subscription.setPrice(order.getOriginalPrice());
        subscription.setPaymentMethod(order.getPaymentMethod());
        subscription.setPaymentProviderData(paymentProviderData);
        subscription.setPlan(order.getPlan());
        subscription.setExpiredAt(expirationDate);
    }

    @Transactional
    public void updateGracePeriodSubscription(
            UserSubscription subscription,
            User user,
            LocalDate expiredAt
    ) {
        subscription.setStatus(PENDING);
        subscription.setPaymentMethod(PaymentMethod.MANUAL);
        subscription.setExpiredAt(expiredAt.atStartOfDay());
        ActivateTradingViewAccessDto dto = new ActivateTradingViewAccessDto(
                user.getEmail(), user.getSocial().getTradingViewName(), subscription.getExpiredAt());
        activatingSubscriptionService.activateTradingViewAccess(dto);
        subscription.setStatus(SubscriptionStatus.GRANTED);
    }

    private void extendExistingSubscription(
            UserSubscription subscription,
            Order order,
            Map<String, Object> paymentProviderData
    ) {
        var plan = order.getPlan();
        LocalDateTime expirationDate = calculateExpirationDate(subscription.getExpiredAt(), plan, paymentProviderData);

        if (subscription.getPaymentMethod().equals(STRIPE) && !order.getPaymentMethod().equals(STRIPE)) {
            stripePaymentService.cancelSubscription(subscription);
        }

        subscription.setPlan(plan);
        subscription.setPrice(order.getOriginalPrice());
        subscription.setPaymentMethod(order.getPaymentMethod());
        subscription.setPaymentProviderData(paymentProviderData);
        subscription.setExpiredAt(expirationDate);
    }

    @Transactional
    public void extendExistingSubscription(
            UserSubscription subscription,
            User user,
            LocalDate expiredAt
    ) {
        subscription.setExpiredAt(expiredAt.atStartOfDay());
        ActivateTradingViewAccessDto dto = new ActivateTradingViewAccessDto(
                user.getEmail(), user.getSocial().getTradingViewName(), subscription.getExpiredAt());
        activatingSubscriptionService.activateTradingViewAccess(dto);
        subscription.setStatus(SubscriptionStatus.GRANTED);
    }

    private LocalDateTime getNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private LocalDateTime calculateExpirationDate(
            LocalDateTime baseDate,
            SubscriptionPlan plan,
            Map<String, Object> paymentProviderData
    ) {
        if (paymentProviderData != null && paymentProviderData.containsKey(CURRENT_PERIOD_END)) {
            Long periodEnd = (Long) paymentProviderData.get(CURRENT_PERIOD_END);
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(periodEnd),
                    ZoneOffset.UTC
            ).plusDays(PAYMENT_GRACE_DAYS);
        }
        return baseDate.plusDays(PAYMENT_GRACE_DAYS + plan.getDurationDays());
    }

    @Transactional
    public UserSubscriptionReadDto activateSubscription(SubscriptionActivateDto dto) {
        User user = userDataService.getUserByTradingViewName(dto.getUsername());
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO);
        UserSubscription userSubscription = userSubscriptionService.getUserSubBySubTypeIdNotTerminated(
                user.getId(), subscriptionType.getId()
        ).orElseThrow(() -> new EntityNotFoundException("Active subscription not found"));
        var expiration = dto.getExpiration().atStartOfDay();
        userSubscription.setExpiredAt(expiration);
        activatingSubscriptionService.activateTradingViewAccess(
                new ActivateTradingViewAccessDto(user.getEmail(), dto.getUsername(), expiration)
        );
        return userMapper.toDto(userSubscriptionService.save(userSubscription));
    }

    public List<PlanSubscriptionCount> getActiveUsersCountOnDateWithPlan(LocalDate date) {
        return userSubscriptionRepository.countActiveSubscriptionsOnDate(date.atStartOfDay());
    }
}
