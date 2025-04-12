package com.winworld.coursestools.specification.userSubscription.filters;

import com.winworld.coursestools.dto.user.UsersSubscriptionsFilterDto;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.specification.userSubscription.UserSubscriptionPredicateBuilder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Component;

@Component
public class IsTrialUserSubscriptionPredicate extends UserSubscriptionPredicateBuilder {

    @Override
    protected Predicate buildPredicate(From<?, UserSubscription> from, CriteriaBuilder cb, UsersSubscriptionsFilterDto filter) {
        return cb.equal(from.get(UserSubscription.IS_TRIAL), filter.getIsTrial());
    }

    @Override
    protected boolean canApply(UsersSubscriptionsFilterDto filter) {
        return filter.getIsTrial() != null;
    }
}
