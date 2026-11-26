package com.mae134.equipmentinspection.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/login", "/h2-console/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/css/tokens.css", "/css/common.css", "/css/login.css")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/equipment/new")
                    .hasRole("ADMIN")
                    .requestMatchers("/equipment/{equipmentId}/edit")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        "/equipment/*/inspection-items", "/equipment/*/inspection-items/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/equipment/{equipmentId}")
                    .hasAnyRole("ADMIN", "INSPECTOR")
                    .requestMatchers(HttpMethod.POST, "/equipment")
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
}
