package com.mustafaqasimov.ordertracker.controller;

import com.mustafaqasimov.ordertracker.dto.request.ChangePasswordRequest;
import com.mustafaqasimov.ordertracker.dto.response.UserProfileResponse;
import com.mustafaqasimov.ordertracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Controller", description = "User management endpoints")
public class UserController {

    private final UserService userService;

    // ================= User Methods =================

    @GetMapping("/my-profile")
    @Operation(summary = "Get current user's profile",
            description = "Retrieve the profile information of the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User profile retrieved successfully")
    })
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile(getCurrentUserId()));
    }

    @PutMapping("/my-profile/password")
    @Operation(summary = "Change user's password", description = "Update the password of the currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password updated successfully")
    })
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        userService.changePassword(getCurrentUserId(), request);
        return ResponseEntity.ok("Password updated!");
    }

    // ================= ADMIN METHODS =================

    @GetMapping("/admin/all")
    @Operation(summary = "Get all users", description = "Retrieve a list of all users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully")
    })
    public ResponseEntity<List<UserProfileResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PatchMapping("/admin/toggle-status/{id}")
    @Operation(summary = "Toggle user status", description = "Update the active status of a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User status updated successfully")
    })
    public ResponseEntity<String> toggleStatus(@PathVariable Long id) {
        userService.toggleUserActiveStatus(id);
        return ResponseEntity.ok("User status updated!");
    }

    private Long getCurrentUserId() {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        return Long.parseLong(userIdStr);
    }
}
