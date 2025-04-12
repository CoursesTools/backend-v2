package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.dto.order.ProcessOrderDto;
import com.winworld.coursestools.dto.order.ReadOrderDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.PromoCode;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.OrderStatus;
import com.winworld.coursestools.event.ReferralActivityEvent;
import com.winworld.coursestools.exception.EntityNotFoundException;
import com.winworld.coursestools.mapper.OrderMapper;
import com.winworld.coursestools.repository.OrderRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserTransactionService;
import com.winworld.coursestools.validation.validator.OrderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserDataService userDataService;
    private final OrderValidator orderValidator;
    private final PromoCodeService promoCodeService;
    private final SubscriptionService subscriptionService;
    private final OrderMapper orderMapper;
    private final UserTransactionService userTransactionService;
    private final ApplicationEventPublisher eventPublisher;

    public ReadOrderDto createOrder(int userId, CreateOrderDto createDto) {
        User user = userDataService.getUserById(userId);
        orderValidator.validateCreateOrder(user, createDto);

        BigDecimal subscriptionPrice;
        UserSubscription userSubscription = user.getSubscription();
        if (userSubscription != null && !userSubscription.isTrial()) {
            subscriptionPrice = userSubscription.getSubscriptionPrice();
        } else {
            subscriptionPrice = subscriptionService.getSubscriptionPrice();
        }

        BigDecimal originalPrice = subscriptionService.formPrice(createDto.getPlan(), subscriptionPrice);
        BigDecimal totalPrice;
        PromoCode promoCode = null;

        if (createDto.getPromoCode() != null) {
            promoCode = promoCodeService.getPromoCodeByCode(createDto.getPromoCode());
            totalPrice = subscriptionService.formPrice(createDto.getPlan(), promoCode, subscriptionPrice);
        } else {
            totalPrice = originalPrice;
        }

        Order order = Order.builder()
                .user(user)
                .promoCode(promoCode)
                .totalPrice(totalPrice)
                .originalPrice(originalPrice)
                .paymentMethod(createDto.getPaymentMethod())
                .plan(createDto.getPlan())
                .status(OrderStatus.PENDING)
                .build();

        return orderMapper.toDto(orderRepository.save(order));
    }

    @Transactional
    public void processSuccessfulPayment(ProcessOrderDto dto) {
        Order order = getOrderById(dto.getOrderId());
        order.setStatus(OrderStatus.PAID);
        PromoCode promoCode = order.getPromoCode();

        User user = userDataService.getUserById(order.getUser().getId());
        UserSubscription userSubscription = user.getSubscription();

        if (promoCode != null) {
            orderValidator.validatePromoCodeEligibility(promoCode, user);
            if (promoCode.isPartnershipCode()) {
                user.setReferrer(promoCode.getOwner());
            }
        }

        subscriptionService.updateUserSubscriptionAfterPayment(
                userSubscription,
                order,
                user,
                dto.getPaymentProviderData()
        );

        boolean isActiveChanged = false;

        if (!user.isActive()) {
            user.setActive(true);
            user.setActiveUpdatedAt(LocalDateTime.now());
            isActiveChanged = true;
        }

        if (user.hasReferrer()) {
            ReferralActivityEvent referralActivityEvent = ReferralActivityEvent.builder()
                    .referralId(user.getReferrer().getId())
                    .isActiveChanged(isActiveChanged)
                    .amount(order.getTotalPrice())
                    .build();

            eventPublisher.publishEvent(referralActivityEvent);
        }

        userTransactionService.addPurchaseTransaction(user.getId(), order.getTotalPrice());
    }

    public Order getOrderById(int id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    }
}
