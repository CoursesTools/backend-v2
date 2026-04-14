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
import com.winworld.coursestools.entity.user.UserAlertId;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionName;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.event.UserAlertsChangeEvent;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.mapper.AlertMapper;
import com.winworld.coursestools.repository.AlertRepository;
import com.winworld.coursestools.repository.user.UserAlertRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import com.winworld.coursestools.specification.alert.AlertSpecification;
import com.winworld.coursestools.specification.alert.UserAlertSpecification;
import com.winworld.coursestools.validation.validator.AlertValidator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final AlertValidator alertValidator;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

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
        UserSubscription userSubscription = checkUserSubscription(userId);
        User user = userDataService.getUserById(userId);

        // 1. Находим все алерты по фильтру
        Specification<Alert> specification = alertSpecification.from(dto);
        List<Alert> alerts = alertRepository.findAll(specification);

        if (alerts.isEmpty()) {
            return new CountDto(0);
        }

        // Validate indicator permissions based on user's tier
        SubscriptionTier tier = userSubscription.getPlan().getTier();
        int subTypeId = userSubscription.getPlan().getSubscriptionType().getId();
        alertValidator.validateIndicatorPermissions(tier, subTypeId, alerts);

        // 2. Одним запросом получаем существующие связи
        List<Integer> alertIds = alerts.stream()
                .map(BaseEntity::getId)
                .toList();

        // 3. Разделяем на update и insert
        List<UserAlert> toUpdate = new ArrayList<>();
        List<UserAlert> toInsert = new ArrayList<>();

        List<UserAlertId> existingIds = userAlertRepository
                .findIdsByUserIdAndAlertIds(userId, alertIds);

        Set<UserAlertId> existingIdSet = new HashSet<>(existingIds);

        for (Alert alert : alerts) {
            UserAlertId id = new UserAlertId(user.getId(), alert.getId());
            if (existingIdSet.contains(id)) {
                // UPDATE
                UserAlert ua = new UserAlert();
                ua.setId(id);
                ua.setUser(user);
                ua.setAlert(alert);
                ua.setProperties(dto.getProperties());
                toUpdate.add(ua);
            } else {
                // INSERT
                UserAlert ua = new UserAlert();
                ua.setId(id);
                ua.setUser(user);
                ua.setAlert(alert);
                ua.setProperties(dto.getProperties());
                toInsert.add(ua);
            }
        }


        // 4. Сохраняем батчами
        if (!toUpdate.isEmpty()) {
            userAlertRepository.saveAll(toUpdate); // Hibernate сделает batch UPDATE
        }

        if (!toInsert.isEmpty()) {
            // persist вместо merge → Hibernate не делает SELECT перед INSERT
            toInsert.forEach(entityManager::persist);
            entityManager.flush(); // отправляем батч INSERT
        }

        String telegramId = user.getSocial().getTelegramId();
        eventPublisher.publishEvent(new UserAlertsChangeEvent(telegramId));

        return new CountDto(toUpdate.size() + toInsert.size());
    }


    @Transactional
    public void unSubscribeOnAlerts(int userId, List<Integer> alertsIds) {
        checkUserSubscription(userId);
        List<UserAlertId> userAlertIds = userAlertRepository.findIdsByUserIdAndAlertIds(userId, alertsIds);
        if (userAlertIds.size() != alertsIds.size()) {
            List<Integer> notSubscribedAlerts = alertsIds.stream()
                    .filter(id -> userAlertIds.stream()
                            .noneMatch(userAlertId -> userAlertId.getAlertId().equals(id)))
                    .toList();
            throw new ConflictException("You not subscribe on alerts: " + notSubscribedAlerts);
        }
        User user = userDataService.getUserById(userId);
        eventPublisher.publishEvent(new UserAlertsChangeEvent(user.getSocial().getTelegramId()));
        userAlertRepository.deleteAllById(userAlertIds);
    }

    @Transactional
    public void unSubscribeOnAllAlerts(int userId) {
        userAlertRepository.deleteAllByUser_Id(userId);
        User user = userDataService.getUserById(userId);
        eventPublisher.publishEvent(new UserAlertsChangeEvent(user.getSocial().getTelegramId()));
    }

    public AlertCategoriesReadDto getUserAlertsCategories(int userId) {
        checkUserSubscription(userId);
        return alertRepository.getUserAlertsCategories(userId);
    }

    @Transactional(readOnly = true)
    public AlertSubscriptionCategoriesDto getAlertSubscriptionCategories(int userId, boolean isMulti, List<String> requestedIndicators) {
        UserSubscription userSubscription = checkUserSubscription(userId);
        SubscriptionTier tier = userSubscription.getPlan().getTier();
        int subTypeId = userSubscription.getPlan().getSubscriptionType().getId();
        Set<String> tierAllowed = alertValidator.getAllowedIndicators(tier, subTypeId);

        // Determine effective indicator filter:
        // 1. If tier is restricted AND query param provided: intersect (respect both)
        // 2. If tier is restricted only: use tier restriction
        // 3. If query param provided only: use query param
        // 4. Neither: no filter (return all)
        List<String> effectiveFilter = null;
        if (!tierAllowed.isEmpty() && requestedIndicators != null && !requestedIndicators.isEmpty()) {
            effectiveFilter = requestedIndicators.stream()
                    .filter(tierAllowed::contains)
                    .toList();
        } else if (!tierAllowed.isEmpty()) {
            effectiveFilter = new ArrayList<>(tierAllowed);
        } else if (requestedIndicators != null && !requestedIndicators.isEmpty()) {
            effectiveFilter = requestedIndicators;
        }

        AlertSubscriptionCategoriesDto categories = new AlertSubscriptionCategoriesDto();
        final List<String> indicatorFilter = effectiveFilter;

        if (indicatorFilter != null && !indicatorFilter.isEmpty()) {
            var types = alertRepository.getAllTypesByIndicators(isMulti, indicatorFilter);
            types.forEach(type -> categories.getTypes().add(new AlertSubscriptionCategoriesDto.Type(
                    type,
                    alertRepository.getAllAssetsByTypeAndIndicators(type, isMulti, indicatorFilter),
                    alertRepository.getAllBrokersByTypeAndIndicators(type, isMulti, indicatorFilter)
            )));
            categories.setEvents(alertRepository.getAllEventsByIndicators(isMulti, indicatorFilter));
            categories.setTimeFrames(alertRepository.getAllTimeFramesByIndicators(isMulti, indicatorFilter));
            categories.setIndicators(indicatorFilter);
        } else {
            var types = alertRepository.getAllTypes(isMulti);
            types.forEach(type -> categories.getTypes().add(new AlertSubscriptionCategoriesDto.Type(
                    type,
                    alertRepository.getAllAssetsByType(type, isMulti),
                    alertRepository.getAllBrokersByType(type, isMulti)
            )));
            categories.setEvents(alertRepository.getAllEvents(isMulti));
            categories.setTimeFrames(alertRepository.getAllTimeFrames(isMulti));
            categories.setIndicators(alertRepository.getAllIndicators(isMulti));
        }

        return categories;
    }

    private UserSubscription checkUserSubscription(int userId) {
        var subscriptionType = subscriptionService.getSubscriptionTypeByName(SubscriptionName.COURSESTOOLS);
        UserSubscription userSub = userSubscriptionService
                .getCurrentUserSubBySubTypeId(userId, subscriptionType.getId())
                .orElseThrow(() -> new ConflictException("You must have a subscription to use this feature"));
        User user = userDataService.getUserById(userId);
        if (user.getSocial().getTelegramId() == null) {
            throw new ConflictException("You must have a Telegram account to use this feature");
        }
        return userSub;
    }


}
