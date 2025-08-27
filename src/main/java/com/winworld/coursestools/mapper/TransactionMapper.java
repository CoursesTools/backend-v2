package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.transaction.TransactionCreateDto;
import com.winworld.coursestools.dto.transaction.TransactionReadDto;
import com.winworld.coursestools.dto.transaction.WithdrawRequestDto;
import com.winworld.coursestools.entity.user.UserTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface TransactionMapper {

    @Mapping(target = "type", source = "transactionType")
    TransactionReadDto toDto(UserTransaction transaction);

    @Mapping(target = "transactionId", source = "transaction.id")
    @Mapping(target = "currency", expression = "java(com.winworld.coursestools.enums.Currency.USD)")
    @Mapping(target = "amount", ignore = true)
    @Mapping(target = "email", source = "transaction.user.email")
    WithdrawRequestDto toDto(UserTransaction transaction, String wallet, String secret);

    UserTransaction toEntity(TransactionCreateDto dto);
}
