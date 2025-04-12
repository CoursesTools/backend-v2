package com.winworld.coursestools.specification.alert;

import com.winworld.coursestools.dto.alert.AlertFilterDto;
import com.winworld.coursestools.entity.Alert;
import com.winworld.coursestools.specification.AbstractSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AlertSpecification extends AbstractSpecification<Alert, AlertFilterDto> {
    private final List<AlertPredicateBuilder> alertPredicateBuilders;

    @Override
    public Specification<Alert> from(AlertFilterDto from) {
        Specification<Alert> specification = Specification.where(null);
        for (var builder : alertPredicateBuilders) {
            specification = specification.and(
                    ((root, query, cb) ->
                            builder.predicate(root, cb, from))
            );
        }
        return specification;
    }
}
