package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.transaction.TransactionReadDto;
import com.winworld.coursestools.entity.user.UserTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface TransactionMapper {

    @Mapping(target = "type", source = "transactionType")
    TransactionReadDto toDto(UserTransaction transaction);
}
