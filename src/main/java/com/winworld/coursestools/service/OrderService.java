package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.transaction.TransactionCreateDto;
import com.winworld.coursestools.entity.Code;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.OrderStatus;
import com.winworld.coursestools.enums.OrderType;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.TransactionType;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.repository.OrderRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import com.winworld.coursestools.service.user.UserTransactionService;
import com.winworld.coursestools.validation.validator.OrderValidator;
import com.winworld.coursestools.validation.validator.payment.PaymentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserDataService userDataService;
    private final OrderValidator orderValidator;
    private final CodeService codeService;
    private final SubscriptionService subscriptionService;
    private final UserTransactionService userTransactionService;
    private final PricingService pricingService;
    private final UserSubscriptionService userSubscriptionService;
    private final PartnershipService partnershipService;
    private final List<PaymentValidator> paymentValidators;

    public Order createOrder(int userId, CreateOrderDto createDto) {
        User user = userDataService.getUserById(userId);
        SubscriptionPlan plan = subscriptionService.getSubscriptionPlan(createDto.getPlanId());
        UserSubscription userSubscription = userSubscriptionService
                .getUserSubBySubTypeIdNotTerminated(userId, plan.getSubscriptionType().getId())
                .orElse(null);
        orderValidator.validateCreateOrder(user, createDto, userSubscription);

        BigDecimal subscriptionPrice = plan.getPrice();

        if (userSubscription != null && !userSubscription.getIsTrial()
                && userSubscription.getPlan().equals(plan)) {
            subscriptionPrice = userSubscription.getPrice();
        }

        BigDecimal totalPrice;
        Code code = null;
        Referral referrer = user.getReferred();

        if (createDto.getCode() != null) {
            code = codeService.getCodeByValue(createDto.getCode());
            totalPrice = pricingService.calculatePrice(
                    code, plan.getDiscountMultiplier(), subscriptionPrice
            );
        } else if (referrer != null && !referrer.isBonusUsed()) {
            code = referrer.getReferrer().getPartnerCode();
            totalPrice = pricingService.calculatePrice(
                    code,
                    plan.getDiscountMultiplier(),
                    subscriptionPrice
            );
        } else {
            totalPrice = subscriptionPrice;
        }

        Order order = Order.builder()
                .user(user)
                .code(code)
                .totalPrice(totalPrice)
                .originalPrice(subscriptionPrice)
                .paymentMethod(createDto.getPaymentMethod())
                .plan(plan)
                .status(OrderStatus.PENDING)
                .orderType(createDto.getPaymentMethod().equals(PaymentMethod.STRIPE)
                        ? OrderType.RECURRENT : OrderType.ONE_TIME
                )
                .build();

        paymentValidators.stream()
                .filter(validator -> validator.getSupportedPaymentMethod().equals(order.getPaymentMethod()))
                .forEach(validator -> validator.validate(user, order, userSubscription));

        return orderRepository.save(order);
    }

    @Transactional
    public void processSuccessfulPayment(ProcessPaymentDto dto) {
        Order order = getOrderById(dto.getOrderId());
        Code code = order.getCode();
        User user = order.getUser();

        boolean orderIsPaid = order.getStatus().equals(OrderStatus.PAID);
        boolean isRecurrentPayment = order.getOrderType().equals(OrderType.RECURRENT) && orderIsPaid;
        var paymentAmount = isRecurrentPayment ? order.getOriginalPrice() : order.getTotalPrice();

        if (!orderIsPaid && code != null) {
            codeService.useCode(user.getId(), code);
        }

        UserSubscription userSubscription = userSubscriptionService
                .getUserSubBySubTypeIdNotTerminated(user.getId(), order.getPlan().getSubscriptionType().getId())
                .orElse(null);

        subscriptionService.updateUserSubscriptionAfterPayment(
                userSubscription,
                order,
                user,
                dto.getPaymentProviderData()
        );
        var transaction = userTransactionService.addTransaction(new TransactionCreateDto(
                user,
                paymentAmount,
                TransactionType.PURCHASE,
                order
        ));

        var referred = user.getReferred();
        if (referred != null) {
            partnershipService.calculateCashbackAfterNewReferral(
                    referred, paymentAmount, transaction
            );
            if (!referred.isActive()) {
                referred.setActive(true);
            }
            if (!referred.isBonusUsed() && (code == null || code.equals(referred.getReferrer().getPartnerCode()))) {
                referred.setBonusUsed(true);
            }
        }
        if (!orderIsPaid) {
            order.setStatus(OrderStatus.PAID);
        }
        log.info("Order {} for user {} processed successfully", order.getId(), user.getId());
    }

    public Order getOrderById(int id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    }
}
