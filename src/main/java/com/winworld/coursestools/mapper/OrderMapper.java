package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.order.ReadOrderDto;
import com.winworld.coursestools.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.IGNORE;

@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public interface OrderMapper {

    @Mapping(target = "paymentLink", ignore = true)
    @Mapping(target = "userId", source = "user.id")
    ReadOrderDto toDto(Order order);
}
