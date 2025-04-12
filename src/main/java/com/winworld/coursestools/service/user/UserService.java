package com.winworld.coursestools.service.user;

import com.winworld.coursestools.dto.user.UpdateUserEmailDto;
import com.winworld.coursestools.dto.user.UpdateUserPasswordDto;
import com.winworld.coursestools.dto.user.UpdateUserDto;
import com.winworld.coursestools.dto.user.UserReadDto;
import com.winworld.coursestools.entity.user.User;
import com.winworld.coursestools.mapper.UserMapper;
import com.winworld.coursestools.messaging.user.SendEmailCodeMessageBuilder;
import com.winworld.coursestools.service.EmailService;
import com.winworld.coursestools.service.TokenService;
import com.winworld.coursestools.validation.validator.UserValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final UserDataService userDataService;
    private final UserValidator userValidator;
    private final SendEmailCodeMessageBuilder sendEmailCodeMessageBuilder;
    private final EmailService emailService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public UserReadDto getUser(int userId) {
        return userMapper.toDto(userDataService.getUserById(userId));
    }

    @Transactional
    public UserReadDto updateUser(int userId, UpdateUserPasswordDto dto) {
        User user = userDataService.getUserById(userId);
        userValidator.validateUserPasswordUpdate(dto, user);
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        return userMapper.toDto(user);
    }

    @Transactional
    public UserReadDto updateUser(int userId, UpdateUserDto dto) {
        User user = userDataService.getUserById(userId);
        userValidator.validateUserUpdate(dto, user);
        userMapper.updateUserFromDto(dto, user);
        return userMapper.toDto(user);
    }

    @Transactional
    public UserReadDto updateUser(int userId, UpdateUserEmailDto dto) {
        User user = userDataService.getUserById(userId);
        userValidator.validateUserEmailUpdate(dto, user);
        if (dto.getEmailCode() != null) {
            tokenService.deleteEmailToken(userId);
            user.setEmail(dto.getEmail());
        }
        else {
            String emailCode = tokenService.saveAndGetEmailToken(userId);
            emailService.send(user.getEmail(), sendEmailCodeMessageBuilder.buildMessage(emailCode));
        }
        return userMapper.toDto(user);
    }
}
