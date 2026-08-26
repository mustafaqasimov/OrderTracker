package com.mustafaqasimov.ordertracker.dto.response;

import com.mustafaqasimov.ordertracker.enums.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@Schema(description = "User profile response DTO")
public class UserProfileResponse {

    @Schema(description = "User ID", example = "1")
    Long id;

    @Schema(description = "User's full name", example = "Mustafa Qasimov")
    String fullName;

    @Schema(description = "User's email address", example = "mustafa.qasimov@example.com")
    String email;

    @Schema(description = "User's role", example = "ROLE_USER")
    String role;

    @Schema(description = "User's active status", example = "ACTIVE")
    ActiveStatus activeStatus;
}
