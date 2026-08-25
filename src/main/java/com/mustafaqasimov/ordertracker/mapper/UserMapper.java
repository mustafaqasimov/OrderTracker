package com.mustafaqasimov.ordertracker.mapper;

import com.mustafaqasimov.ordertracker.dto.request.RegisterRequest;
import com.mustafaqasimov.ordertracker.dto.response.AuthResponse;
import com.mustafaqasimov.ordertracker.dto.response.UserProfileResponse;
import com.mustafaqasimov.ordertracker.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "ROLE_USER")
    @Mapping(target = "password", source = "hashedPassword")
    User toEntity(RegisterRequest request, String hashedPassword);

    @Mapping(target = "accessToken", source = "accessToken")
    @Mapping(target = "refreshToken", source = "refreshToken")
    AuthResponse toAuthResponse(User user, String accessToken, String refreshToken);

    UserProfileResponse toProfileResponse(User user);
}
