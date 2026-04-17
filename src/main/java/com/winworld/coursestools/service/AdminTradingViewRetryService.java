package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.admin.TradingViewRetryJobFilterDto;
import com.winworld.coursestools.dto.admin.TradingViewRetryJobReadDto;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.TradingViewRetryJobMapper;
import com.winworld.coursestools.repository.TradingViewRetryJobRepository;
import com.winworld.coursestools.service.external.TradingViewRetryService;
import com.winworld.coursestools.specification.TradingViewRetryJobSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminTradingViewRetryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "nextAttemptAt", "firstEnqueuedAt", "attempts", "status", "type"
    );

    private final TradingViewRetryJobRepository repository;
    private final TradingViewRetryJobMapper mapper;
    private final TradingViewRetryService retryService;

    @Transactional(readOnly = true)
    public PageDto<TradingViewRetryJobReadDto> list(TradingViewRetryJobFilterDto filter, Pageable pageable) {
        validateSort(pageable.getSort());
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new DataValidationException("Page size must be <= " + MAX_PAGE_SIZE);
        }
        var page = repository.findAll(TradingViewRetryJobSpecification.from(filter), pageable)
                .map(mapper::toDto);
        return PageDto.of(page);
    }

    @Transactional(readOnly = true)
    public TradingViewRetryJobReadDto get(Integer id) {
        var job = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Retry job " + id + " not found"));
        return mapper.toDto(job);
    }

    public TradingViewRetryJobReadDto forceRetry(Integer id) {
        return mapper.toDto(retryService.forceRetry(id));
    }

    public void drop(Integer id) {
        retryService.drop(id);
    }

    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new DataValidationException(
                        "Unsupported sort property: " + order.getProperty()
                                + ". Allowed: " + ALLOWED_SORT_PROPERTIES
                );
            }
        }
    }
}
