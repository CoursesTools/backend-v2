package com.winworld.coursestools.service.payment.impl;

import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.Subscription;
import com.stripe.model.Coupon;
import com.stripe.model.Customer;
import com.stripe.model.CustomerCollection;
import com.stripe.model.InvoiceItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import com.stripe.param.InvoiceCreateParams;
import com.stripe.param.InvoiceItemCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.CouponCreateParams;
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
import com.winworld.coursestools.enums.Currency;
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
import java.math.BigDecimal;

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
            log.warn("Stripe subscription id not found, user id {}", userSubscription.getUser().getId());
            return;
        }
        try {
            var resource = Subscription.retrieve(stripeSubscriptionId);
            resource.cancel(SubscriptionCancelParams.builder().build());
        } catch (StripeException e) {
            log.error("Failed to cancel Stripe subscription with ID: {}", stripeSubscriptionId, e);
        }
    }

    /**
     * Создает купон в Stripe с процентной скидкой
     * @param couponId уникальный идентификатор купона
     * @param percentOff процент скидки (например, 20 для 20% скидки)
     * @return созданный купон
     * @throws ExternalServiceException если не удалось создать купон
     */
    public Coupon createPercentageCoupon(String couponId, Long percentOff) {
        try {
            CouponCreateParams.Builder paramsBuilder = CouponCreateParams.builder()
                    .setId(couponId.toUpperCase())
                    .setPercentOff(BigDecimal.valueOf(percentOff))
                    .setDuration(CouponCreateParams.Duration.ONCE);

            CouponCreateParams params = paramsBuilder.build();
            Coupon coupon = Coupon.create(params);

            log.info("Successfully created Stripe coupon with ID: {}, discount: {}%", couponId, percentOff);
            return coupon;
        } catch (StripeException e) {
            log.error("Failed to create Stripe coupon with ID: {}", couponId, e);
            throw new ExternalServiceException("Failed to create coupon: " + e.getMessage());
        }
    }

    /**
     * Создает купон с фиксированной суммой скидки
     * @param couponId уникальный идентификатор купона
     * @param amountOff сумма скидки в центах (например, 500 для $5.00)
     * @return созданный купон
     * @throws ExternalServiceException если не удалось создать купон
     */
    public Coupon createFixedAmountCoupon(String couponId, Long amountOff) {
        try {
            CouponCreateParams.Builder paramsBuilder = CouponCreateParams.builder()
                    .setId(couponId.toUpperCase())
                    .setAmountOff(amountOff)
                    .setCurrency(Currency.USD.name().toLowerCase())
                    .setDuration(CouponCreateParams.Duration.ONCE);


            CouponCreateParams params = paramsBuilder.build();
            Coupon coupon = Coupon.create(params);

            log.info("Successfully created Stripe fixed amount coupon with ID: {}, discount: {}", couponId, amountOff);
            return coupon;
        } catch (StripeException e) {
            log.error("Failed to create Stripe fixed amount coupon with ID: {}", couponId, e);
            throw new ExternalServiceException("Failed to create fixed amount coupon: " + e.getMessage());
        }
    }

    /**
     * Создает кастомный Stripe Invoice для одноразового платежа
     * @param order заказ для которого создается invoice
     * @param email email пользователя
     * @param description описание invoice (опционально)
     * @return URL на hosted invoice page
     * @throws ExternalServiceException если не удалось создать invoice
     */
    public String createCustomInvoice(com.winworld.coursestools.entity.Order order, String email, String description) {
        try {
            // 1. Find or create customer
            String customerId = findOrCreateCustomer(email);

            // 2. Create invoice item
            InvoiceItemCreateParams itemParams = InvoiceItemCreateParams.builder()
                    .setCustomer(customerId)
                    .setAmount(order.getTotalPrice().longValue())
                    .setCurrency("usd")
                    .setDescription(description != null ? description : "CoursesTools Pro " + order.getPlan().getDisplayName())
                    .putMetadata("order_id", order.getId().toString())
                    .build();
            InvoiceItem.create(itemParams);

            // 3. Create invoice
            InvoiceCreateParams invoiceParams = InvoiceCreateParams.builder()
                    .setCustomer(customerId)
                    .setAutoAdvance(true)  // Automatically finalize and attempt payment
                    .setCollectionMethod(InvoiceCreateParams.CollectionMethod.CHARGE_AUTOMATICALLY)
                    .build();
            Invoice invoice = Invoice.create(invoiceParams);

            // 4. Finalize invoice
            invoice = invoice.finalizeInvoice();

            log.info("Successfully created custom Stripe invoice {} for order {}", invoice.getId(), order.getId());
            return invoice.getHostedInvoiceUrl();
        } catch (StripeException e) {
            log.error("Failed to create custom Stripe invoice for order ID: {}", order.getId(), e);
            throw new ExternalServiceException("Failed to create custom invoice: " + e.getMessage());
        }
    }

    /**
     * Находит существующего клиента по email или создает нового
     * @param email email клиента
     * @return ID клиента в Stripe
     * @throws StripeException если произошла ошибка при работе со Stripe API
     */
    private String findOrCreateCustomer(String email) throws StripeException {
        // Try to find existing customer
        CustomerListParams listParams = CustomerListParams.builder()
                .setEmail(email)
                .setLimit(1L)
                .build();
        CustomerCollection customers = Customer.list(listParams);

        if (!customers.getData().isEmpty()) {
            return customers.getData().get(0).getId();
        }

        // Create new customer if not found
        CustomerCreateParams createParams = CustomerCreateParams.builder()
                .setEmail(email)
                .build();
        Customer customer = Customer.create(createParams);
        log.info("Created new Stripe customer {} for email {}", customer.getId(), email);
        return customer.getId();
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

        // Build payment data - subscriptionId can be null for one-time invoices
        Map<String, Object> paymentData = new java.util.HashMap<>();
        paymentData.put(CUSTOMER_ID, invoice.getCustomer());
        if (invoice.getSubscription() != null) {
            paymentData.put(SUBSCRIPTION_ID, invoice.getSubscription());
        }

        return ProcessPaymentDto.builder()
                .paymentProviderData(paymentData)
                .orderId(getOrderIdFromInvoice(invoice))
                .build();
    }

    private Integer getOrderIdFromInvoice(Invoice invoice) {
        if (invoice.getLines() == null || invoice.getLines().getData().isEmpty()) {
            throw new PaymentProcessingException("Invoice has no line items");
        }

        var lineItem = invoice.getLines().getData().get(0);
        if (lineItem.getMetadata() == null || !lineItem.getMetadata().containsKey("order_id")) {
            throw new PaymentProcessingException("Order ID not found in invoice metadata");
        }

        String orderId = lineItem.getMetadata().get("order_id");
        try {
            return Integer.parseInt(orderId);
        } catch (NumberFormatException e) {
            throw new PaymentProcessingException("Invalid order ID format: " + orderId);
        }
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
                    .setCoupon(dto.getCode().toUpperCase())
                    .build();
        }
    }
}
