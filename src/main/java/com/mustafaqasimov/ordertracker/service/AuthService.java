package com.mustafaqasimov.ordertracker.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.mustafaqasimov.ordertracker.dto.request.LoginRequest;
import com.mustafaqasimov.ordertracker.dto.request.RegisterRequest;
import com.mustafaqasimov.ordertracker.dto.response.AuthResponse;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.exception.error.InvalidCredentialsException;
import com.mustafaqasimov.ordertracker.exception.error.ResourceAlreadyExistsException;
import com.mustafaqasimov.ordertracker.exception.error.ResourceNotFoundException;
import com.mustafaqasimov.ordertracker.mapper.UserMapper;
import com.mustafaqasimov.ordertracker.repository.UserRepository;
import com.mustafaqasimov.ordertracker.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("Email already registered");
        }

        userRepository.save(
                userMapper.toEntity(request, passwordEncoder.encode(request.getPassword()))
        );
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return userMapper.toAuthResponse(user, accessToken, refreshToken);
    }

    // 3. Token Yeniləmə (Refresh)
    public AuthResponse refreshToken(String refreshToken) {
        try {
            DecodedJWT decoded = jwtService.verifyToken(refreshToken);
            Long userId = Long.parseLong(decoded.getSubject());

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found!"));

            String newAccessToken = jwtService.generateAccessToken(user);
            String newRefreshToken = jwtService.generateRefreshToken(user);

            return userMapper.toAuthResponse(user, newAccessToken, newRefreshToken);
        } catch (Exception e) {
            throw new InvalidCredentialsException("Refresh token is invalid. Please login again!");
        }
    }
}
