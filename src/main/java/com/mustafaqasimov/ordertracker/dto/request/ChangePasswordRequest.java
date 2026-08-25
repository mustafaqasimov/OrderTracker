package com.mustafaqasimov.ordertracker.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@AllArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@Schema(description = "Change password request DTO")
public class ChangePasswordRequest {

    @Schema(description = "Old password", example = "oldPassword123")
    String oldPassword;

    @Schema(description = "New password", example = "newPassword")
    String newPassword;

    @Schema(description = "Confirm new password", example = "newPassword123")
    String confirmNewPassword;
}
