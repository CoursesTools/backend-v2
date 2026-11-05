package com.winworld.coursestools.service.ops;

import com.winworld.coursestools.config.props.OpsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TelegramHtmlTest {

    @Test
    void customerTextCannotInjectTelegramMarkup() {
        String html = TelegramHtml.fromMarkdown("said: <b>free</b> & <a href='x'>click</a>");

        assertEquals("said: &lt;b&gt;free&lt;/b&gt; &amp; &lt;a href='x'&gt;click&lt;/a&gt;", html);
    }

    @Test
    void supportedMarkdownBecomesTelegramHtml() {
        String html = TelegramHtml.fromMarkdown("### Customers\n- **3** new `evt_1` [Stripe](https://dashboard.stripe.com/x)");

        assertEquals("<b>Customers</b>\n• <b>3</b> new <code>evt_1</code> "
                + "<a href=\"https://dashboard.stripe.com/x\">Stripe</a>", html);
    }

    @Test
    void onlyHttpLinksAreLinked() {
        String html = TelegramHtml.fromMarkdown("[x](javascript:alert(1)) [y](tg://resolve)");

        assertFalse(html.contains("<a "));
    }

    @Test
    void telegramClientNeverLeaksTheBotTokenInErrorText() {
        OpsProperties properties = new OpsProperties(true, "https://admin.example", Duration.ofHours(6),
                new OpsProperties.Telegram("123456:SECRET-token", "https://api.telegram.org"),
                new OpsProperties.AlertBot("", ""), new OpsProperties.Loki("", "backend"),
                "/api/v1/payments/stripe", List.of(), List.of());
        OpsTelegramClient client = new OpsTelegramClient(properties, new ObjectMapper());

        String redacted = client.redact(
                "I/O error on POST request for \"https://api.telegram.org/bot123456:SECRET-token/sendMessage\"");

        assertFalse(redacted.contains("SECRET"));
        assertEquals("I/O error on POST request for \"https://api.telegram.org/bot<redacted>/sendMessage\"", redacted);
    }
}
