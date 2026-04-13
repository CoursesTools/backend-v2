package com.winworld.coursestools.service;

import com.winworld.coursestools.config.props.PartnershipProps;
import com.winworld.coursestools.dto.code.CodeReadDto;
import com.winworld.coursestools.entity.Code;
import com.winworld.coursestools.entity.subscription.SubscriptionPlan;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.DiscountType;
import com.winworld.coursestools.enums.SubscriptionTier;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.mapper.CodeMapper;
import com.winworld.coursestools.repository.CodeRepository;
import com.winworld.coursestools.service.payment.impl.StripePaymentService;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.util.StringGeneratorUtil;
import com.winworld.coursestools.validation.validator.CodeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeServiceTest {

    @Mock
    private CodeRepository codeRepository;
    @Mock
    private StringGeneratorUtil stringGeneratorUtil;
    @Mock
    private PartnershipProps partnershipProps;
    @Mock
    private UserDataService userDataService;
    @Mock
    private CodeValidator codeValidator;
    @Mock
    private CodeMapper codeMapper;
    @Mock
    private ReferralService referralService;
    @Mock
    private PartnershipService partnershipService;
    @Mock
    private StripePaymentService stripePaymentService;

    @InjectMocks
    private CodeService codeService;

    private User testUser;
    private final int userId = 1;
    private final BigDecimal partnershipDiscount = new BigDecimal("30");

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(userId);
    }

    @Test
    void createPartnerCode_assignsProTierAndPartnershipDiscount() {
        when(stringGeneratorUtil.generatePartnerCode()).thenReturn("PARTNER123");
        when(partnershipProps.getDiscount()).thenReturn(partnershipDiscount);

        codeService.createPartnerCode(testUser);

        ArgumentCaptor<Code> captor = ArgumentCaptor.forClass(Code.class);
        verify(codeRepository).save(captor.capture());
        Code saved = captor.getValue();

        assertEquals(SubscriptionTier.PRO, saved.getTier(),
                "Partner code must be PRO-tier-scoped so it cannot discount Essentials plans");
        assertTrue(saved.isPartnershipCode(),
                "Code must be owned by the user (partnership code)");
        assertSame(testUser, saved.getOwner());
        assertEquals("PARTNER123", saved.getCode());
        assertEquals(DiscountType.PERCENTAGE, saved.getDiscountType());
        assertEquals(partnershipDiscount, saved.getDiscountValue());
    }

    @Test
    void checkCodeWithPlan_partnerCode_rejectedAgainstEssentialsPlan() {
        Code partnerCode = Code.builder()
                .code("PARTNER123")
                .owner(testUser)
                .tier(SubscriptionTier.PRO)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(partnershipDiscount)
                .build();
        partnerCode.setId(10);

        SubscriptionPlan essentialsPlan = new SubscriptionPlan();
        essentialsPlan.setTier(SubscriptionTier.ESSENTIALS);

        when(userDataService.getUserById(userId)).thenReturn(testUser);
        when(codeRepository.findByCodeIgnoreCase("PARTNER123")).thenReturn(Optional.of(partnerCode));
        when(codeRepository.existsUsageCodeByUser(userId, partnerCode.getId())).thenReturn(false);
        when(codeMapper.toDto(partnerCode)).thenReturn(new CodeReadDto());

        DataValidationException ex = assertThrows(DataValidationException.class,
                () -> codeService.checkCode(userId, "PARTNER123", essentialsPlan));
        assertEquals("This promo code is not valid for ESSENTIALS plans", ex.getMessage());
    }

    @Test
    void checkCodeWithPlan_partnerCode_acceptedAgainstProPlan() {
        Code partnerCode = Code.builder()
                .code("PARTNER123")
                .owner(testUser)
                .tier(SubscriptionTier.PRO)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(partnershipDiscount)
                .build();
        partnerCode.setId(10);

        SubscriptionPlan proPlan = new SubscriptionPlan();
        proPlan.setTier(SubscriptionTier.PRO);

        CodeReadDto dto = new CodeReadDto();
        when(userDataService.getUserById(userId)).thenReturn(testUser);
        when(codeRepository.findByCodeIgnoreCase("PARTNER123")).thenReturn(Optional.of(partnerCode));
        when(codeRepository.existsUsageCodeByUser(userId, partnerCode.getId())).thenReturn(false);
        when(codeMapper.toDto(partnerCode)).thenReturn(dto);

        CodeReadDto result = codeService.checkCode(userId, "PARTNER123", proPlan);
        assertSame(dto, result);
        verify(codeValidator).validateCodeEligibility(eq(partnerCode), any(User.class));
    }
}
