package com.mustafaqasimov.ordertracker.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Login request DTO")
public class LoginRequest {

    @Schema(description = "The email of the user", example = "mustafa@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    String email;

    @Schema(description = "Password", example = "1234567")
    @NotBlank(message = "Password is required")
    String password;
}
