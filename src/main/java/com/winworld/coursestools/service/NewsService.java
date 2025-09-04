package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.news.CreateNewsDto;
import com.winworld.coursestools.dto.news.NewsReadDto;
import com.winworld.coursestools.dto.news.UpdateNewsDto;
import com.winworld.coursestools.entity.News;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.NewsMapper;
import com.winworld.coursestools.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsService {
    private final NewsRepository newsRepository;
    private final NewsMapper newsMapper;

    public List<NewsReadDto> getAllNews() {
        return newsRepository.findAll().stream().map(newsMapper::toDto).toList();
    }

    public NewsReadDto createNews(CreateNewsDto dto) {
        News news = newsMapper.toEntity(dto);
        return newsMapper.toDto(newsRepository.save(news));
    }

    public NewsReadDto updateNews(UpdateNewsDto dto, Integer newsId) {
        var news = newsRepository.findById(newsId)
                .orElseThrow(() -> new EntityNotFoundException("News with id " + newsId + " not found"));
        newsMapper.updateEntityFromDto(dto, news);
        return newsMapper.toDto(newsRepository.save(news));
    }

    public NewsReadDto getNewsById(Integer newsId) {
        return newsMapper.toDto(newsRepository.findById(newsId)
                .orElseThrow(() -> new EntityNotFoundException("News with id " + newsId + " not found")));
    }

    public void deleteNews(Integer newsId) {
        newsRepository.deleteById(newsId);
    }
}
