package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.news.NewsReadDto;
import com.winworld.coursestools.entity.News;
import org.mapstruct.Mapper;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface NewsMapper {

    NewsReadDto toDto(News alert);

}
