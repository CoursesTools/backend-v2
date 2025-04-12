package com.winworld.coursestools.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;

public abstract class AbstractPredicateBuilder<X, T> {
    public Predicate predicate(From<?, X> from, CriteriaBuilder cb, T filter) {
        if (!canApply(filter)) {
            return cb.conjunction();
        }
        return buildPredicate(from, cb, filter);
    };

    protected abstract Predicate buildPredicate(From<?, X> from, CriteriaBuilder cb, T filter);
    protected abstract boolean canApply(T filter);

    protected final Predicate equal(From<?, X> from, CriteriaBuilder cb, String field, Object value) {
        return cb.equal(from.get(field), value);
    }
}
