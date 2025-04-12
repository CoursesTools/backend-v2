package com.winworld.coursestools.util;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnvironmentUtil {
    private final Environment environment;

    public boolean isProd() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    public boolean isDev() {
        return environment.acceptsProfiles(Profiles.of("dev"));
    }
}
