package org.workfitai.authservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceApplicationTests {

    @Test
    void applicationClassExists() {
        // Verifies the entry-point class is loadable without Spring Boot context
        new AuthServiceApplication();
    }
}
