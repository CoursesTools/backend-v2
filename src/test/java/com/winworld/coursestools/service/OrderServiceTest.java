package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.order.CreateOrderDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.OrderType;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.Plan;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.repository.OrderRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import com.winworld.coursestools.service.user.UserTransactionService;
import com.winworld.coursestools.validation.validator.OrderValidator;
import com.winworld.coursestools.validation.validator.payment.PaymentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserDataService userDataService;

    @Mock
    private OrderValidator orderValidator;

    @Mock
    private CodeService codeService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private UserTransactionService userTransactionService;

    @Mock
    private PricingService pricingService;

    @Mock
    private UserSubscriptionService userSubscriptionService;

    @Mock
    private PartnershipService partnershipService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                userDataService,
                orderValidator,
                codeService,
                subscriptionService,
                userTransactionService,
                pricingService,
                userSubscriptionService,
                partnershipService,
                List.<PaymentValidator>of()
        );
    }

    @Test
    void createOrder_usesGrandfatheredMonthlyPriceOnlyForCurrentMatchingSubscription() {
        User user = new User();
        user.setId(7);

        SubscriptionType subscriptionType = new SubscriptionType();
        subscriptionType.setId(3);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(9);
        plan.setName(Plan.MONTH);
        plan.setPrice(new BigDecimal("29.99"));
        plan.setSubscriptionType(subscriptionType);

        UserSubscription userSubscription = UserSubscription.builder()
                .id(100)
                .user(user)
                .plan(plan)
                .status(SubscriptionStatus.GRANTED)
                .price(new BigDecimal("12.20"))
                .isTrial(false)
                .expiredAt(LocalDateTime.now().plusDays(3))
                .build();

        CreateOrderDto createOrderDto = new CreateOrderDto(plan.getId(), null, PaymentMethod.STRIPE);

        when(userDataService.getUserById(user.getId())).thenReturn(user);
        when(subscriptionService.getSubscriptionPlan(plan.getId())).thenReturn(plan);
        when(userSubscriptionService.getCurrentUserSubBySubTypeId(user.getId(), subscriptionType.getId()))
                .thenReturn(Optional.of(userSubscription));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.createOrder(user.getId(), createOrderDto);

        assertEquals(new BigDecimal("12.20"), order.getOriginalPrice());
        assertEquals(new BigDecimal("12.20"), order.getTotalPrice());
        assertEquals(OrderType.RECURRENT, order.getOrderType());
        verify(orderValidator).validateCreateOrder(user, createOrderDto, userSubscription);
        verify(orderValidator).validateMonthlyPlanPrice(
                plan,
                new BigDecimal("12.20"),
                new BigDecimal("12.20"),
                createOrderDto,
                null
        );
    }

    @Test
    void createOrder_fallsBackToCanonicalPlanPriceWhenNoCurrentSubscriptionExists() {
        User user = new User();
        user.setId(8);

        SubscriptionType subscriptionType = new SubscriptionType();
        subscriptionType.setId(4);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setId(12);
        plan.setName(Plan.MONTH);
        plan.setPrice(new BigDecimal("29.99"));
        plan.setSubscriptionType(subscriptionType);

        CreateOrderDto createOrderDto = new CreateOrderDto(plan.getId(), null, PaymentMethod.STRIPE);

        when(userDataService.getUserById(user.getId())).thenReturn(user);
        when(subscriptionService.getSubscriptionPlan(plan.getId())).thenReturn(plan);
        when(userSubscriptionService.getCurrentUserSubBySubTypeId(user.getId(), subscriptionType.getId()))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.createOrder(user.getId(), createOrderDto);

        assertEquals(new BigDecimal("29.99"), order.getOriginalPrice());
        assertEquals(new BigDecimal("29.99"), order.getTotalPrice());
        assertEquals(OrderType.RECURRENT, order.getOrderType());
        verify(orderValidator).validateCreateOrder(user, createOrderDto, null);
        verify(orderValidator).validateMonthlyPlanPrice(
                plan,
                new BigDecimal("29.99"),
                new BigDecimal("29.99"),
                createOrderDto,
                null
        );
    }
}
