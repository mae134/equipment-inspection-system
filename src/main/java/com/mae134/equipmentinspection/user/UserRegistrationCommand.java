package com.mae134.equipmentinspection.user;

import java.io.Console;
import java.io.PrintStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class UserRegistrationCommand {

  private static final Set<String> FIELDS = Set.of("name", "email", "role", "password");
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private final UserRegistrationService service;
  private final PrintStream output;

  public UserRegistrationCommand(UserRegistrationService service, PrintStream output) {
    this.service = service;
    this.output = output;
  }

  public int runLocal() {
    Console console = System.console();
    if (console == null) {
      return runLocal(null);
    }
    return runLocal(
        new Terminal() {
          public String readLine(String prompt) {
            return console.readLine("%s", prompt);
          }

          public char[] readPassword(String prompt) {
            return console.readPassword("%s", prompt);
          }
        });
  }

  int runLocal(Terminal terminal) {
    char[] password = null;
    char[] confirmation = null;
    try {
      output.println("Environment: LOCAL");
      if (terminal == null) {
        output.println("Registration requires an interactive console.");
        return 1;
      }
      String name = requireInput(terminal.readLine("Name: "));
      String email = requireInput(terminal.readLine("Email: ")).strip();
      String role =
          switch (requireInput(terminal.readLine("Role (1. ADMIN, 2. INSPECTOR): "))) {
            case "1" -> "ADMIN";
            case "2" -> "INSPECTOR";
            default -> null;
          };
      if (role == null) {
        output.println("Select role 1 or 2.");
        return 2;
      }
      password = terminal.readPassword("Password: ");
      if (password == null) {
        return cancelled();
      }
      confirmation = terminal.readPassword("Confirm password: ");
      if (confirmation == null) {
        return cancelled();
      }
      if (!Arrays.equals(password, confirmation)) {
        output.println("Passwords do not match.");
        return 2;
      }

      output.printf(
          "Environment: LOCAL%nName: %s%nEmail: %s%nRole: %s%n",
          display(name), display(email), role);
      String answer = requireInput(terminal.readLine("Create this user? [y/N]: "));
      if (!"y".equalsIgnoreCase(answer.strip())) {
        return cancelled();
      }
      return register(name, email, password, role);
    } catch (EndOfInput ex) {
      return cancelled();
    } catch (RuntimeException ex) {
      output.println("Registration failed.");
      return 1;
    } finally {
      clear(password);
      clear(confirmation);
    }
  }

  public int runAws(String payload) {
    char[] password = null;
    try {
      if (!fitsPayloadLimit(payload)) {
        return invalidPayload();
      }
      JsonNode data = JSON.readTree(payload);
      if (!data.isObject() || data.size() != FIELDS.size()) {
        return invalidPayload();
      }
      for (String field : FIELDS) {
        if (!data.path(field).isString()) {
          return invalidPayload();
        }
      }
      password = data.get("password").asString().toCharArray();
      return register(
          data.get("name").asString(),
          data.get("email").asString(),
          password,
          data.get("role").asString());
    } catch (RuntimeException ex) {
      return invalidPayload();
    } finally {
      clear(password);
    }
  }

  private int register(String name, String email, char[] password, String role) {
    try {
      service.register(name, email, password, role);
    } catch (UserRegistrationService.InvalidInput ex) {
      output.println(ex.getMessage());
      return 2;
    } catch (RuntimeException ex) {
      output.println("Registration failed. Check database availability and email uniqueness.");
      return 1;
    }
    output.println("SUCCESS: User registered.");
    return 0;
  }

  private static boolean fitsPayloadLimit(String payload) {
    if (payload == null || payload.length() > 4096) {
      return false;
    }
    var bytes = StandardCharsets.UTF_8.encode(CharBuffer.wrap(payload));
    try {
      return bytes.remaining() <= 4096;
    } finally {
      Arrays.fill(bytes.array(), (byte) 0);
    }
  }

  private int invalidPayload() {
    output.println("Invalid registration payload.");
    return 2;
  }

  private int cancelled() {
    output.println("Registration cancelled.");
    return 3;
  }

  private static String requireInput(String value) {
    if (value == null) {
      throw new EndOfInput();
    }
    return value;
  }

  private static String display(String value) {
    return value.replaceAll("[\\p{C}\\p{Zl}\\p{Zp}]", "?");
  }

  private static void clear(char[] value) {
    if (value != null) {
      Arrays.fill(value, '\0');
    }
  }

  interface Terminal {
    String readLine(String prompt);

    char[] readPassword(String prompt);
  }

  private static final class EndOfInput extends RuntimeException {}
}
