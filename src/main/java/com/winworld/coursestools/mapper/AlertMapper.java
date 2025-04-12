package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.alert.AlertReadDto;
import com.winworld.coursestools.entity.Alert;
import org.mapstruct.Mapper;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface AlertMapper {

    AlertReadDto toDto(Alert alert);

}
