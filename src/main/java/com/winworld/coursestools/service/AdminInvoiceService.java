package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.admin.CreateCustomInvoiceDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.OrderStatus;
import com.winworld.coursestools.enums.OrderType;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.repository.OrderRepository;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInvoiceService {
    private final UserDataService userDataService;
    private final SubscriptionService subscriptionService;
    private final UserSubscriptionService userSubscriptionService;
    private final OrderRepository orderRepository;
    private final StripePaymentService stripePaymentService;

    @Transactional
    public String createCustomInvoice(CreateCustomInvoiceDto dto) {
        User user = userDataService.getUserById(dto.getUserId());
        SubscriptionType subscription = subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO);
        SubscriptionPlan subscriptionPlan = subscription.getPlans()
                .stream()
                .filter(plan -> plan.getName().equals(Plan.LIFETIME))
                .findFirst()
                .orElseThrow(() -> new DataValidationException(
                        "LIFETIME plan not found for subscription " + SubscriptionName.COURSESTOOLSPRO));

        if (!subscriptionPlan.getName().equals(Plan.LIFETIME)) {
            throw new DataValidationException("Custom invoices can only be created for LIFETIME plans");
        }

        UserSubscription userSubscription = userSubscriptionService
                .getUserSubBySubTypeIdNotTerminated(user.getId(), subscriptionPlan.getSubscriptionType().getId())
                .orElse(null);

        if (userSubscription != null && userSubscription.getPlan().getName().equals(Plan.LIFETIME)) {
            throw new ConflictException("User already has a lifetime plan");
        }

        Order order = Order.builder()
                .user(user)
                .code(null)
                .totalPrice(dto.getCustomPrice())
                .originalPrice(dto.getCustomPrice())
                .paymentMethod(PaymentMethod.STRIPE)
                .plan(subscriptionPlan)
                .status(OrderStatus.PENDING)
                .orderType(OrderType.ONE_TIME)
                .build();

        order = orderRepository.save(order);
        log.info("Created custom invoice order {} for user {} with price {}",
                order.getId(), user.getId(), dto.getCustomPrice());

        return stripePaymentService.createCustomInvoice(order, user.getEmail(), dto.getDescription());
    }
}
