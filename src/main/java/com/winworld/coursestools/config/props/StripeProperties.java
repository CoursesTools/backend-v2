package com.winworld.coursestools.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("payment-platforms.stripe")
public record StripeProperties(
        String secret,
        String webhookSecret,
        String coupon
) {
}
