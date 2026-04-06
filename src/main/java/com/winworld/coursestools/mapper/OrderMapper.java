package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.order.ReadOrderDto;
import com.winworld.coursestools.dto.payment.CreatePaymentLinkDto;
import com.winworld.coursestools.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.IGNORE;

@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public interface OrderMapper {

    @Mapping(target = "paymentLink", ignore = true)
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "plan", source = "plan.displayName")
    @Mapping(target = "tier", source = "plan.tier")
    @Mapping(target = "code", source = "code.code")
    ReadOrderDto toDto(Order order);

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "code", source = "code.code")
    @Mapping(target = "isPartnershipCode", expression = "java(order.getCode() != null && order.getCode().isPartnershipCode())")
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "planDisplayName", source = "plan.displayName")
    CreatePaymentLinkDto toCreateDto(Order order);
}
