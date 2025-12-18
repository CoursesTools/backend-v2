package com.winworld.coursestools.facade;

import com.winworld.coursestools.dto.payment.BalanceRetrieveDto;
import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.StripeRetrieveDto;
import com.winworld.coursestools.dto.payment.crypto.CryptoRetrieveDto;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.service.OrderService;
import com.winworld.coursestools.service.SubscriptionService;
import com.winworld.coursestools.service.payment.impl.BalancePaymentService;
import com.winworld.coursestools.service.payment.impl.CryptoPaymentService;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentFacade {
    private final StripePaymentService stripePaymentService;
    private final CryptoPaymentService cryptoPaymentService;
    private final BalancePaymentService balancePaymentService;
    private final OrderService orderService;
    private final SubscriptionService subscriptionService;
    private final UserSubscriptionService userSubscriptionService;

    public void retrieveStripePayment(String payload, String signature) {
        StripeRetrieveDto dto = StripeRetrieveDto.builder()
                .payload(payload)
                .signature(signature)
                .build();
        ProcessPaymentDto paymentDto = stripePaymentService.processWebhook(dto);
        if (paymentDto != null) {
            processOrder(paymentDto);
        }
    }

    public void retrieveCryptoPayment(CryptoRetrieveDto dto) {
        processOrder(cryptoPaymentService.processPayment(dto));
    }

    public void retrieveBalancePayment(BalanceRetrieveDto dto) {
        processOrder(balancePaymentService.processPayment(dto));
    }

    public String getStripePanel(int userId) {
        var subscription = subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLSPRO);
        UserSubscription userSubscription = userSubscriptionService
                .getUserSubBySubTypeIdNotTerminated(userId, subscription.getId())
                .orElseThrow(() -> new EntityNotFoundException("User subscription not found"));

        if (userSubscription.getIsTrial())
            throw new ConflictException("You in trial status");
        if (!userSubscription.getPaymentMethod().equals(PaymentMethod.STRIPE))
            throw new ConflictException("Your payment method not stripe");
        return stripePaymentService.getStripePanel(
                (String) userSubscription.getPaymentProviderData().get(StripePaymentService.CUSTOMER_ID)
        );
    }

    private void processOrder(ProcessPaymentDto dto) {
        orderService.processSuccessfulPayment(dto);
    }
}
