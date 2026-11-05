package com.winworld.coursestools.service.ops.checks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winworld.coursestools.config.props.OpsProperties;
import com.winworld.coursestools.enums.OpsAlertCategory;
import com.winworld.coursestools.enums.OpsAlertSeverity;
import com.winworld.coursestools.service.ops.OpsAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import java.util.List;
import java.util.Optional;

/** Domains, TLS certificates and log shipping: the failures behind the October 2026 outage. */
@Component
@Slf4j
public class InfrastructureOpsChecks {
    private static final int DOMAIN_WARN_DAYS = 30;
    private static final int DOMAIN_CRITICAL_DAYS = 7;
    private static final int TLS_WARN_DAYS = 14;
    private static final int TLS_CRITICAL_DAYS = 3;
    private static final Duration LOG_STALE_AFTER = Duration.ofMinutes(15);
    // Registrar first: the registry's date jumps a year ahead on its own when a .com expires, which hid
    // the real expiry of winworldteam.com in October 2026.
    private static final List<String> RDAP_SOURCES = List.of(
            "https://rdap.namecheap.com/domain/",
            "https://rdap.verisign.com/com/v1/domain/");

    private final OpsAlertService opsAlertService;
    private final OpsProperties opsProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate client;

    public InfrastructureOpsChecks(OpsAlertService opsAlertService, OpsProperties opsProperties,
                                   ObjectMapper objectMapper) {
        this.opsAlertService = opsAlertService;
        this.opsProperties = opsProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(20).toMillis());
        this.client = new RestTemplate(requestFactory);
    }

    public void runDaily() {
        for (String domain : opsProperties.domains()) {
            OpsChecks.safely("domain " + domain, () -> checkDomain(domain));
        }
        for (String host : opsProperties.tlsHosts()) {
            OpsChecks.safely("tls " + host, () -> checkTls(host));
        }
    }

    public void runLogFreshness() {
        OpsChecks.safely("log freshness", this::checkLogFreshness);
    }

    public void announceStartup() {
        opsAlertService.raise(OpsAlertCategory.INFRASTRUCTURE, OpsAlertSeverity.INFO, "Backend started",
                "The backend (re)started at " + Instant.now() + ". Expected after a deploy; "
                        + "otherwise it may have crashed and restarted.", null);
    }

    void checkDomain(String domain) {
        Optional<Instant> expiry = registrarExpiry(domain);
        if (expiry.isEmpty()) {
            opsAlertService.track("infra.domain-lookup:" + domain, OpsAlertCategory.INFRASTRUCTURE,
                    OpsAlertSeverity.WARNING, true, "Cannot check domain " + domain,
                    "No RDAP source returned an expiry date for `" + domain + "`; check it at the registrar.");
            return;
        }
        opsAlertService.track("infra.domain-lookup:" + domain, OpsAlertCategory.INFRASTRUCTURE,
                OpsAlertSeverity.WARNING, false, "Cannot check domain " + domain, "");
        long daysLeft = ChronoUnit.DAYS.between(Instant.now(), expiry.get());
        opsAlertService.track("infra.domain:" + domain, OpsAlertCategory.INFRASTRUCTURE,
                daysLeft <= DOMAIN_CRITICAL_DAYS ? OpsAlertSeverity.CRITICAL : OpsAlertSeverity.WARNING,
                daysLeft <= DOMAIN_WARN_DAYS, "Domain " + domain + " expires soon",
                "`" + domain + "` expires in **" + daysLeft + " days** (" + expiry.get() + "). "
                        + "If it lapses, everything served from it stops: webhooks, APIs, logs. "
                        + "Renew it at the registrar and keep auto-renew on.");
    }

    Optional<Instant> registrarExpiry(String domain) {
        Instant earliest = null;
        for (String source : RDAP_SOURCES) {
            try {
                JsonNode rdap = objectMapper.readTree(client.getForObject(source + domain, String.class));
                for (JsonNode event : rdap.path("events")) {
                    if (event.path("eventAction").asText().contains("expiration")) {
                        Instant date = OffsetDateTime.parse(event.path("eventDate").asText()).toInstant();
                        earliest = earliest == null || date.isBefore(earliest) ? date : earliest;
                    }
                }
            } catch (Exception e) {
                log.warn("RDAP lookup of {} at {} failed: {}", domain, source, e.getMessage());
            }
        }
        return Optional.ofNullable(earliest);
    }

    void checkTls(String host) {
        String problem;
        OpsAlertSeverity severity = OpsAlertSeverity.WARNING;
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket()) {
            socket.connect(new InetSocketAddress(host, 443), 10_000);
            socket.setSoTimeout(10_000);
            socket.startHandshake();
            X509Certificate certificate = (X509Certificate) socket.getSession().getPeerCertificates()[0];
            long daysLeft = ChronoUnit.DAYS.between(Instant.now(), certificate.getNotAfter().toInstant());
            problem = daysLeft <= TLS_WARN_DAYS
                    ? "The TLS certificate of `" + host + "` expires in **" + daysLeft + " days**. "
                    + "Caddy normally renews 30 days ahead, so renewal is failing." : null;
            severity = daysLeft <= TLS_CRITICAL_DAYS ? OpsAlertSeverity.CRITICAL : OpsAlertSeverity.WARNING;
        } catch (Exception e) {
            problem = "TLS connection to `" + host + "` failed: `" + e.getMessage()
                    + "`. Browsers, Stripe and bots can't connect either.";
            severity = OpsAlertSeverity.CRITICAL;
        }
        opsAlertService.track("infra.tls:" + host, OpsAlertCategory.INFRASTRUCTURE, severity,
                problem != null, "TLS problem on " + host, problem == null ? "" : problem);
    }

    void checkLogFreshness() {
        String problem;
        try {
            Instant now = Instant.now();
            URI uri = UriComponentsBuilder.fromHttpUrl(opsProperties.loki().url())
                    .path("/loki/api/v1/query_range")
                    .queryParam("query", "{container_name=\"backend\"}")
                    .queryParam("limit", 1)
                    .queryParam("direction", "backward")
                    .queryParam("start", now.minus(Duration.ofHours(2)).toEpochMilli() * 1_000_000)
                    .queryParam("end", now.toEpochMilli() * 1_000_000)
                    .encode()
                    .build()
                    .toUri();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Scope-OrgID", opsProperties.loki().tenant());
            JsonNode response = objectMapper.readTree(
                    client.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody());
            long newestNanos = 0;
            for (JsonNode stream : response.path("data").path("result")) {
                for (JsonNode value : stream.path("values")) {
                    newestNanos = Math.max(newestNanos, Long.parseLong(value.path(0).asText("0")));
                }
            }
            Instant newest = newestNanos == 0 ? null : Instant.ofEpochSecond(0, newestNanos);
            problem = newest == null || newest.isBefore(now.minus(LOG_STALE_AFTER))
                    ? "Loki's newest backend log line is " + (newest == null ? "older than 2 hours" : "from " + newest)
                    + ". Log shipping is broken, so production logs are only in `docker logs backend`." : null;
        } catch (Exception e) {
            problem = "Could not query Loki at `" + opsProperties.loki().url() + "`: `" + e.getMessage() + "`.";
        }
        opsAlertService.track("infra.log-shipping", OpsAlertCategory.INFRASTRUCTURE, OpsAlertSeverity.WARNING,
                problem != null, "Backend logs are not reaching Loki", problem == null ? "" : problem);
    }
}
