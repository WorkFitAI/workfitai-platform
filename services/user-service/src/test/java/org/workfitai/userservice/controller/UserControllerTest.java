package org.workfitai.userservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.service.UserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock UserService userService;

    @InjectMocks
    UserController controller;

    private UserBaseResponse makeUser(String username) {
        UserBaseResponse u = new UserBaseResponse();
        u.setUsername(username);
        return u;
    }

    // ---- getByEmail ----

    @Test
    void getByEmail_returnsUser() {
        UserBaseResponse user = makeUser("alice");
        when(userService.getByEmail("alice@test.com")).thenReturn(user);

        ResponseEntity<ResponseData<UserBaseResponse>> resp =
                controller.getByEmail("alice@test.com");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getUsername()).isEqualTo("alice");
    }

    // ---- getByUsername ----

    @Test
    void getByUsername_returnsUser() {
        UserBaseResponse user = makeUser("bob");
        when(userService.getByUsername("bob")).thenReturn(user);

        ResponseEntity<ResponseData<UserBaseResponse>> resp = controller.getByUsername("bob");

        assertThat(resp.getBody().getData().getUsername()).isEqualTo("bob");
    }

    // ---- getUsersByUsernames ----

    @Test
    void getByUsernames_returnsList() {
        List<String> names = List.of("alice", "bob");
        when(userService.getUsersByUsernames(names)).thenReturn(
                List.of(makeUser("alice"), makeUser("bob")));

        ResponseEntity<ResponseData<List<UserBaseResponse>>> resp =
                controller.getByUsernames(names);

        assertThat(resp.getBody().getData()).hasSize(2);
    }

    // ---- getUsersByCompanyId ----

    @Test
    void getByCompanyId_returnsList() {
        String companyId = "company-uuid";
        when(userService.getUsersByCompanyId(companyId)).thenReturn(List.of(makeUser("hr1")));

        ResponseEntity<ResponseData<List<UserBaseResponse>>> resp =
                controller.getByCompanyId(companyId);

        assertThat(resp.getBody().getData()).hasSize(1);
    }

    // ---- existsByEmail ----

    @Test
    void existsByEmail_trueWhenExists() {
        when(userService.existsByEmail("existing@test.com")).thenReturn(true);

        ResponseEntity<Boolean> resp = controller.existsByEmail("existing@test.com");

        assertThat(resp.getBody()).isTrue();
    }

    @Test
    void existsByEmail_falseWhenNotExists() {
        when(userService.existsByEmail("new@test.com")).thenReturn(false);

        ResponseEntity<Boolean> resp = controller.existsByEmail("new@test.com");

        assertThat(resp.getBody()).isFalse();
    }

    // ---- existsByUsername ----

    @Test
    void existsByUsername_delegatesAndReturns() {
        when(userService.existsByUsername("taken")).thenReturn(true);
        when(userService.existsByUsername("free")).thenReturn(false);

        assertThat(controller.existsByUsername("taken").getBody()).isTrue();
        assertThat(controller.existsByUsername("free").getBody()).isFalse();
    }

    // ---- existsByPhoneNumber ----

    @Test
    void existsByPhoneNumber_delegatesAndReturns() {
        when(userService.existsByPhoneNumber("0901234567")).thenReturn(true);

        assertThat(controller.existsByPhoneNumber("0901234567").getBody()).isTrue();
    }

    // ---- checkAndReactivateAccount ----

    @Test
    void checkAndReactivateAccount_returnsTrue_whenReactivated() {
        when(userService.checkAndReactivateAccount("deactivated")).thenReturn(true);

        ResponseEntity<Boolean> resp = controller.checkAndReactivateAccount("deactivated");

        assertThat(resp.getBody()).isTrue();
        verify(userService).checkAndReactivateAccount("deactivated");
    }

    @Test
    void checkAndReactivateAccount_returnsFalse_whenBeyondRetention() {
        when(userService.checkAndReactivateAccount("olduser")).thenReturn(false);

        ResponseEntity<Boolean> resp = controller.checkAndReactivateAccount("olduser");

        assertThat(resp.getBody()).isFalse();
    }
}
