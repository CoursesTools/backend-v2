package com.winworld.coursestools.service;

import com.winworld.coursestools.config.props.PartnershipProps;
import com.winworld.coursestools.dto.code.CodeReadDto;
import com.winworld.coursestools.entity.Code;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.enums.DiscountType;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.mapper.CodeMapper;
import com.winworld.coursestools.repository.CodeRepository;
import com.winworld.coursestools.service.user.UserDataService;
import com.winworld.coursestools.util.StringGeneratorUtil;
import com.winworld.coursestools.validation.validator.CodeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeService {
    private final CodeRepository codeRepository;
    private final StringGeneratorUtil stringGeneratorUtil;
    private final PartnershipProps partnershipProps;
    private final UserDataService userDataService;
    private final CodeValidator codeValidator;
    private final CodeMapper codeMapper;
    private final ReferralService referralService;
    private final PartnershipService partnershipService;

    public void createPartnerCode(User user) {
        Code code = Code.builder()
                .code(stringGeneratorUtil.generatePartnerCode())
                .owner(user)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(partnershipProps.getDiscount())
                .build();
        codeRepository.save(code);
    }

    public boolean existsByUserIdAndPromoCodeId(int userId, int codeId) {
        return codeRepository.existsUsageCodeByUser(userId, codeId);
    }

    @Transactional
    public void useCode(int userId, Code code) {
        User user = userDataService.getUserById(userId);
        checkCode(userId, code.getCode());
        if (code.isPartnershipCode()) {
            if (!user.hasReferrer()) {
                referralService.registerReferral(code.getOwner(), user, true);
            }
            partnershipService.recalculateLevelAfterNewReferral(code.getOwner());
        }
        codeRepository.useCode(code.getId(), userId);
    }

    public CodeReadDto checkCode(int userId, String codeValue) {
        User user = userDataService.getUserById(userId);
        Code code = getCodeByValue(codeValue);
        if (existsByUserIdAndPromoCodeId(user.getId(), code.getId())) {
            throw new ConflictException("Promo code already used");
        }
        codeValidator.validateCodeEligibility(code, user);
        return codeMapper.toDto(code);
    }

    public boolean existsByCode(String code) {
        return codeRepository.existsByCode(code);
    }

    public Code getCodeByValue(String code) {
        return codeRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Promo code not found"));
    }
}
