package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.external.ActivateTradingViewAccessDto;
import com.winworld.coursestools.dto.subscription.PlanSubscriptionCount;
import com.winworld.coursestools.dto.subscription.SubscriptionActivateDto;
import com.winworld.coursestools.dto.subscription.TierPlanSubscriptionCount;
import com.winworld.coursestools.dto.subscription.SubscriptionReadDto;
import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.OrderStatus;
import com.winworld.coursestools.enums.OrderType;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
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
    public static final int PAYMENT_GRACE_DAYS = 2;
    public static final int GRACE_PERIOD_DAYS = 7;
    private static final LocalDateTime LIFETIME_EXPIRY = LocalDateTime.of(2100, 12, 31, 23, 59, 59);

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
    private final SubscriptionStateReconciliationService subscriptionStateReconciliationService;

    @Value("${subscription.ct-pro.trial.days}")
    private int ctProTrialDays;

    public SubscriptionReadDto getSubscription(SubscriptionName name) {
        return getSubscription(name, null);
    }

    public SubscriptionReadDto getSubscription(SubscriptionName name, SubscriptionTier tier) {
        SubscriptionType subscriptionType = getSubscriptionTypeByName(name);
        SubscriptionReadDto dto = subscriptionMapper.toDto(subscriptionType);
        if (tier != null) {
            dto.setPlans(dto.getPlans().stream()
                    .filter(p -> p.getTier() == tier)
                    .toList());
        }
        return dto;
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
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);

        if (trialActivationRepository.existsByTradingviewUsername(tradingViewName)
                || userSubscriptionService.hasEverHadSubscriptionOfType(userId, subscriptionType.getId())) {
            throw new ConflictException("You can`t use anymore trial subscription");
        }

        LocalDateTime expiredAt = getNow()
                .plusDays(ctProTrialDays);
        SubscriptionPlan trialPlan = subscriptionType.getPlans().stream()
                .filter(p -> p.getTier() == SubscriptionTier.PRO && p.getName() == Plan.MONTH)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Pro monthly plan not found"));

        UserSubscription userSubscription = UserSubscription.builder()
                .user(user)
                .status(PENDING)
                .plan(trialPlan)
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

    public List<Integer> deactivateExpiredSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionService
                .findAllExpiredSubscriptionsByStatus(GRANTED);
        List<Integer> userIds = new java.util.ArrayList<>();

        usersSubscriptions.forEach(userSubscription -> {
            try {
                subscriptionDeactivationService.deactivateSingleSubscription(userSubscription.getId());
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

    public void deactivateExpiredGracePeriodSubscriptions() {
        subscriptionStateReconciliationService.reconcilePastGracePeriodSubscriptions("scheduler");
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

    private void extendExistingSubscription(
            UserSubscription subscription,
            Order order,
            Map<String, Object> paymentProviderData
    ) {
        var plan = order.getPlan();
        // For Stripe renewals, use CURRENT_PERIOD_END + grace (Stripe controls the billing boundary).
        // For non-Stripe renewals, extend from the current expiry by the plan duration only — do NOT
        // add PAYMENT_GRACE_DAYS here, because the existing expiredAt already includes grace days from
        // prior renewals and compounding them causes ~2 days of drift per renewal (~1 extra month per year).
        LocalDateTime expirationDate;
        if (paymentProviderData != null && paymentProviderData.containsKey(CURRENT_PERIOD_END)) {
            Long periodEnd = (Long) paymentProviderData.get(CURRENT_PERIOD_END);
            expirationDate = LocalDateTime.ofInstant(Instant.ofEpochSecond(periodEnd), ZoneOffset.UTC)
                    .plusDays(PAYMENT_GRACE_DAYS);
        } else {
            expirationDate = subscription.getExpiredAt().plusDays(plan.getDurationDays());
        }

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
    public UserSubscription createNewLifetimeSubscription(User user, SubscriptionTier tier) {
        var plan = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS).getPlans().stream()
                .filter(p -> p.getTier() == tier && p.getName() == Plan.LIFETIME)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(tier + " lifetime plan not found"));

        UserSubscription newSubscription = new UserSubscription();
        newSubscription.setPlan(plan);
        newSubscription.setPrice(plan.getPrice());
        newSubscription.setPaymentMethod(PaymentMethod.MANUAL);
        newSubscription.setPaymentProviderData(null);
        newSubscription.setIsTrial(false);
        newSubscription.setExpiredAt(LIFETIME_EXPIRY);

        user.addSubscription(newSubscription);
        ActivateTradingViewAccessDto dto = new ActivateTradingViewAccessDto(
                user.getEmail(), plan.getTier(),
                user.getSocial().getTradingViewName(), newSubscription.getExpiredAt(), true);
        activatingSubscriptionService.activateTradingViewAccess(user.getId(), dto);
        newSubscription.setStatus(SubscriptionStatus.GRANTED);
        return userSubscriptionService.save(newSubscription);
    }

    @Transactional
    public void grantLifetimeToExistingSubscription(UserSubscription subscription, User user, SubscriptionTier tier) {
        var lifetimePlan = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS).getPlans().stream()
                .filter(p -> p.getTier() == tier && p.getName() == Plan.LIFETIME)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(tier + " lifetime plan not found"));

        if (STRIPE.equals(subscription.getPaymentMethod())) {
            stripePaymentService.cancelSubscription(subscription);
        }
        subscription.setPlan(lifetimePlan);
        subscription.setPaymentMethod(PaymentMethod.MANUAL);
        subscription.setExpiredAt(LIFETIME_EXPIRY);
        ActivateTradingViewAccessDto dto = new ActivateTradingViewAccessDto(
                user.getEmail(), lifetimePlan.getTier(),
                user.getSocial().getTradingViewName(), subscription.getExpiredAt(), true);
        activatingSubscriptionService.activateTradingViewAccess(user.getId(), dto);
        subscription.setStatus(SubscriptionStatus.GRANTED);
    }

    // --- Admin classic/custom grant surface ----------------------------------

    /**
     * Classic MONTH/YEAR admin grant.
     * <p>
     * Default ({@code keepExpirationDate=false}): routes through the canonical
     * payment-update path via a transient (never-persisted) Order. Reuses every
     * tested branch — create / extend / grace-restore, Stripe-cancel-on-switch,
     * grace-day rules, trial-handoff.
     * <p>
     * {@code keepExpirationDate=true}: tier/plan swap on an existing subscription
     * without touching its {@code expiredAt}. Requires an existing non-terminated
     * sub (400 otherwise). Cancels Stripe if the sub was on STRIPE. Publishes
     * EXTENDED so the listener re-activates TV with the new tier.
     * <p>
     * No user_transactions row is written either way (this is not a payment).
     */
    @Transactional
    public UserSubscription adminGrantPaid(User user, SubscriptionTier tier, Plan planName, boolean keepExpirationDate) {
        if (planName != Plan.MONTH && planName != Plan.YEAR) {
            throw new DataValidationException("adminGrantPaid only accepts MONTH or YEAR; got " + planName);
        }
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);
        SubscriptionPlan plan = subscriptionType.getPlans().stream()
                .filter(p -> p.getTier() == tier && p.getName() == planName)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(tier + " " + planName + " plan not found"));

        UserSubscription currentSub = userSubscriptionService
                .getCurrentUserSubBySubTypeId(user.getId(), subscriptionType.getId())
                .orElse(null);

        if (keepExpirationDate) {
            return adminSwapTierKeepExpiry(user, currentSub, plan);
        }

        // Transient Order: value carrier only — never saved, not referenced after this call.
        Order syntheticOrder = Order.builder()
                .user(user)
                .plan(plan)
                .originalPrice(plan.getPrice())
                .totalPrice(plan.getPrice())
                .paymentMethod(PaymentMethod.MANUAL)
                .orderType(OrderType.ONE_TIME)
                .status(OrderStatus.PAID)
                .build();

        updateUserSubscriptionAfterPayment(currentSub, syntheticOrder, user, null);

        // Re-read — updateUserSubscriptionAfterPayment may have created a new row when
        // currentSub was null or a trial.
        return userSubscriptionService
                .getCurrentUserSubBySubTypeId(user.getId(), subscriptionType.getId())
                .orElseThrow(() -> new EntityNotFoundException("Subscription missing after classic grant"));
    }

    private UserSubscription adminSwapTierKeepExpiry(User user, UserSubscription currentSub, SubscriptionPlan plan) {
        if (currentSub == null) {
            throw new DataValidationException(
                    "User '" + user.getSocial().getTradingViewName()
                            + "' has no active subscription whose expiration could be kept");
        }
        if (STRIPE.equals(currentSub.getPaymentMethod())) {
            stripePaymentService.cancelSubscription(currentSub);
        }
        currentSub.setPlan(plan);
        currentSub.setPrice(plan.getPrice());
        currentSub.setPaymentMethod(PaymentMethod.MANUAL);
        currentSub.setPaymentProviderData(null);
        currentSub.setIsTrial(false);
        currentSub.setStatus(PENDING);
        // expiredAt deliberately untouched.
        UserSubscription saved = userSubscriptionService.save(currentSub);
        eventPublisher.publishEvent(subscriptionMapper.toEvent(user, EXTENDED, saved));
        return saved;
    }

    @Transactional
    public UserSubscription adminGrantLifetime(User user, SubscriptionTier tier) {
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);
        UserSubscription current = userSubscriptionService
                .getCurrentUserSubBySubTypeId(user.getId(), subscriptionType.getId())
                .orElse(null);
        if (current == null) {
            return createNewLifetimeSubscription(user, tier);
        }
        grantLifetimeToExistingSubscription(current, user, tier);
        return current;
    }

    /**
     * Admin trial grant with caller-supplied expiry. If user has a non-trial active
     * sub → 400. If an active trial exists → extend it. Else → create a fresh
     * trial (same shape as the self-serve signup trial).
     */
    @Transactional
    public UserSubscription adminGrantTrial(User user, SubscriptionTier tier, LocalDate trialExpiresAt) {
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);
        UserSubscription current = userSubscriptionService
                .getCurrentUserSubBySubTypeId(user.getId(), subscriptionType.getId())
                .orElse(null);

        if (current != null && !Boolean.TRUE.equals(current.getIsTrial())) {
            throw new DataValidationException(
                    "User '" + user.getSocial().getTradingViewName()
                            + "' already has an active subscription; cannot issue a trial");
        }

        LocalDateTime expiresAt = trialExpiresAt.atStartOfDay();

        if (current != null) {
            current.setExpiredAt(expiresAt);
            // Trial tier is fixed to PRO in the self-serve path; keep it consistent here too
            // when the caller asks for a specific tier (fresh plan lookup).
            SubscriptionPlan trialPlan = subscriptionType.getPlans().stream()
                    .filter(p -> p.getTier() == SubscriptionTier.PRO && p.getName() == Plan.MONTH)
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Pro monthly plan not found"));
            current.setPlan(trialPlan);
            UserSubscription saved = userSubscriptionService.save(current);
            eventPublisher.publishEvent(subscriptionMapper.toEvent(user, EXTENDED, saved));
            return saved;
        }

        // Fresh trial — mirrors activateCtProTrialForUser (minus the self-serve guards).
        SubscriptionPlan trialPlan = subscriptionType.getPlans().stream()
                .filter(p -> p.getTier() == SubscriptionTier.PRO && p.getName() == Plan.MONTH)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Pro monthly plan not found"));

        UserSubscription newSub = UserSubscription.builder()
                .user(user)
                .status(PENDING)
                .plan(trialPlan)
                .price(BigDecimal.ZERO)
                .isTrial(true)
                .expiredAt(expiresAt)
                .build();
        user.addSubscription(newSub);
        UserSubscription saved = userSubscriptionService.save(newSub);

        // Mark the trial-used flag so the user can't also self-claim a trial after this.
        // Skip if the row already exists (user previously self-trialed + lapsed; trial_activations.user_id is PK).
        String tradingViewName = user.getSocial().getTradingViewName();
        if (!trialActivationRepository.existsByTradingviewUsername(tradingViewName)) {
            trialActivationRepository.createTrialActivation(user.getId(), tradingViewName);
        }

        eventPublisher.publishEvent(subscriptionMapper.toEvent(user, TRIAL_CREATED, saved));
        return saved;
    }

    /**
     * Custom admin update: pure expiredAt bump. Tier/plan inherited from the
     * existing subscription. Publishes EXTENDED so the async listener re-activates
     * TV access with the new expiry and flips status to GRANTED.
     */
    @Transactional
    public UserSubscription adminCustomUpdateExpiry(User user, LocalDate expiredAt) {
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);
        UserSubscription current = userSubscriptionService
                .getCurrentUserSubBySubTypeId(user.getId(), subscriptionType.getId())
                .orElseThrow(() -> new DataValidationException(
                        "User '" + user.getSocial().getTradingViewName()
                                + "' doesn't have an active subscription to update"));

        current.setExpiredAt(expiredAt.atStartOfDay());
        UserSubscription saved = userSubscriptionService.save(current);
        eventPublisher.publishEvent(subscriptionMapper.toEvent(user, EXTENDED, saved));
        return saved;
    }

    // ------------------------------------------------------------------------

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
        SubscriptionType subscriptionType = getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);
        UserSubscription userSubscription = userSubscriptionService.getCurrentUserSubBySubTypeId(
                user.getId(), subscriptionType.getId()
        ).orElseThrow(() -> new EntityNotFoundException("Active subscription not found"));
        var expiration = dto.getExpiration().atStartOfDay();
        userSubscription.setExpiredAt(expiration);
        activatingSubscriptionService.activateTradingViewAccess(
                user.getId(),
                new ActivateTradingViewAccessDto(user.getEmail(), userSubscription.getPlan().getTier(),
                        dto.getUsername(), expiration, userSubscription.getPlan().getName() == Plan.LIFETIME)
        );
        return userMapper.toDto(userSubscriptionService.save(userSubscription));
    }

    public List<PlanSubscriptionCount> getActiveUsersCountOnDateWithPlan(LocalDate date) {
        return userSubscriptionRepository.countActiveSubscriptionsOnDate(date.atStartOfDay());
    }

    public List<TierPlanSubscriptionCount> getActiveSubscriptionsByTierAndPlan(
            java.util.Collection<SubscriptionStatus> statuses
    ) {
        return userSubscriptionRepository.countActiveSubscriptionsByTierAndPlan(statuses);
    }
}
