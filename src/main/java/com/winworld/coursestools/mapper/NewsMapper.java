package com.winworld.coursestools.mapper;

import com.winworld.coursestools.dto.news.CreateNewsDto;
import com.winworld.coursestools.dto.news.NewsReadDto;
import com.winworld.coursestools.dto.news.UpdateNewsDto;
import com.winworld.coursestools.entity.News;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import static org.mapstruct.ReportingPolicy.WARN;

@Mapper(componentModel = "spring", unmappedTargetPolicy = WARN)
public interface NewsMapper {

    NewsReadDto toDto(News alert);

    News toEntity(CreateNewsDto dto);

    void updateEntityFromDto(UpdateNewsDto dto, @MappingTarget News news);
}
