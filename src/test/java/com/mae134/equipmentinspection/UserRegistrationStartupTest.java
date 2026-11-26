package com.mae134.equipmentinspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mae134.equipmentinspection.security.SecurityConfig;
import com.mae134.equipmentinspection.user.UserRegistrationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserRegistrationStartupTest {

  private static final String DATABASE = "jdbc:h2:mem:registration-startup";
  private static final String DATASOURCE = "--spring.datasource.url=" + DATABASE;

  @Test
  void registrationStartsWithoutWebServerOrDatabaseInitialization() throws Exception {
    try (var connection = DriverManager.getConnection(DATABASE, "sa", "");
        var context =
            EquipmentInspectionSystemApplication.registrationApplication()
                .run(
                    DATASOURCE,
                    "--spring.sql.init.schema-locations=classpath:missing-test-schema.sql")) {
      assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
      assertThat(context.getBeansOfType(SecurityConfig.class)).isEmpty();
      assertThat(context.getBean(PasswordEncoder.class)).isNotNull();
      assertThat(context.getBean(UserRegistrationService.class)).isNotNull();
      try (var tables = connection.getMetaData().getTables(null, "PUBLIC", "%", null)) {
        assertThat(tables.next()).isFalse();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "--spring.main.web-application-type=servlet",
        "--spring.sql.init.mode=always",
        "--spring.jpa.hibernate.ddl-auto=update",
        "--spring.jpa.show-sql=true",
        "--spring.jpa.properties[hibernate.hbm2ddl.auto]=create",
        "--spring.jpa.properties[jakarta.persistence.schema-generation.database.action]=drop-and-create",
        "--spring.datasource.hikari.connection-init-sql=CREATE TABLE unexpected (id INT)"
      })
  void rejectsConflictingEffectiveConfiguration(String conflictingSetting) {
    assertThatThrownBy(
            () ->
                EquipmentInspectionSystemApplication.registrationApplication()
                    .run(DATASOURCE, conflictingSetting))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Select registration explicitly and use compatible startup settings.");
  }

  @Test
  void rejectsConflictingEnvironmentVariable() {
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("SPRING_SQL_INIT_MODE", "always")));
    var application = EquipmentInspectionSystemApplication.registrationApplication();
    application.setEnvironment(environment);

    assertThatThrownBy(() -> application.run(DATASOURCE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Select registration explicitly and use compatible startup settings.");
  }

  @Test
  void rejectsConflictingProfile(@TempDir Path configDirectory) throws Exception {
    Files.writeString(
        configDirectory.resolve("application-conflict.properties"),
        "spring.jpa.hibernate.ddl-auto=create");
    assertThatThrownBy(
            () ->
                EquipmentInspectionSystemApplication.registrationApplication()
                    .run(
                        DATASOURCE,
                        "--spring.config.additional-location=" + configDirectory.toUri(),
                        "--spring.profiles.active=conflict"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Select registration explicitly and use compatible startup settings.");
  }

  @Test
  void requiresOneExplicitRegistrationMode() {
    assertThat(EquipmentInspectionSystemApplication.registrationRequested(new String[0])).isFalse();
    assertThat(
            EquipmentInspectionSystemApplication.registrationRequested(
                new String[] {"--user-registration=aws"}))
        .isTrue();
    assertThat(
            EquipmentInspectionSystemApplication.runRegistration(
                new String[] {"--user-registration=local", "--user-registration=aws"}))
        .isEqualTo(2);
    assertThat(
            EquipmentInspectionSystemApplication.runRegistration(
                new String[] {"--user-registration=other"}))
        .isEqualTo(2);
    assertThat(
            EquipmentInspectionSystemApplication.runRegistration(
                new String[] {"--user-registration=aws", "--spring.sql.init.mode=always"}))
        .isEqualTo(2);
  }

  @Test
  void registrationProfileAloneIsRejected() {
    assertThatThrownBy(
            () ->
                EquipmentInspectionSystemApplication.main(
                    new String[] {DATASOURCE, "--spring.profiles.active=user-registration"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Select registration explicitly and use compatible startup settings.");
  }
}
