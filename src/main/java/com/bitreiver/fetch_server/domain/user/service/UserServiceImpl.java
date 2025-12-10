package com.bitreiver.fetch_server.domain.user.service;

import com.bitreiver.fetch_server.domain.user.dto.AuthResponse;
import com.bitreiver.fetch_server.domain.user.dto.UserLoginRequest;
import com.bitreiver.fetch_server.domain.user.dto.UserResponse;
import com.bitreiver.fetch_server.domain.user.dto.UserSignUpRequest;
import com.bitreiver.fetch_server.domain.user.entity.User;
import com.bitreiver.fetch_server.domain.user.repository.UserRepository;
import com.bitreiver.fetch_server.global.common.exception.CustomException;
import com.bitreiver.fetch_server.global.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public UserResponse signup(UserSignUpRequest request) {
        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
            }
            
            if (userRepository.existsByNickname(request.getNickname())) {
                throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
            }
            
            if (request.getSignupType() == 0 && (request.getPassword() == null || request.getPassword().trim().isEmpty())) {
                throw new CustomException(ErrorCode.BAD_REQUEST, "비밀번호는 필수입니다.");
            }
            
            String passwordHash = null;
            if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
                passwordHash = passwordEncoder.encode(request.getPassword());
            }
            
            User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .nickname(request.getNickname())
                .signupType(request.getSignupType())
                .passwordHash(passwordHash)
                .snsProvider(request.getSnsProvider())
                .snsId(request.getSnsId())
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .isConnectExchange(false)
                .connectedExchanges(null)
                .build();
            
            User savedUser = userRepository.save(user);
            
            return UserResponse.from(savedUser);
        } catch (CustomException e) {
            if (e.getErrorCode() != ErrorCode.DUPLICATE_EMAIL && 
                e.getErrorCode() != ErrorCode.DUPLICATE_NICKNAME) {
                log.error("signup - {}", e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            log.error("signup - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "회원가입 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public AuthResponse login(UserLoginRequest request) {
        try {
            User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "존재하지 않는 이메일입니다."));
            
            if (user.getPasswordHash() == null || 
                !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new CustomException(ErrorCode.WRONG_PASSWORD);
            }
            
            user.updateLastLogin();
            userRepository.save(user);
            
            return AuthResponse.from(user);
        } catch (CustomException e) {
            if (e.getErrorCode() != ErrorCode.USER_NOT_FOUND && 
                e.getErrorCode() != ErrorCode.WRONG_PASSWORD) {
                log.error("login - {}", e.getMessage());
            }
            throw e;
        } catch (Exception e) {
            log.error("login - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "로그인 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public boolean checkEmailDuplicate(String email) {
        try {
            return userRepository.existsByEmail(email);
        } catch (Exception e) {
            log.error("checkEmailDuplicate - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "이메일 중복 확인 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public boolean checkNicknameDuplicate(String nickname) {
        try {
            return userRepository.existsByNickname(nickname);
        } catch (Exception e) {
            log.error("checkNicknameDuplicate - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "닉네임 중복 확인 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public Optional<User> getUser(UUID userId) {
        try {
            return userRepository.findById(userId);
        } catch (Exception e) {
            log.error("getUser - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "사용자 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public void updateUserTradingHistoryUpdatedAt(UUID userId) {
        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            
            user.updateTradingHistorySyncTime();
            userRepository.save(user);
        } catch (CustomException e) {
            log.error("updateUserTradingHistoryUpdatedAt - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("updateUserTradingHistoryUpdatedAt - 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.INTERNAL_ERROR, 
                "사용자 정보 업데이트 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    private java.util.List<String> parseConnectedExchanges(String connectedExchanges) {
        if (connectedExchanges == null || connectedExchanges.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(connectedExchanges, java.util.List.class);
        } catch (Exception e) {
            log.warn("parseConnectedExchanges - connectedExchanges 파싱 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
