package com.winworld.coursestools.service.payment.impl;

import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.Discount;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.Recurring.Interval;
import com.stripe.param.checkout.SessionCreateParams.SubscriptionData;
import com.winworld.coursestools.config.props.StripeProperties;
import com.winworld.coursestools.dto.order.ProcessOrderDto;
import com.winworld.coursestools.dto.payment.StripeRetrieveDto;
import com.winworld.coursestools.entity.Order;
import com.winworld.coursestools.enums.PaymentMethod;
import com.winworld.coursestools.exception.PaymentProcessingException;
import com.winworld.coursestools.exception.SecurityException;
import com.winworld.coursestools.service.OrderService;
import com.winworld.coursestools.service.payment.PaymentService;
import jakarta.annotation.PostConstruct;
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

    public StripePaymentService(OrderService orderService, StripeProperties properties) {
        super(orderService);
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = properties.secret();
    }

    @Override
    public String createPaymentLink(int orderId) {
        Order order = orderService.getOrderById(orderId);

        try {
            SessionCreateParams params = buildSessionParams(order);
            Session session = Session.create(params);
            return session.getUrl();
        } catch (StripeException e) {
            log.error("Failed to create Stripe payment link for order ID: {}", orderId, e);
            throw new PaymentProcessingException("Failed to create payment link");
        }
    }

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.STRIPE;
    }

    @Override
    public void processPayment(StripeRetrieveDto paymentRequest) {
        Invoice invoice = parseInvoiceFromWebhook(paymentRequest);
        Map<String, Object> paymentData = Map.of(
                CUSTOMER_ID, invoice.getCustomer(),
                SUBSCRIPTION_ID, invoice.getSubscription()
        );
        ProcessOrderDto dto = ProcessOrderDto.builder()
                .paymentProviderData(paymentData)
                .orderId(getOrderIdFromInvoice(invoice))
                .build();

        verifyPaymentMethodCompatibility(dto.getOrderId());
        orderService.processSuccessfulPayment(dto);
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

    private SessionCreateParams buildSessionParams(Order order) {
        ProductData productData = buildProductData();
        PriceData priceData = buildPriceData(order, productData);
        Discount discount = buildDiscount(order);

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
                                .putMetadata("order_id", order.getId().toString())
                                .build()
                )
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomerEmail(order.getUser().getEmail());

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

    private PriceData buildPriceData(Order order, ProductData productData) {
        return PriceData.builder()
                .setCurrency("usd")
                .setUnitAmount(order.getOriginalPrice().longValue())
                .setRecurring(
                        PriceData.Recurring.builder()
                                .setInterval(Interval.MONTH)
                                .setIntervalCount(1L)
                                .build()
                )
                .setProductData(productData)
                .build();
    }

    private Discount buildDiscount(Order order) {
        if (order.getPromoCode() == null) {
            return null;
        }

        if (order.getPromoCode().isPartnershipCode()) {
            return Discount
                    .builder()
                    .setCoupon(properties.coupon())
                    .build();
        } else {
            return Discount.builder()
                    .setCoupon(order.getPromoCode().getCode())
                    .build();
        }
    }
}