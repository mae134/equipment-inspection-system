package com.mae134.equipmentinspection.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:registration-service",
      "spring.sql.init.mode=never",
      "spring.jpa.show-sql=false",
      "logging.level.org.hibernate.orm.jdbc.error=OFF"
    })
@AutoConfigureMockMvc
class UserRegistrationServiceTest {

  private static final String PASSWORD = "  test-only  ";
  private static final String EMAIL = "validation@example.test";

  @Autowired private UserRegistrationService service;
  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;
  @Autowired private PlatformTransactionManager transactions;
  @Autowired private MockMvc mvc;

  @ParameterizedTest
  @ValueSource(strings = {"ADMIN", "INSPECTOR"})
  void commitsActiveUserWithExistingAuthentication(String role) throws Exception {
    String email = role + "@example.test";
    String name = "名".repeat(100);
    service.register(name, "  " + email + "\t", PASSWORD.toCharArray(), role);

    User user = users.findByEmail(email).orElseThrow();
    assertThat(user.getName()).isEqualTo(name);
    assertThat(user.getEmail()).isEqualTo(email);
    assertThat(user.getRole()).isEqualTo(role);
    assertThat(user.getActive()).isTrue();
    assertThat(user.getId()).isNotNull();
    assertThat(user.getCreatedAt()).isNotNull();
    assertThat(user.getUpdatedAt()).isNotNull();
    assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
    assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();

    mvc.perform(formLogin().user(email).password(PASSWORD))
        .andExpect(authenticated().withRoles(role));
  }

  @ParameterizedTest
  @MethodSource("invalidInputs")
  void rejectsInvalidInput(String name, String email, String password, String role) {
    assertThatThrownBy(
            () ->
                service.register(
                    name, email, password == null ? null : password.toCharArray(), role))
        .isInstanceOf(UserRegistrationService.InvalidInput.class)
        .hasMessageNotContaining(PASSWORD);
    assertThat(users.existsByEmail(EMAIL)).isFalse();
  }

  private static Stream<Arguments> invalidInputs() {
    return Stream.of(
        Arguments.of(null, EMAIL, PASSWORD, "ADMIN"),
        Arguments.of("  ", EMAIL, PASSWORD, "ADMIN"),
        Arguments.of("a".repeat(101), EMAIL, PASSWORD, "ADMIN"),
        Arguments.of("Name", null, PASSWORD, "ADMIN"),
        Arguments.of("Name", "  ", PASSWORD, "ADMIN"),
        Arguments.of("Name", "invalid", PASSWORD, "ADMIN"),
        Arguments.of("Name", "a@b@invalid", PASSWORD, "ADMIN"),
        Arguments.of(
            "Name", "a@" + "b".repeat(63) + ("." + "b".repeat(63)).repeat(3), PASSWORD, "ADMIN"),
        Arguments.of("Name", EMAIL, null, "ADMIN"),
        Arguments.of("Name", EMAIL, "", "ADMIN"),
        Arguments.of("Name", EMAIL, " ".repeat(12), "ADMIN"),
        Arguments.of("Name", EMAIL, "a".repeat(11), "ADMIN"),
        Arguments.of("Name", EMAIL, "a".repeat(73), "ADMIN"),
        Arguments.of("Name", EMAIL, "界".repeat(24) + "a", "ADMIN"),
        Arguments.of("Name", EMAIL, "😀".repeat(11), "ADMIN"),
        Arguments.of("Name", EMAIL, "a".repeat(12) + Character.MIN_HIGH_SURROGATE, "ADMIN"),
        Arguments.of("Name", EMAIL, PASSWORD, "MANAGER"),
        Arguments.of("Name", EMAIL, PASSWORD, "admin"),
        Arguments.of("Name", EMAIL, PASSWORD, null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "界", "😀"})
  void acceptsExactly72Utf8Bytes(String character) {
    int repeats = 72 / character.getBytes(StandardCharsets.UTF_8).length;
    String password = character.repeat(repeats);
    String email = repeats + "@example.test";
    service.register("Boundary", email, password.toCharArray(), "ADMIN");
    assertThat(encoder.matches(password, users.findByEmail(email).orElseThrow().getPasswordHash()))
        .isTrue();
  }

  @Test
  void rejectsDuplicateAndPreservesExistingUser() {
    String email = "duplicate@example.test";
    service.register("First", email, PASSWORD.toCharArray(), "ADMIN");
    assertThatThrownBy(() -> service.register("Second", email, PASSWORD.toCharArray(), "INSPECTOR"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(users.findByEmail(email).orElseThrow().getName()).isEqualTo("First");
  }

  @Test
  void databaseConstraintRollsBackEarlierRegistrationInSameTransaction() {
    String email = "rolled-back@example.test";
    var transaction = new TransactionTemplate(transactions);
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      service.register("First", email, PASSWORD.toCharArray(), "ADMIN");
                      User duplicate = new User();
                      duplicate.setName("Duplicate");
                      duplicate.setEmail(email);
                      duplicate.setRole("INSPECTOR");
                      duplicate.setPasswordHash(encoder.encode(PASSWORD));
                      users.saveAndFlush(duplicate);
                    }))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(users.existsByEmail(email)).isFalse();
  }

  @Test
  void concurrentRegistrationsAllowOnlyOneCommit() throws Exception {
    String email = "concurrent@example.test";
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> concurrentRegistration(email, start));
      var second = executor.submit(() -> concurrentRegistration(email, start));
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(users.existsByEmail(email)).isTrue();
  }

  private boolean concurrentRegistration(String email, CountDownLatch start)
      throws InterruptedException {
    start.await();
    try {
      service.register("Concurrent", email, PASSWORD.toCharArray(), "ADMIN");
      return true;
    } catch (IllegalStateException ex) {
      return false;
    }
  }
}
