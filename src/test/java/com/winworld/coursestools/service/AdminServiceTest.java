package com.winworld.coursestools.service;

import com.winworld.coursestools.dto.admin.DirectAccessRequestDto;
import com.winworld.coursestools.dto.admin.DirectAccessSubmissionDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.TradingViewDeliveryStatus;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.repository.user.UserTransactionRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.service.user.UserSubscriptionService;
import com.winworld.coursestools.service.user.UserTransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock
    private UserTransactionService userTransactionService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserDataService userDataService;
    @Mock
    private UserSubscriptionService userSubscriptionService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserTransactionRepository userTransactionRepository;
    @InjectMocks
    private AdminService adminService;

    @Test
    void directExtendResolvesTradingViewNameThroughCaseInsensitiveUserLookup() {
        DirectAccessRequestDto request = new DirectAccessRequestDto();
        request.setTradingViewName("ArYaNsN484");
        request.setExpiredAt(LocalDate.of(2026, 9, 19));
        User user = new User();
        user.setId(7);
        DirectAccessSubmissionDto expected = DirectAccessSubmissionDto.builder()
                .subscriptionId(4968)
                .tradingViewName("aryansn484")
                .expiration(request.getExpiredAt().atStartOfDay())
                .deliveryStatus(TradingViewDeliveryStatus.DELIVERED)
                .build();
        when(userDataService.getUserByTradingViewName("ArYaNsN484")).thenReturn(user);
        when(subscriptionService.directExtendTradingViewAccess(user, request.getExpiredAt(), 1))
                .thenReturn(expected);

        assertThat(adminService.directExtendAccess(request, 1)).isSameAs(expected);
        verify(userDataService).getUserByTradingViewName("ArYaNsN484");
    }
}
