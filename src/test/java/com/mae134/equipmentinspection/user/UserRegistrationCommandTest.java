package com.mae134.equipmentinspection.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserRegistrationCommandTest {

  private static final String PASSWORD = "test-password-only";
  private static final String EMAIL = "Name@example.test";
  private static final String PAYLOAD =
      """
      {"name":"Test","email":"Name@example.test","role":"INSPECTOR","password":"test-password-only"}
      """;
  private final UserRegistrationService service = mock(UserRegistrationService.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final UserRegistrationCommand command =
      new UserRegistrationCommand(service, new PrintStream(output, true, StandardCharsets.UTF_8));
  private final UserRegistrationCommand.Terminal terminal =
      mock(UserRegistrationCommand.Terminal.class);

  @Test
  void localRegistrationRequiresConfirmationAndClearsBothArrays() {
    var password = PASSWORD.toCharArray();
    var confirmation = PASSWORD.toCharArray();
    prepareLocal("2", password, confirmation, "y");
    var received = capturePassword();

    assertThat(command.runLocal(terminal)).isZero();
    assertThat(received.get()).isSameAs(password);
    assertThat(password).containsOnly('\0');
    assertThat(confirmation).containsOnly('\0');
    assertThat(message()).contains("LOCAL", EMAIL, "INSPECTOR", "SUCCESS").doesNotContain(PASSWORD);
  }

  @Test
  void mismatchedPasswordsAreRejectedAndCleared() {
    var password = PASSWORD.toCharArray();
    var confirmation = "different-password".toCharArray();
    prepareLocal("2", password, confirmation, "y");

    assertThat(command.runLocal(terminal)).isEqualTo(2);
    assertThat(password).containsOnly('\0');
    assertThat(confirmation).containsOnly('\0');
    assertThat(message()).doesNotContain(PASSWORD, "different-password");
    verifyNoInteractions(service);
  }

  @Test
  void invalidRoleIsRejected() {
    prepareLocal("3", null, null, null);
    assertThat(command.runLocal(terminal)).isEqualTo(2);
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"n", "yes"})
  void registrationRequiresExplicitYes(String answer) {
    prepareLocal("2", PASSWORD.toCharArray(), PASSWORD.toCharArray(), answer);
    assertThat(command.runLocal(terminal)).isEqualTo(3);
    verifyNoInteractions(service);
  }

  @Test
  void eofAtNameCancels() {
    assertThat(command.runLocal(terminal)).isEqualTo(3);
    verifyNoInteractions(service);
  }

  @Test
  void eofAtPasswordConfirmationCancelsAndClearsPassword() {
    var password = PASSWORD.toCharArray();
    prepareLocal("2", password, null, null);
    assertThat(command.runLocal(terminal)).isEqualTo(3);
    assertThat(password).containsOnly('\0');
    verifyNoInteractions(service);
  }

  @Test
  void unavailableConsoleFailsSafely() {
    assertThat(command.runLocal(null)).isEqualTo(1);
    verifyNoInteractions(service);
  }

  @Test
  void awsRegistrationProcessesPayloadAndClearsPassword() {
    var received = capturePassword();

    assertThat(command.runAws(PAYLOAD)).isZero();
    assertThat(received.get()).containsOnly('\0');
    assertThat(message()).contains("SUCCESS").doesNotContain(EMAIL, PASSWORD, "INSPECTOR", "Test");
  }

  @ParameterizedTest
  @MethodSource("invalidPayloads")
  void awsRejectsInvalidPayloadWithoutEchoingIt(String payload) {
    assertThat(command.runAws(payload)).isEqualTo(2);
    assertThat(message()).isEqualTo("Invalid registration payload." + System.lineSeparator());
    verifyNoInteractions(service);
  }

  private static Stream<String> invalidPayloads() {
    return Stream.of(
        null,
        "",
        "{" + PASSWORD,
        "[]",
        "null",
        PAYLOAD + "{}",
        PAYLOAD.replace("\"Test\"", "true"),
        PAYLOAD.replace("\"Name@example.test\"", "null"),
        PAYLOAD.replace("\"INSPECTOR\"", "3"),
        PAYLOAD.replace("\"" + PASSWORD + "\"", "[]"),
        PAYLOAD.replace("\"password\"", "\"other\""),
        PAYLOAD.replace("\"role\"", "\"email\""),
        PAYLOAD.replace("\"Test\"", "\"" + "界".repeat(1400) + "\""));
  }

  @Test
  void serviceInputErrorReturnsInvalidInputCode() {
    var realValidation = new UserRegistrationService(null, null, null);
    var realCommand =
        new UserRegistrationCommand(
            realValidation, new PrintStream(output, true, StandardCharsets.UTF_8));
    assertThat(realCommand.runAws(PAYLOAD.replace("\"Test\"", "\"\""))).isEqualTo(2);
    assertThat(message()).doesNotContain(EMAIL, PASSWORD);
  }

  @Test
  void registrationFailureDoesNotExposeUnderlyingException() {
    doThrow(new IllegalStateException(PASSWORD))
        .when(service)
        .register(eq("Test"), eq(EMAIL), any(char[].class), eq("INSPECTOR"));
    assertThat(command.runAws(PAYLOAD)).isEqualTo(1);
    assertThat(message()).doesNotContain(PASSWORD, EMAIL, "SUCCESS");
  }

  private AtomicReference<char[]> capturePassword() {
    var received = new AtomicReference<char[]>();
    doAnswer(
            invocation -> {
              char[] password = invocation.getArgument(2);
              assertThat(password).containsExactly(PASSWORD.toCharArray());
              assertThat(message()).doesNotContain("SUCCESS");
              received.set(password);
              return null;
            })
        .when(service)
        .register(eq("Test"), eq(EMAIL), any(char[].class), eq("INSPECTOR"));
    return received;
  }

  private void prepareLocal(String role, char[] password, char[] confirmation, String answer) {
    when(terminal.readLine("Name: ")).thenReturn("Test");
    when(terminal.readLine("Email: ")).thenReturn(EMAIL);
    when(terminal.readLine("Role (1. ADMIN, 2. INSPECTOR): ")).thenReturn(role);
    when(terminal.readPassword("Password: ")).thenReturn(password);
    when(terminal.readPassword("Confirm password: ")).thenReturn(confirmation);
    when(terminal.readLine("Create this user? [y/N]: ")).thenReturn(answer);
  }

  private String message() {
    return output.toString(StandardCharsets.UTF_8);
  }
}
