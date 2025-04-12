package com.winworld.coursestools.dto.recovery;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecoveryEmailDto {
    private String email;
    private String url;
}
