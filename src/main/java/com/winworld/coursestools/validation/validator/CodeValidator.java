package com.winworld.coursestools.validation.validator;

import com.winworld.coursestools.entity.Code;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.repository.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class CodeValidator {
    private final CodeRepository codeRepository;

    public void validateCodeEligibility(Code code, User user) {
        if (code.getMaxUses() != null && codeRepository.countCodeUsages(code.getId()) >= code.getMaxUses()) {
            throw new ConflictException("Code has reached max uses");
        }
        else if (code.getValidUntil() != null && code.getValidUntil().isBefore(LocalDate.now())) {
            throw new ConflictException("Code is expired");
        }

        if (!code.isPartnershipCode()) {
            return;
        }

        //Checks for partner code
        if (user.hasReferrer()) {
            throw new ConflictException("You already have a referrer");
        }

        User codeOwner = code.getOwner();
        if (user.getId().equals(codeOwner.getId())) {
            throw new ConflictException("Partner code can`t be used by yourself");
        }
        if (codeOwner.getReferred() != null &&
                codeOwner.getReferred().getReferrer().equals(user)) {
            throw new ConflictException("You are already listed as a curator for " +
                    "this user and therefore he cannot become your curator");
        }
    }
}
