package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.auth.AuthSignUpDto;
import com.winworld.coursestools.entity.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface AuthMapper {

    @Mapping(target = "password", ignore = true)
    @Mapping(source = "tradingViewName", target = "tradingViewName")
    @Mapping(source = "email", target = "email")
    User toEntity(AuthSignUpDto dto);
}
