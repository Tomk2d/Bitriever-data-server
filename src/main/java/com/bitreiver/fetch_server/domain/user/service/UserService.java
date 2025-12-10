package com.bitreiver.fetch_server.domain.user.service;

import com.bitreiver.fetch_server.domain.user.dto.AuthResponse;
import com.bitreiver.fetch_server.domain.user.dto.UserLoginRequest;
import com.bitreiver.fetch_server.domain.user.dto.UserResponse;
import com.bitreiver.fetch_server.domain.user.dto.UserSignUpRequest;
import com.bitreiver.fetch_server.domain.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserService {
    UserResponse signup(UserSignUpRequest request);
    AuthResponse login(UserLoginRequest request);
    boolean checkEmailDuplicate(String email);
    boolean checkNicknameDuplicate(String nickname);
    Optional<User> getUser(UUID userId);
    void updateUserTradingHistoryUpdatedAt(UUID userId);
}
