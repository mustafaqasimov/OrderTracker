package com.mustafaqasimov.ordertracker.service;

import com.mustafaqasimov.ordertracker.dto.request.ChangePasswordRequest;
import com.mustafaqasimov.ordertracker.dto.response.UserProfileResponse;
import com.mustafaqasimov.ordertracker.entity.User;
import com.mustafaqasimov.ordertracker.enums.ActiveStatus;
import com.mustafaqasimov.ordertracker.exception.error.InvalidCredentialsException;
import com.mustafaqasimov.ordertracker.exception.error.ResourceNotFoundException;
import com.mustafaqasimov.ordertracker.mapper.UserMapper;
import com.mustafaqasimov.ordertracker.repository.UserRepository;
import com.mustafaqasimov.ordertracker.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;

    @InjectMocks UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = TestFixtures.user(7L, "user@test.local");
    }

    @Test
    @DisplayName("getMyProfile maps the user it found")
    void getMyProfile() {
        UserProfileResponse expected = UserProfileResponse.builder().build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userMapper.toProfileResponse(user)).thenReturn(expected);

        assertThat(userService.getMyProfile(7L)).isSameAs(expected);
    }

    @Test
    @DisplayName("getMyProfile fails for an unknown id")
    void getMyProfileUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("changePassword stores the new hash")
    void changePassword() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newPassword1")).thenReturn("new-hash");

        userService.changePassword(7L, new ChangePasswordRequest("old", "newPassword1", "newPassword1"));

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changePassword refuses a mismatched confirmation before touching the database")
    void changePasswordMismatch() {
        assertThatThrownBy(() -> userService.changePassword(7L,
                new ChangePasswordRequest("old", "newPassword1", "different")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("do not match");

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword refuses a wrong current password")
    void changePasswordWrongOldPassword() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(7L,
                new ChangePasswordRequest("wrong", "newPassword1", "newPassword1")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Old password");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAllUsers maps every row")
    void getAllUsers() {
        User second = TestFixtures.user(8L, "second@test.local");
        when(userRepository.findAll()).thenReturn(List.of(user, second));
        when(userMapper.toProfileResponse(any())).thenReturn(UserProfileResponse.builder().build());

        assertThat(userService.getAllUsers()).hasSize(2);
        verify(userMapper).toProfileResponse(user);
        verify(userMapper).toProfileResponse(second);
    }

    @Test
    @DisplayName("toggleUserActiveStatus flips ACTIVE to INACTIVE and back")
    void toggleActiveStatus() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        userService.toggleUserActiveStatus(7L);
        assertThat(user.getActive()).isEqualTo(ActiveStatus.INACTIVE);

        userService.toggleUserActiveStatus(7L);
        assertThat(user.getActive()).isEqualTo(ActiveStatus.ACTIVE);

        verify(userRepository, org.mockito.Mockito.times(2)).save(user);
    }

    @Test
    @DisplayName("toggling an unknown user fails")
    void toggleUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.toggleUserActiveStatus(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
