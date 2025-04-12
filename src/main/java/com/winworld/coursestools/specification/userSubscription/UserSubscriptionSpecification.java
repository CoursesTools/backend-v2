package com.winworld.coursestools.specification.userSubscription;

import com.winworld.coursestools.dto.user.UsersSubscriptionsFilterDto;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.specification.AbstractSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserSubscriptionSpecification extends AbstractSpecification<UserSubscription, UsersSubscriptionsFilterDto> {
    private final List<UserSubscriptionPredicateBuilder> userSubscriptionPredicateBuilders;

    @Override
    public Specification<UserSubscription> from(UsersSubscriptionsFilterDto from) {
        Specification<UserSubscription> specification = Specification.where(null);
        for (var builder : userSubscriptionPredicateBuilders) {
            specification = specification.and(
                    ((root, query, cb) ->
                            builder.predicate(root, cb, from))
            );
        }
        return specification;
    }
}
