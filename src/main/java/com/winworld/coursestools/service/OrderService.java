package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.dto.order.ProcessOrderDto;
import com.winworld.coursestools.dto.order.ReadOrderDto;
import com.winworld.coursestools.entity.Code;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.Referral;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.OrderStatus;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.OrderMapper;
import com.winworld.coursestools.repository.OrderRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import com.winworld.coursestools.service.user.UserTransactionService;
import com.winworld.coursestools.validation.validator.OrderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserDataService userDataService;
    private final OrderValidator orderValidator;
    private final CodeService codeService;
    private final SubscriptionService subscriptionService;
    private final OrderMapper orderMapper;
    private final UserTransactionService userTransactionService;
    private final PricingService pricingService;
    private final UserSubscriptionService userSubscriptionService;
    private final ReferralService referralService;
    private final PartnershipService partnershipService;

    public ReadOrderDto createOrder(int userId, CreateOrderDto createDto) {
        User user = userDataService.getUserById(userId);
        SubscriptionPlan plan = subscriptionService.getSubscriptionPlan(createDto.getPlanId());
        UserSubscription userSubscription = userSubscriptionService
                .getUserSubBySubType(userId, plan.getSubscriptionType().getId())
                .orElse(null);
        orderValidator.validateCreateOrder(user, createDto, userSubscription);

        BigDecimal subscriptionPrice = plan.getPrice();

        //TODO сделать это потом как-нибудь по лучше
        if (userSubscription != null && userSubscription.getPaymentMethod().equals(createDto.getPaymentMethod())
                && createDto.getPaymentMethod().equals(PaymentMethod.STRIPE)) {
            throw new ConflictException("You already have an active subscription with Stripe payment method.");
        }

        if (userSubscription != null && !userSubscription.getIsTrial()
                && userSubscription.getPlan().equals(plan)) {
            subscriptionPrice = userSubscription.getPrice();
        }

        BigDecimal totalPrice;
        Code code = null;

        if (createDto.getCode() != null) {
            Referral referrer = user.getReferred();
            code = codeService.getCodeByValue(createDto.getCode());
            totalPrice = pricingService.calculatePrice(
                    code, plan.getDiscountMultiplier(), subscriptionPrice
            );
            if (referrer != null && !referrer.isBonusUsed()) {
                Code partnerCode = referrer.getReferrer().getPartnerCode();
                BigDecimal priceWithReferrerCode = pricingService.calculatePrice(
                        partnerCode,
                        plan.getDiscountMultiplier(),
                        subscriptionPrice
                );
                if (totalPrice.compareTo(priceWithReferrerCode) >= 0) {
                    totalPrice = priceWithReferrerCode;
                    code = partnerCode;
                }
            }
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
                .build();

        return orderMapper.toDto(orderRepository.save(order));
    }

    @Transactional
    public void processSuccessfulPayment(ProcessOrderDto dto) {
        Order order = getOrderById(dto.getOrderId());
        if (order.getStatus().equals(OrderStatus.PAID)) {
            throw new ConflictException("Order already paid");
        }
        order.setStatus(OrderStatus.PAID);
        Code code = order.getCode();

        User user = order.getUser();
        UserSubscription userSubscription = userSubscriptionService
                .getUserSubBySubType(user.getId(), order.getPlan().getSubscriptionType().getId())
                .orElse(null);

        if (code != null) {
            codeService.checkCode(user.getId(), code.getCode());
            if (code.isPartnershipCode()) {
                referralService.registerReferral(code.getOwner(), user, true);
                partnershipService.recalculateLevelAfterNewReferral(code.getOwner());
            }
            codeService.useCode(user.getId(), code);
        }

        subscriptionService.updateUserSubscriptionAfterPayment(
                userSubscription,
                order,
                user,
                dto.getPaymentProviderData()
        );
        var transaction = userTransactionService.addPurchaseTransaction(user.getId(), order.getTotalPrice());

        var referred = user.getReferred();
        if (referred != null) {
            partnershipService.calculateCashbackAfterNewReferral(
                    referred, order.getTotalPrice(), transaction
            );
            if (!referred.isActive()) {
                referred.setActive(true);
            }
            if (!referred.isBonusUsed() && (code == null || code.equals(referred.getReferrer().getPartnerCode()))) {
                referred.setBonusUsed(true);
            }
        }
    }

    public Order getOrderById(int id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    }
}
