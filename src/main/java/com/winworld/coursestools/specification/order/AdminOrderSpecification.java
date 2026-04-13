package com.winworld.coursestools.specification.order;

import com.winworld.coursestools.dto.admin.AdminOrderFilterDto;
import com.winworld.coursestools.entity.Order;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class AdminOrderSpecification {

    private AdminOrderSpecification() {}

    public static Specification<Order> from(AdminOrderFilterDto filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getOrderId() != null) {
                predicates.add(cb.equal(root.get("id"), filter.getOrderId()));
            }
            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), filter.getUserId()));
            }
            if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                var userJoin = root.join("user", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(userJoin.get("email")),
                        "%" + filter.getEmail().toLowerCase() + "%"));
            }
            if (filter.getTradingViewName() != null && !filter.getTradingViewName().isBlank()) {
                var socialJoin = root.join("user", JoinType.LEFT).join("social", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(socialJoin.get("tradingViewName")),
                        "%" + filter.getTradingViewName().toLowerCase() + "%"));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getPaymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), filter.getPaymentMethod()));
            }
            if (filter.getTier() != null) {
                predicates.add(cb.equal(root.get("plan").get("tier"), filter.getTier()));
            }
            if (filter.getOrderType() != null) {
                predicates.add(cb.equal(root.get("orderType"), filter.getOrderType()));
            }
            if (filter.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.getCreatedFrom()));
            }
            if (filter.getCreatedTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.getCreatedTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
