package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.code.CodeReadDto;
import com.winworld.coursestools.entity.Code;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface CodeMapper {

    @Mapping(target = "isPartnership", expression = "java(code.isPartnershipCode())")
    CodeReadDto toDto(Code code);
}
