package com.winworld.coursestools.specification;

import org.springframework.data.jpa.domain.Specification;

public abstract class AbstractSpecification<T, V> {
    public abstract Specification<T> from(V from);
}
