package com.winworld.coursestools.dto.ops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.winworld.coursestools.enums.OpsAlertCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class SystemSettingsReadDto {
    @Schema(requiredMode = REQUIRED)
    private int gracePeriodDays;
    @Schema(requiredMode = REQUIRED)
    private int minGracePeriodDays;
    @Schema(requiredMode = REQUIRED)
    private int maxGracePeriodDays;
    @Schema(requiredMode = REQUIRED, description = "Value used whenever the stored setting cannot be read")
    private int defaultGracePeriodDays;
    @Schema(requiredMode = REQUIRED)
    private boolean opsAlertsPaused;
    @Schema(requiredMode = REQUIRED)
    private List<Category> categories;
    @Schema(requiredMode = REQUIRED)
    private List<TelegramRecipientReadDto> recipients;
    @Schema(requiredMode = REQUIRED, description = "Send back when changing the grace period (optimistic locking)")
    private int version;
    @Schema(description = "UTC")
    private LocalDateTime updatedAt;
    private String updatedByEmail;
    @Schema(requiredMode = REQUIRED, description = "Whether the ops Telegram bot token is configured on the server")
    private boolean telegramBotConfigured;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Category {
        @Schema(requiredMode = REQUIRED)
        private OpsAlertCategory category;
        @Schema(requiredMode = REQUIRED)
        private boolean enabled;
    }
}
