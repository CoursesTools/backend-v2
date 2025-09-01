package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.auth.BasicAuthSignUpDto;
import com.winworld.coursestools.dto.auth.GoogleAuthSignUpDto;
import com.winworld.coursestools.entity.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface AuthMapper {

    @Mapping(target = "password", ignore = true)
    @Mapping(target = "email", expression = "java(dto.getEmail().toLowerCase())")
    User toEntity(BasicAuthSignUpDto dto);

    @Mapping(target = "password", ignore = true)
    @Mapping(target = "email", expression = "java(email.toLowerCase())")
    User toEntity(GoogleAuthSignUpDto dto, String email);
}
