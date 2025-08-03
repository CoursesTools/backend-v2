package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.CountDto;
import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.alert.AlertCategoriesReadDto;
import com.winworld.coursestools.dto.alert.AlertFilterDto;
import com.winworld.coursestools.dto.alert.AlertReadDto;
import com.winworld.coursestools.dto.alert.AlertSubscribeDto;
import com.winworld.coursestools.dto.alert.AlertSubscriptionCategoriesDto;
import com.winworld.coursestools.entity.Alert;
import com.winworld.coursestools.entity.base.BaseEntity;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserAlert;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.mapper.AlertMapper;
import com.winworld.coursestools.repository.AlertRepository;
import com.winworld.coursestools.repository.user.UserAlertRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import com.winworld.coursestools.specification.alert.AlertSpecification;
import com.winworld.coursestools.specification.alert.UserAlertSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertMapper alertMapper;
    private final AlertRepository alertRepository;
    private final UserDataService userDataService;
    private final UserAlertRepository userAlertRepository;
    private final AlertSpecification alertSpecification;
    private final UserAlertSpecification userAlertSpecification;
    private final UserSubscriptionService userSubscriptionService;
    private final SubscriptionService subscriptionService;

    public PageDto<AlertReadDto> getAlertsByFilter(int userId, AlertFilterDto filterDto, Pageable pageable) {
        checkUserSubscription(userId);
        Specification<Alert> specification = alertSpecification.from(filterDto);
        return PageDto.of(
                alertRepository
                        .findAll(specification, pageable)
                        .map(alertMapper::toDto)
        );
    }

    public PageDto<AlertReadDto> getUserAlertsByFilter(int userId, AlertFilterDto filterDto, Pageable pageable) {
        checkUserSubscription(userId);
        Specification<UserAlert> specification = userAlertSpecification.from(filterDto)
                .and((root, query, cb) ->
                        cb.equal(root.get(UserAlert.USER).get(User.ID), userId)
                );
        return PageDto.of(
                userAlertRepository
                        .findAll(specification, pageable)
                        .map(UserAlert::getAlert)
                        .map(alertMapper::toDto)
        );
    }

    @Transactional
    public CountDto subscribeOnAlerts(int userId, AlertSubscribeDto dto) {
        checkUserSubscription(userId);
        User user = userDataService.getUserById(userId);
        Specification<Alert> specification = alertSpecification.from(dto);
        List<Alert> alerts = alertRepository.findAll(specification);
        List<UserAlert> existingAlerts = userAlertRepository.findByUserIdAndAlertsIds(
                userId, alerts.stream().map(BaseEntity::getId).toList()
        );
        Map<Integer, UserAlert> existingAlertsMap = existingAlerts.stream()
                .collect(Collectors.toMap(
                        userAlert -> userAlert.getAlert().getId(),
                        userAlert -> userAlert
                ));

        List<UserAlert> toCreate = new ArrayList<>();
        List<UserAlert> toUpdate = new ArrayList<>();

        for (Alert alert : alerts) {
            UserAlert existingAlert = existingAlertsMap.get(alert.getId());
            if (existingAlert != null) {
                existingAlert.setProperties(dto.getProperties());
                toUpdate.add(existingAlert);
            } else {
                toCreate.add(UserAlert.builder()
                        .user(user)
                        .alert(alert)
                        .properties(dto.getProperties())
                        .build());
            }
        }

        if (!toUpdate.isEmpty()) {
            userAlertRepository.saveAll(toUpdate);
        }

        if (!toCreate.isEmpty()) {
            userAlertRepository.saveAll(toCreate);
        }

        return new CountDto(toUpdate.size() + toCreate.size());
    }

    @Transactional
    public void unSubscribeOnAlerts(int userId, List<Integer> alertsIds) {
        checkUserSubscription(userId);
        List<UserAlert> userAlerts = userAlertRepository.findByUserIdAndAlertsIds(userId, alertsIds);
        if (userAlerts.size() != alertsIds.size()) {
            List<Integer> notSubscribedAlerts = alertsIds.stream()
                    .filter(id -> userAlerts.stream()
                            .noneMatch(userAlert -> userAlert.getId().equals(id)))
                    .toList();
            throw new ConflictException("You not subscribe on alerts: " + notSubscribedAlerts);
        }
        userAlertRepository.deleteAll(userAlerts);
    }

    @Transactional
    public void unSubscribeOnAllAlerts(int userId) {
        checkUserSubscription(userId);
        userAlertRepository.deleteAllByUser_Id(userId);
    }

    public AlertCategoriesReadDto getUserAlertsCategories(int userId) {
        checkUserSubscription(userId);
        return alertRepository.getUserAlertsCategories(userId);
    }

    public AlertSubscriptionCategoriesDto getAlertSubscriptionCategories(int userId, boolean isMulti) {
        checkUserSubscription(userId);
        var types = alertRepository.getAllTypes(isMulti);
        AlertSubscriptionCategoriesDto categories = new AlertSubscriptionCategoriesDto();
        types.forEach(type -> {
            categories.getTypes().add(new AlertSubscriptionCategoriesDto.Type(
                    type,
                    alertRepository.getAllAssetsByType(type, isMulti),
                    alertRepository.getAllBrokersByType(type, isMulti)
            ));
        });
        categories.setEvents(alertRepository.getAllEvents(isMulti));
        categories.setTimeFrames(alertRepository.getAllTimeFrames(isMulti));
        return categories;
    }

    private void checkUserSubscription(int userId) {
        var subscription = subscriptionService.getSubscription(SubscriptionName.COURSESTOOLSPRO);
        userSubscriptionService.getUserSubBySubTypeIdNotTerminated(userId, subscription.getId())
                .orElseThrow(() -> new ConflictException("You must have a subscription to use this feature"));
    }
}
