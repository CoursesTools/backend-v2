package com.winworld.coursestools.dto.ops;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class OpsAlertPageDto {
    @Schema(requiredMode = REQUIRED)
    private List<OpsAlertReadDto> items;
    @Schema(description = "Pass as beforeId to load the next page; null when there are no more results")
    private Long nextCursor;
    @Schema(requiredMode = REQUIRED, description = "Total matches for the current filters")
    private long total;
    @Schema(requiredMode = REQUIRED, description = "Lower-cased search words the server matched, for highlighting")
    private List<String> terms;
}
