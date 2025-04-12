package com.winworld.coursestools.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCreateEvent {
    private int id;
    private String forwardedFor;
    private String email;
    private String generatedPassword;
}
