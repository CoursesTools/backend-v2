package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.admin.TradingViewRetryJobReadDto;
import com.winworld.coursestools.entity.TradingViewRetryJob;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import static org.mapstruct.ReportingPolicy.IGNORE;

@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public interface TradingViewRetryJobMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userEmail", source = "user.email")
    @Mapping(target = "tradingViewName", source = "user.social.tradingViewName")
    TradingViewRetryJobReadDto toDto(TradingViewRetryJob job);
}
