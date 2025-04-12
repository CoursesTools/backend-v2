package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.PageDto;
import com.winworld.coursestools.dto.alert.AlertCategoriesReadDto;
import com.winworld.coursestools.dto.alert.AlertFilterDto;
import com.winworld.coursestools.dto.alert.AlertReadDto;
import com.winworld.coursestools.dto.alert.AlertSubscribeDto;
import com.winworld.coursestools.dto.alert.AlertSubscriptionCategoriesDto;
import com.winworld.coursestools.entity.Alert;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserAlert;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.mapper.AlertMapper;
import com.winworld.coursestools.repository.AlertRepository;
import com.winworld.coursestools.repository.user.UserAlertRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.specification.alert.AlertSpecification;
import com.winworld.coursestools.specification.alert.UserAlertSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {
    private final AlertMapper alertMapper;
    private final AlertRepository alertRepository;
    private final UserDataService userDataService;
    private final UserAlertRepository userAlertRepository;
    private final AlertSpecification alertSpecification;
    private final UserAlertSpecification userAlertSpecification;

    public PageDto<AlertReadDto> getAlertsByFilter(AlertFilterDto filterDto, Pageable pageable) {
        Specification<Alert> specification = alertSpecification.from(filterDto);
        return PageDto.of(
                alertRepository
                        .findAll(specification, pageable)
                        .map(alertMapper::toDto)
        );
    }

    public PageDto<AlertReadDto> getUserAlertsByFilter(int userId, AlertFilterDto filterDto, Pageable pageable) {
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
    public void subscribeOnAlerts(int userId, AlertSubscribeDto dto) {
        User user = userDataService.getUserById(userId);
        List<Alert> alerts = alertRepository.findAllById(dto.getAlertsIds());
        List<UserAlert> existingAlerts = userAlertRepository.findByUserIdAndAlertsIds(
                userId, dto.getAlertsIds()
        );

        if (!existingAlerts.isEmpty()) {
            throw new ConflictException("You already subscribed to these alerts: " +
                    existingAlerts
                            .stream()
                            .map(userAlert -> userAlert.getAlert().getId())
                            .toList()
            );
        }

        List<UserAlert> userAlerts = alerts.stream()
                .map(alert -> UserAlert.builder()
                        .user(user)
                        .alert(alert)
                        .properties(dto.getProperties())
                        .build())
                .toList();

        userAlertRepository.saveAll(userAlerts);
    }

    @Transactional
    public void unSubscribeOnAlerts(int userId, List<Integer> alertsIds) {
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

    public AlertCategoriesReadDto getUserAlertsCategories(int userId) {
        return alertRepository.getUserAlertsCategories(userId);
    }

    public AlertSubscriptionCategoriesDto getAlertSubscriptionCategories(boolean isMulti) {
        return null;
    }
}
