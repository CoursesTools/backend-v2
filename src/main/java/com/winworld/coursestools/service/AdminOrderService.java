package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.admin.AdminOrderFilterDto;
import com.winworld.coursestools.dto.admin.AdminOrderReadDto;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.mapper.OrderMapper;
import com.winworld.coursestools.repository.OrderRepository;
import com.winworld.coursestools.specification.order.AdminOrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "createdAt", "totalPrice", "status", "paymentMethod"
    );

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public PageDto<AdminOrderReadDto> getOrders(AdminOrderFilterDto filter, Pageable pageable) {
        validateSort(pageable.getSort());
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new DataValidationException("Page size must be <= " + MAX_PAGE_SIZE);
        }

        var page = orderRepository.findAll(AdminOrderSpecification.from(filter), pageable)
                .map(orderMapper::toAdminDto);
        return PageDto.of(page);
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
