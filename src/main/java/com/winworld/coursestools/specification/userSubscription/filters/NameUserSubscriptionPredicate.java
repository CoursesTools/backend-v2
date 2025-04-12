package com.winworld.coursestools.specification.userSubscription.filters;

import com.winworld.coursestools.dto.user.UsersSubscriptionsFilterDto;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.subscription.SubscriptionType;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.specification.userSubscription.UserSubscriptionPredicateBuilder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Component;

@Component
public class NameUserSubscriptionPredicate extends UserSubscriptionPredicateBuilder {

    @Override
    protected Predicate buildPredicate(From<?, UserSubscription> from, CriteriaBuilder cb, UsersSubscriptionsFilterDto filter) {
        return from
                .join(UserSubscription.PLAN)
                .join(SubscriptionPlan.SUBSCRIPTION_TYPE)
                .get(SubscriptionType.NAME)
                .in(filter.getNames());
    }

    @Override
    protected boolean canApply(UsersSubscriptionsFilterDto filter) {
        return filter.getNames() != null && !filter.getNames().isEmpty();
    }
}
