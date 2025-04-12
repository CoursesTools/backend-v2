package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.PromoCode;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.exception.ConflictException;
import com.winworld.coursestools.mapper.SubscriptionMapper;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.UserSubscriptionRepository;
import com.winworld.coursestools.service.user.UserDataService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.winworld.coursestools.enums.SubscriptionEventType.TRIAL;
import static com.winworld.coursestools.enums.SubscriptionStatus.GRACE_PERIOD;
import static com.winworld.coursestools.enums.SubscriptionStatus.GRANTED;
import static com.winworld.coursestools.enums.SubscriptionStatus.PENDING;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    private static final int YEAR_DISCOUNT = 20;
    private static final int YEAR_MULTIPLIER = 12;
    private static final int LIFETIME_MULTIPLIER = 40;
    private final UserDataService userDataService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SubscriptionMapper subscriptionMapper;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Getter
    @Value("${subscription.price}")
    private BigDecimal subscriptionPrice;

    @Value("${subscription.trial.days}")
    private int subscriptionTrialDays;

    public BigDecimal formPrice(Plan plan, PromoCode promoCode, BigDecimal subscriptionPrice) {
        return switch (plan) {
            case MONTH -> applyDiscount(
                    subscriptionPrice,
                    promoCode.getMonthDiscount()
            );
            case YEAR -> applyDiscount(
                    subscriptionPrice.multiply(BigDecimal.valueOf(YEAR_MULTIPLIER)),
                    YEAR_DISCOUNT + promoCode.getYearDiscount()
            );
            case LIFETIME -> applyDiscount(
                    subscriptionPrice.multiply(BigDecimal.valueOf(LIFETIME_MULTIPLIER)),
                    promoCode.getLifetimeDiscount()
            );
            default -> throw new IllegalArgumentException("Unsupported plan: " + plan);
        };
    }

    public BigDecimal formPrice(Plan plan, BigDecimal subscriptionPrice) {
        return switch (plan) {
            case MONTH -> subscriptionPrice;
            case YEAR -> applyDiscount(
                    subscriptionPrice.multiply(BigDecimal.valueOf(YEAR_MULTIPLIER)),
                    YEAR_DISCOUNT
            );
            case LIFETIME -> subscriptionPrice.multiply(
                    BigDecimal.valueOf(LIFETIME_MULTIPLIER)
            );
            default -> throw new IllegalArgumentException("Unsupported plan: " + plan);
        };
    }

    @Transactional
    public UserSubscriptionReadDto activateTrialForUser(int userId) {
        User user = userDataService.getUserById(userId);
        if (user.isTrialUsed()) {
            throw new ConflictException("You have already used the trial period");
        }
        user.setTrialUsed(true);

        LocalDateTime expiredAt = getNow()
                .plusDays(subscriptionTrialDays);
        UserSubscription userSubscription = UserSubscription.builder()
                .user(user)
                .status(PENDING)
                .plan(Plan.TRIAL)
                .subscriptionPrice(BigDecimal.ZERO)
                .isTrial(true)
                .expiredAt(expiredAt)
                .build();
        user.setSubscription(userSubscription);

        User savedUser = userDataService.save(user);
        eventPublisher.publishEvent(subscriptionMapper.toEvent(savedUser, TRIAL));
        return userMapper.toDto(savedUser.getSubscription());
    }

    @Transactional
    public void deactivateExpiredSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionRepository
                .findAllWithExpiredSubNotTrial(GRANTED);
        LocalDateTime now = getNow();
        usersSubscriptions.forEach(userSubscription -> {
                    userSubscription.setStatus(GRACE_PERIOD);
                    User user = userSubscription.getUser();
                    user.setActive(false);
                    user.setActiveUpdatedAt(now);
                    log.info("User {} subscription expired", user.getId());
                    //TODO Отправить письмецо
                    //TODO Сделать напоминание о 3 днях, 7 и т.д.
                });
    }

    @Transactional
    public void cleanupExpiredTrialSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionRepository
                .findAllWithExpiredTrialSub();
        usersSubscriptions.forEach(userSubscription -> {
            User user = userSubscription.getUser();
            user.setTrialUsed(true);
        });
        userSubscriptionRepository.deleteAll(usersSubscriptions);

    }

    @Transactional
    public void cleanupExpiredGracePeriodSubscriptions() {
        List<UserSubscription> usersSubscriptions = userSubscriptionRepository
                .findAllWithExpiredSubNotTrial(GRACE_PERIOD);
        userSubscriptionRepository.deleteAll(usersSubscriptions);
    }

    public void updateUserSubscriptionAfterPayment(
            UserSubscription userSubscription,
            Order order,
            User user,
            Map<String, Object> paymentProviderData
    ) {
        if (userSubscription == null || userSubscription.isTrial()) {
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
        UserSubscription newSubscription;

        if (currentSubscription != null && currentSubscription.isTrial()) {
            expirationDate = currentSubscription.getExpiredAt()
                    .plusMonths(order.getPlan().getValue())
                    .plusDays(1);
            newSubscription = currentSubscription;
        } else {
            expirationDate = getNow()
                    .plusMonths(order.getPlan().getValue())
                    .plusDays(1);
            newSubscription = new UserSubscription();
        }

        newSubscription.setUser(user);
        newSubscription.setPlan(order.getPlan());
        newSubscription.setSubscriptionPrice(subscriptionPrice);
        newSubscription.setPaymentMethod(order.getPaymentMethod());
        newSubscription.setStatus(PENDING);
        newSubscription.setPaymentProviderData(paymentProviderData);
        newSubscription.setTrial(false);
        newSubscription.setExpiredAt(expirationDate);

        user.setTrialUsed(true);
        user.setSubscription(newSubscription);
    }

    private void updateGracePeriodSubscription(
            UserSubscription subscription,
            Order order,
            Map<String, Object> paymentProviderData
    ) {
        LocalDateTime expirationDate = getNow()
                .plusMonths(order.getPlan().getValue())
                .plusDays(1);

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
        LocalDateTime expirationDate = subscription.getExpiredAt()
                .plusMonths(order.getPlan().getValue())
                .plusDays(1);

        subscription.setPlan(order.getPlan());
        subscription.setPaymentMethod(order.getPaymentMethod());
        subscription.setPaymentProviderData(paymentProviderData);
        subscription.setExpiredAt(expirationDate);
    }

    private LocalDateTime getNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private BigDecimal applyDiscount(BigDecimal price, int discount) {
        BigDecimal discountFactor = BigDecimal.valueOf(100 - discount)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        return price.multiply(discountFactor)
                .setScale(0, RoundingMode.HALF_UP);
    }
}
