package com.mae134.equipmentinspection.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/login", "/h2-console/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/equipment/*/inspections/**")
                    .hasAnyRole("ADMIN", "INSPECTOR")
                    .requestMatchers("/")
                    .hasAnyRole("ADMIN", "INSPECTOR")
                    .anyRequest()
                    .authenticated())
        .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
        .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
        .formLogin(form -> form.loginPage("/login").permitAll())
        .logout(logout -> logout.permitAll());

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
