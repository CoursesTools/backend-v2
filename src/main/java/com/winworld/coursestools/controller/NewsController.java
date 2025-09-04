package com.winworld.coursestools.controller;

import com.winworld.coursestools.dto.news.CreateNewsDto;
import com.winworld.coursestools.dto.news.NewsReadDto;
import com.winworld.coursestools.dto.news.UpdateNewsDto;
import com.winworld.coursestools.service.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public NewsReadDto createNews(@RequestBody CreateNewsDto dto) {
        return newsService.createNews(dto);
    }

    @PatchMapping("/{newsId}")
    @PreAuthorize("hasRole('ADMIN')")
    public NewsReadDto updateNews(@RequestBody UpdateNewsDto dto, @PathVariable Integer newsId) {
        return newsService.updateNews(dto, newsId);
    }

    @GetMapping("/{newsId}")
    @PreAuthorize("hasRole('ADMIN')")
    public NewsReadDto getNews(@PathVariable Integer newsId) {
        return newsService.getNewsById(newsId);
    }

    @DeleteMapping("/{newsId}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteNews(@PathVariable Integer newsId) {
        newsService.deleteNews(newsId);
    }
}
