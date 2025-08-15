package org.workfitai.authservice.repository;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.workfitai.authservice.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:5.0.13");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    void whenSaveUser_thenFindByUsernameAndEmail() {
        User u = User.builder()
                .username("alice")
                .email("alice@example.com")
                .password("hashedpwd")
                .roles(Set.of("USER"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(u);

        Optional<User> byUsername = userRepository.findByUsername("alice");
        Optional<User> byEmail    = userRepository.findByEmail("alice@example.com");

        assertThat(byUsername).isPresent();
        assertThat(byUsername.get().getEmail()).isEqualTo("alice@example.com");

        assertThat(byEmail).isPresent();
        assertThat(byEmail.get().getUsername()).isEqualTo("alice");
    }

    @Test
    void existsByUsernameOrEmail_shouldReturnTrue() {
        User u = User.builder()
                .username("bob")
                .email("bob@example.com")
                .password("x")
                .roles(Set.of("USER"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(u);

        assertThat(userRepository.existsByUsername("bob")).isTrue();
        assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
    }
}