package com.mae134.equipmentinspection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

class SqlInitializationConfigurationTest {

  @Test
  void defaultProfileUsesBootDefaultDataLocations() {
    var environment = loadConfiguration();

    assertThat(environment.getActiveProfiles()).isEmpty();
    assertThat(environment.getDefaultProfiles()).containsExactly("default");
    assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("always");
    assertThat(environment.getProperty("spring.sql.init.data-locations")).isNull();
  }

  @Test
  void devProfileLoadsSharedDataBeforeDevelopmentUsers() {
    var environment = loadConfiguration("dev");

    assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("always");
    assertThat(environment.getProperty("spring.sql.init.data-locations"))
        .isEqualTo("classpath:data.sql,classpath:data-dev.sql");
  }

  private MockEnvironment loadConfiguration(String... activeProfiles) {
    // Read main configuration without the application.properties on the test classpath.
    var mainResources =
        EquipmentInspectionSystemApplication.class.getProtectionDomain().getCodeSource().getLocation();
    var environment = new MockEnvironment();
    environment.setProperty("spring.config.location", mainResources.toExternalForm());
    environment.setActiveProfiles(activeProfiles);
    ConfigDataEnvironmentPostProcessor.applyTo(environment);
    return environment;
  }
}
