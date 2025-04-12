package com.winworld.coursestools.service.user;

import com.winworld.coursestools.dto.user.UserSubscriptionReadDto;
import com.winworld.coursestools.dto.user.UsersSubscriptionsFilterDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.specification.userSubscription.UserSubscriptionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserSubscriptionService {
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserMapper userMapper;
    private final UserSubscriptionSpecification userSubscriptionSpecification;

    public Optional<UserSubscription> getUserSubBySubType(int userId, int subscriptionTypeId) {
        return userSubscriptionRepository.getUserSubBySubType(subscriptionTypeId, userId);
    }

    public boolean hasEverHadSubscriptionOfType(int userId, int subscriptionTypeId) {
        return userSubscriptionRepository.hasEverHadSubscriptionOfType(subscriptionTypeId, userId);
    }

    public UserSubscription save(UserSubscription userSubscription) {
        return userSubscriptionRepository.save(userSubscription);
    }

    public List<UserSubscription> findAllExpiredSubscriptionsByStatus(SubscriptionStatus status) {
        return userSubscriptionRepository.findAllWithExpiredSubscriptionsByStatus(status);
    }

    public List<UserSubscription> findAllWithExpiredTrialSubscription() {
        return userSubscriptionRepository.findAllWithExpiredTrialSubscription();
    }

    public List<UserSubscriptionReadDto> getUserSubscriptionsByFilter(
            UsersSubscriptionsFilterDto filterDto, int userId
    ) {
        Specification<UserSubscription> specification = userSubscriptionSpecification.from(filterDto)
                .and((root, query, cb) ->
                        cb.equal(root.get(UserSubscription.USER).get(User.ID), userId)
                );
        return userSubscriptionRepository.findAll(specification)
                .stream()
                .map(userMapper::toDto)
                .toList();
    }
}
