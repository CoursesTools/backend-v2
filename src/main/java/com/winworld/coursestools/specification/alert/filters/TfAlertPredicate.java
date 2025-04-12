package com.winworld.coursestools.specification.alert.filters;

import com.winworld.coursestools.dto.alert.AlertFilterDto;
import com.winworld.coursestools.entity.Alert;
import com.winworld.coursestools.specification.alert.AlertPredicateBuilder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Component;

@Component
public class TfAlertPredicate extends AlertPredicateBuilder {

    @Override
    protected Predicate buildPredicate(From<?, Alert> from, CriteriaBuilder cb, AlertFilterDto filter) {
        return from.get(Alert.TF).in(filter.getTfs());
    }

    @Override
    protected boolean canApply(AlertFilterDto filter) {
        return filter.getTfs() != null && !filter.getTfs().isEmpty();
    }
}
