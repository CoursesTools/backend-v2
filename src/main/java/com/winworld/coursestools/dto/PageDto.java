package com.winworld.coursestools.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Data
public class PageDto<T> {
    @Schema(requiredMode = REQUIRED)
    private List<T> content;
    @Schema(requiredMode = REQUIRED)
    private int pageNumber;
    @Schema(requiredMode = REQUIRED)
    private int pageSize;
    @Schema(requiredMode = REQUIRED)
    private int totalPages;
    @Schema(requiredMode = REQUIRED)
    private long totalElements;
    @Schema(requiredMode = REQUIRED)
    private boolean isFirst;
    @Schema(requiredMode = REQUIRED)
    private boolean isLast;
    
    public static <T> PageDto<T> of(Page<T> page) {
        PageDto<T> pageDto = new PageDto<>();
        pageDto.setContent(page.getContent());
        pageDto.setPageNumber(page.getNumber());
        pageDto.setPageSize(page.getSize());
        pageDto.setTotalPages(page.getTotalPages());
        pageDto.setTotalElements(page.getTotalElements());
        pageDto.setFirst(page.isFirst());
        pageDto.setLast(page.isLast());
        return pageDto;
    }
}