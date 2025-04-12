package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.news.NewsReadDto;
import com.winworld.coursestools.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RequestMapping("/v1/news")
@RestController
public class NewsController {
    private final NewsService newsService;

    @GetMapping
    public List<NewsReadDto> getAllNews() {
        return newsService.getAllNews();
    }
}
