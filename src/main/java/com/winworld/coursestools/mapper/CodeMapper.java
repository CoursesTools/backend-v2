package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.code.CodeReadDto;
import com.winworld.coursestools.dto.code.PromoCodeCreateDto;
import com.winworld.coursestools.entity.Code;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface CodeMapper {

    @Mapping(target = "isPartnership", expression = "java(code.isPartnershipCode())")
    CodeReadDto toDto(Code code);

    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orders", ignore = true)
    @Mapping(target = "owner", ignore = true)
    Code toEntity(PromoCodeCreateDto dto);
}
