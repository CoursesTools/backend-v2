package com.winworld.coursestools.service.user;

import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.entity.user.UserSubscription;
import com.winworld.coursestools.enums.SubscriptionStatus;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.user.UserSubscriptionRepository;
import com.winworld.coursestools.specification.userSubscription.UserSubscriptionSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSubscriptionServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserSubscriptionSpecification userSubscriptionSpecification;

    @InjectMocks
    private UserSubscriptionService userSubscriptionService;

    private final int userId = 1;
    private final int subscriptionTypeId = 2;
    private final int userSubscriptionId = 3;
    private UserSubscription testUserSubscription;
    private User testUser;
    private SubscriptionPlan testPlan;

    @BeforeEach
    void setUp() {
        // Подготовка тестовых данных
        testUser = new User();
        testUser.setId(userId);
        testUser.setEmail("test@example.com");

        testPlan = new SubscriptionPlan();
        testPlan.setId(5);

        testUserSubscription = UserSubscription.builder()
                .id(userSubscriptionId)
                .user(testUser)
                .plan(testPlan)
                .status(SubscriptionStatus.GRANTED)
                .isTrial(false)
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();
    }

    @Test
    void getUserSubBySubType_NotTerminated_ExistingSubscription_ReturnsSubscription() {
        // Arrange
        when(userSubscriptionRepository.getUserSubBySubTypeNotTerminated(subscriptionTypeId, userId))
                .thenReturn(Optional.of(testUserSubscription));

        // Act
        Optional<UserSubscription> result = userSubscriptionService.getUserSubBySubTypeIdNotTerminated(userId, subscriptionTypeId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(testUserSubscription, result.get());
        verify(userSubscriptionRepository).getUserSubBySubTypeNotTerminated(subscriptionTypeId, userId);
    }

    @Test
    void getUserSubBySubType_NotTerminated_NonExistingSubscription_ReturnsEmptyOptional() {
        // Arrange
        when(userSubscriptionRepository.getUserSubBySubTypeNotTerminated(subscriptionTypeId, userId))
                .thenReturn(Optional.empty());

        // Act
        Optional<UserSubscription> result = userSubscriptionService.getUserSubBySubTypeIdNotTerminated(userId, subscriptionTypeId);

        // Assert
        assertFalse(result.isPresent());
        verify(userSubscriptionRepository).getUserSubBySubTypeNotTerminated(subscriptionTypeId, userId);
    }

    @Test
    void getUserSubById_ExistingId_ReturnsSubscription() {
        // Arrange
        when(userSubscriptionRepository.findById(userSubscriptionId))
                .thenReturn(Optional.of(testUserSubscription));

        // Act
        UserSubscription result = userSubscriptionService.getUserSubById(userSubscriptionId);

        // Assert
        assertEquals(testUserSubscription, result);
        verify(userSubscriptionRepository).findById(userSubscriptionId);
    }

    @Test
    void getUserSubById_NonExistingId_ThrowsEntityNotFoundException() {
        // Arrange
        int nonExistentId = 999;
        when(userSubscriptionRepository.findById(nonExistentId))
                .thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> userSubscriptionService.getUserSubById(nonExistentId));

        assertEquals("User subscription not found with id: " + nonExistentId, exception.getMessage());
        verify(userSubscriptionRepository).findById(nonExistentId);
    }

    @Test
    void hasEverHadSubscriptionOfType_UserHadSubscription_ReturnsTrue() {
        // Arrange
        when(userSubscriptionRepository.hasEverHadSubscriptionOfType(subscriptionTypeId, userId))
                .thenReturn(true);

        // Act
        boolean result = userSubscriptionService.hasEverHadSubscriptionOfType(userId, subscriptionTypeId);

        // Assert
        assertTrue(result);
        verify(userSubscriptionRepository).hasEverHadSubscriptionOfType(subscriptionTypeId, userId);
    }

    @Test
    void hasEverHadSubscriptionOfType_UserNeverHadSubscription_ReturnsFalse() {
        // Arrange
        when(userSubscriptionRepository.hasEverHadSubscriptionOfType(subscriptionTypeId, userId))
                .thenReturn(false);

        // Act
        boolean result = userSubscriptionService.hasEverHadSubscriptionOfType(userId, subscriptionTypeId);

        // Assert
        assertFalse(result);
        verify(userSubscriptionRepository).hasEverHadSubscriptionOfType(subscriptionTypeId, userId);
    }

    @Test
    void save_ValidUserSubscription_ReturnsUserSubscription() {
        // Arrange
        UserSubscription subscriptionToSave = UserSubscription.builder()
                .user(testUser)
                .plan(testPlan)
                .status(SubscriptionStatus.PENDING)
                .isTrial(true)
                .expiredAt(LocalDateTime.now().plusDays(14))
                .build();

        when(userSubscriptionRepository.save(subscriptionToSave))
                .thenReturn(subscriptionToSave);

        // Act
        UserSubscription savedSubscription = userSubscriptionService.save(subscriptionToSave);

        // Assert
        assertEquals(subscriptionToSave, savedSubscription);
        verify(userSubscriptionRepository).save(subscriptionToSave);
    }

    @Test
    void findAllExpiredSubscriptionsByStatus_WithGrantedStatus_ReturnsExpiredSubscriptions() {
        // Arrange
        SubscriptionStatus status = SubscriptionStatus.GRANTED;
        List<UserSubscription> expiredSubscriptions = Arrays.asList(
                UserSubscription.builder().id(1).status(status).build(),
                UserSubscription.builder().id(2).status(status).build()
        );

        when(userSubscriptionRepository.findAllWithExpiredSubscriptionsByStatus(status))
                .thenReturn(expiredSubscriptions);

        // Act
        List<UserSubscription> result = userSubscriptionService.findAllExpiredSubscriptionsByStatus(status);

        // Assert
        assertEquals(2, result.size());
        assertEquals(expiredSubscriptions, result);
        verify(userSubscriptionRepository).findAllWithExpiredSubscriptionsByStatus(status);
    }

    @Test
    void findAllWithExpiredTrialSubscription_ReturnsExpiredTrialSubscriptions() {
        // Arrange
        List<UserSubscription> expiredTrialSubscriptions = Arrays.asList(
                UserSubscription.builder().id(1).isTrial(true).status(SubscriptionStatus.GRANTED).build(),
                UserSubscription.builder().id(2).isTrial(true).status(SubscriptionStatus.GRANTED).build()
        );

        when(userSubscriptionRepository.findAllWithExpiredTrialSubscription())
                .thenReturn(expiredTrialSubscriptions);

        // Act
        List<UserSubscription> result = userSubscriptionService.findAllWithExpiredTrialSubscription();

        // Assert
        assertEquals(2, result.size());
        assertEquals(expiredTrialSubscriptions, result);
        verify(userSubscriptionRepository).findAllWithExpiredTrialSubscription();
    }
}
