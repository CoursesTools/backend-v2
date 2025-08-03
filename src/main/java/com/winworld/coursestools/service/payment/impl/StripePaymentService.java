package com.winworld.coursestools.service.payment.impl;

import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.Discount;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.Recurring.Interval;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import com.winworld.coursestools.config.props.StripeProperties;
import com.winworld.coursestools.dto.payment.CreatePaymentLinkDto;
import com.winworld.coursestools.dto.payment.ProcessPaymentDto;
import com.winworld.coursestools.dto.payment.StripeRetrieveDto;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import com.winworld.coursestools.exception.exceptions.PaymentProcessingException;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import com.winworld.coursestools.service.payment.PaymentService;
import com.winworld.coursestools.service.user.UserDataService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.stripe.param.WebhookEndpointCreateParams.EnabledEvent.INVOICE__PAYMENT_SUCCEEDED;

@Service
@Slf4j
public class StripePaymentService extends PaymentService<StripeRetrieveDto> {
    public static final String CUSTOMER_ID = "customerId";
    public static final String SUBSCRIPTION_ID = "subscriptionId";

    private final StripeProperties properties;

    @Value("${urls.web}")
    private String webUrl;

    public StripePaymentService(StripeProperties properties) {
        super();
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = properties.secret();
    }

    public String getStripePanel(String customerID) {
        com.stripe.param.billingportal.SessionCreateParams params =
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerID)
                        .build();
        com.stripe.model.billingportal.Session session = null;
        try {
            session = com.stripe.model.billingportal.Session.create(params);
        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe panel for customer: {}", customerID, e);
            throw new ExternalServiceException("Failed to retrieve Stripe panel");
        }
        return session.getUrl();
    }

    public void cancelSubscription(@NotNull UserSubscription userSubscription) {
        var stripeSubscriptionId = (String) userSubscription.getPaymentProviderData().get(SUBSCRIPTION_ID);
        if (stripeSubscriptionId == null) {
            throw new EntityNotFoundException("Stripe subscription ID not found");
        }
        try {
            var resource = Subscription.retrieve(stripeSubscriptionId);
            resource.cancel(SubscriptionCancelParams.builder().build());
        } catch (StripeException e) {
            log.error("Failed to cancel Stripe subscription with ID: {}", stripeSubscriptionId, e);
            throw new ExternalServiceException("Failed to cancel subscription");
        }
    }

    @Override
    public String createPaymentLink(CreatePaymentLinkDto dto) {
        try {
            SessionCreateParams params = buildSessionParams(dto);
            Session session = Session.create(params);
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Failed to create Stripe payment link for order ID: {}", dto.getOrderId(), e);
            throw new PaymentProcessingException("Failed to create payment link");
        }
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.STRIPE;
    }

    @Override
    public ProcessPaymentDto processPayment(StripeRetrieveDto paymentRequest) {
        Invoice invoice = parseInvoiceFromWebhook(paymentRequest);
        Map<String, Object> paymentData = Map.of(
                CUSTOMER_ID, invoice.getCustomer(),
                SUBSCRIPTION_ID, invoice.getSubscription()
        );
        return ProcessPaymentDto.builder()
                .paymentProviderData(paymentData)
                .orderId(getOrderIdFromInvoice(invoice))
                .build();
    }

    private Integer getOrderIdFromInvoice(Invoice invoice) {
        String orderId = invoice.getLines().getData().get(0).getMetadata().get("order_id");
        return Integer.parseInt(orderId);
    }

    private Invoice parseInvoiceFromWebhook(StripeRetrieveDto paymentRequest) {
        try {
            Event event = Webhook.constructEvent(
                    paymentRequest.getPayload(),
                    paymentRequest.getSignature(),
                    properties.webhookSecret()
            );

            if (!event.getType().equals(INVOICE__PAYMENT_SUCCEEDED.getValue())) {
                throw new PaymentProcessingException(
                        "Invalid event type: " + event.getType()
                );
            }
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            return (Invoice) dataObjectDeserializer.deserializeUnsafe();
        } catch (SignatureVerificationException e) {
            throw new SecurityException(e.getMessage());
        } catch (EventDataObjectDeserializationException e) {
            log.error("Deserialization stripe error", e);
            throw new PaymentProcessingException("Failed to deserialize event data");
        }
    }

    private SessionCreateParams buildSessionParams(CreatePaymentLinkDto dto) {
        ProductData productData = buildProductData();
        PriceData priceData = buildPriceData(dto, productData);
        Discount discount = buildDiscount(dto);

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setSuccessUrl(webUrl + "/payment/success")
                .setCancelUrl(webUrl + "/payment/error")
                .addLineItem(
                        LineItem.builder()
                                .setPriceData(priceData)
                                .setQuantity(1L)
                                .build()
                )
                .setSubscriptionData(
                        SubscriptionData.builder()
                                .putMetadata("order_id", dto.getOrderId().toString())
                                .build()
                )
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(dto.getEmail());

        if (discount != null) {
            params.addDiscount(discount);
        }

        return params.build();
    }

    private ProductData buildProductData() {
        return ProductData.builder()
                .setName("CoursesTools Premium")
                .build();
    }

    private PriceData buildPriceData(CreatePaymentLinkDto dto, ProductData productData) {
        return PriceData.builder()
                .setCurrency("usd")
                .setUnitAmount(dto.getOriginalPrice().longValue())
                .setRecurring(
                        PriceData.Recurring.builder()
                                .setInterval(Interval.MONTH)
                                .setIntervalCount(1L)
                                .build()
                )
                .setProductData(productData)
                .build();
    }

    private Discount buildDiscount(CreatePaymentLinkDto dto) {
        if (dto.getCode() == null) {
            return null;
        }

        if (dto.getIsPartnershipCode()) {
            return Discount
                    .builder()
                    .setCoupon(properties.coupon())
                    .build();
        } else {
            return Discount.builder()
                    .setCoupon(dto.getCode())
                    .build();
        }
    }
}