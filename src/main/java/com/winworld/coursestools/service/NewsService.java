package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.news.NewsReadDto;
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
}
