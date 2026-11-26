package com.mae134.equipmentinspection.user;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRegistrationService {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final Validator validator;

  public UserRegistrationService(
      UserRepository users, PasswordEncoder encoder, Validator validator) {
    this.users = users;
    this.encoder = encoder;
    this.validator = validator;
  }

  @Transactional
  public void register(String name, String email, char[] password, String role) {
    if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 100) {
      throw new InvalidInput("Enter a name of 1 to 100 characters.");
    }
    String address = email == null ? "" : email.strip();
    if (address.isBlank()
        || address.codePointCount(0, address.length()) > 255
        || !validator.validate(new EmailInput(address)).isEmpty()
        || address.indexOf('@') < 1) {
      throw new InvalidInput("Enter a valid email address of at most 255 characters.");
    }
    if (!"ADMIN".equals(role) && !"INSPECTOR".equals(role)) {
      throw new InvalidInput("Select ADMIN or INSPECTOR.");
    }
    validatePassword(password);

    try {
      if (users.existsByEmail(address)) {
        throw new IllegalStateException("A user with that email already exists.");
      }

      User user = new User();
      user.setName(name);
      user.setEmail(address);
      user.setRole(role);
      user.setActive(true);
      user.setPasswordHash(encoder.encode(CharBuffer.wrap(password)));
      users.saveAndFlush(user);
    } catch (DataAccessException ex) {
      throw new IllegalStateException("Unable to persist the user registration.");
    }
  }

  private static void validatePassword(char[] password) {
    if (password == null
        || Character.codePointCount(password, 0, password.length) < 12
        || CharBuffer.wrap(password).codePoints().allMatch(Character::isWhitespace)) {
      throw new InvalidInput("Enter a password of at least 12 characters.");
    }
    var encoded = ByteBuffer.allocate(72);
    try {
      var encoder = StandardCharsets.UTF_8.newEncoder();
      var result = encoder.encode(CharBuffer.wrap(password), encoded, true);
      if (result.isError()) {
        throw new InvalidInput("Enter a password with valid Unicode characters.");
      }
      if (result.isOverflow()) {
        throw new InvalidInput("The password must use at most 72 UTF-8 bytes.");
      }
    } finally {
      Arrays.fill(encoded.array(), (byte) 0);
    }
  }

  private record EmailInput(@Email String address) {}

  static final class InvalidInput extends IllegalArgumentException {
    private InvalidInput(String message) {
      super(message);
    }
  }
}
