package com.winworld.coursestools.specification;

import com.winworld.coursestools.dto.admin.TradingViewRetryJobFilterDto;
import com.winworld.coursestools.entity.TradingViewRetryJob;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class TradingViewRetryJobSpecification {

    private TradingViewRetryJobSpecification() {}

    public static Specification<TradingViewRetryJob> from(TradingViewRetryJobFilterDto filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), filter.getUserId()));
            }
            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }
            if (filter.getType() != null) {
                predicates.add(cb.equal(root.get("type"), filter.getType()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
