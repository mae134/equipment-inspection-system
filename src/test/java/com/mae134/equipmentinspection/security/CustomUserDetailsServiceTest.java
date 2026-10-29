package com.mae134.equipmentinspection.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@SpringBootTest
class CustomUserDetailsServiceTest {

  @Autowired private CustomUserDetailsService customUserDetailsService;

  @Test
  void loadUserByUsernameReturnsInspectorUser() {
    UserDetails user = customUserDetailsService.loadUserByUsername("inspector@example.com");

    assertThat(user.getUsername()).isEqualTo("inspector@example.com");
    assertThat(user.isEnabled()).isTrue();
    assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_INSPECTOR");
  }

  @Test
  void loadUserByUsernameThrowsWhenUserDoesNotExist() {
    assertThrows(
        UsernameNotFoundException.class,
        () -> customUserDetailsService.loadUserByUsername("unknown@example.com"));
  }
}
