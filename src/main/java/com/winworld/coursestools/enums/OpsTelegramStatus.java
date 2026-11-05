package com.winworld.coursestools.enums;

/** What happened to an ops alert's Telegram delivery. The alert itself is always kept in history. */
public enum OpsTelegramStatus {
    PENDING, SENT, PARTIAL, FAILED, PAUSED, CATEGORY_DISABLED, NO_RECIPIENTS, NOT_CONFIGURED
}
