package com.winworld.coursestools.exception.exceptions;

/**
 * Thrown when the TradingView bot reports that a nickname does not exist on
 * TradingView (HTTP 404). This is a permanent input error (not transient) — the
 * retry queue cannot recover from it by waiting, and the automatic retry config
 * treats it as non-retryable via DataValidationException inheritance. Maps to
 * HTTP 400 in GlobalExceptionHandler.
 */
public class TradingViewUserNotFoundException extends DataValidationException {
    private final String tradingViewName;

    public TradingViewUserNotFoundException(String tradingViewName) {
        super("TradingView username '" + tradingViewName
                + "' was not found on TradingView. Verify the nickname is correct.");
        this.tradingViewName = tradingViewName;
    }

    public String getTradingViewName() {
        return tradingViewName;
    }
}
