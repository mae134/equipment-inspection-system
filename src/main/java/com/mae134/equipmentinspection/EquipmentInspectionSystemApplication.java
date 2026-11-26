package com.mae134.equipmentinspection;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.mae134.equipmentinspection.user.UserRegistrationCommand;
import com.mae134.equipmentinspection.user.UserRegistrationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class EquipmentInspectionSystemApplication {

  private static final String REGISTRATION = "user-registration";
  private static final Map<String, String> REGISTRATION_SETTINGS =
      Map.of(
          "spring.main.web-application-type", "none",
          "spring.sql.init.mode", "never",
          "spring.jpa.hibernate.ddl-auto", "none",
          "spring.jpa.show-sql", "false");

  public static void main(String[] args) {
    if (!registrationRequested(args)) {
      SpringApplication application =
          new SpringApplication(EquipmentInspectionSystemApplication.class);
      application.addListeners(new RegistrationEnvironment(false));
      application.run(args);
      return;
    }
    System.exit(runRegistration(args));
  }

  static boolean registrationRequested(String[] args) {
    for (String arg : args) {
      if (arg.equals("--user-registration") || arg.startsWith("--user-registration=")) {
        return true;
      }
    }
    return false;
  }

  static int runRegistration(String[] args) {
    if (args.length != 1
        || !("--user-registration=local".equals(args[0])
            || "--user-registration=aws".equals(args[0]))) {
      System.err.println("Select exactly one registration mode: local or aws.");
      return 2;
    }

    // Config data can fail before Boot has initialized registration logging.
    if (LoggerFactory.getILoggerFactory() instanceof LoggerContext loggers) {
      loggers.reset();
      loggers.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.OFF);
    }
    try (var context = registrationApplication().run()) {
      var command =
          new UserRegistrationCommand(context.getBean(UserRegistrationService.class), System.out);
      return "--user-registration=local".equals(args[0])
          ? command.runLocal()
          : command.runAws(System.getenv("USER_REGISTRATION_PAYLOAD"));
    } catch (InvalidRegistrationConfiguration ex) {
      System.err.println("Conflicting registration startup configuration.");
      return 2;
    } catch (RuntimeException ex) {
      System.err.println("Registration application failed.");
      return 1;
    }
  }

  static SpringApplication registrationApplication() {
    SpringApplication application =
        new SpringApplication(EquipmentInspectionSystemApplication.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setAdditionalProfiles(REGISTRATION);
    application.addListeners(new RegistrationEnvironment(true));
    return application;
  }

  private record RegistrationEnvironment(boolean registration)
      implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    @Override
    public int getOrder() {
      // Inspect the resolved config data before logging and context creation.
      return LoggingApplicationListener.DEFAULT_ORDER - 1;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
      var environment = event.getEnvironment();
      if (!registration) {
        if (environment.acceptsProfiles(Profiles.of(REGISTRATION))) {
          throw new InvalidRegistrationConfiguration();
        }
        return;
      }

      // Hibernate and JDBC failures can contain bound values, including password hashes.
      Map<String, Object> fixed = new LinkedHashMap<>();
      fixed.put("logging.config", "classpath:user-registration-logback.xml");
      fixed.put("debug", "false");
      fixed.put("trace", "false");
      fixed.put("spring.main.banner-mode", "off");
      fixed.put("spring.main.log-startup-info", "false");
      var propertySource = new MapPropertySource("registrationSafety", fixed);
      environment.getPropertySources().addFirst(propertySource);

      for (var entry : REGISTRATION_SETTINGS.entrySet()) {
        if (!entry.getValue().equalsIgnoreCase(environment.getProperty(entry.getKey()))) {
          throw new InvalidRegistrationConfiguration();
        }
      }

      Map<String, String> jpaProperties =
          Binder.get(environment)
              .bind("spring.jpa.properties", Bindable.mapOf(String.class, String.class))
              .orElse(Map.of());
      for (var entry : jpaProperties.entrySet()) {
        String key = entry.getKey();
        if (key.startsWith("hibernate.hbm2ddl.")
            || key.startsWith("jakarta.persistence.schema-generation.")
            || key.startsWith("javax.persistence.schema-generation.")
            || ("hibernate.show_sql".equals(key) && !"false".equalsIgnoreCase(entry.getValue()))) {
          throw new InvalidRegistrationConfiguration();
        }
      }
      if (environment.containsProperty("spring.datasource.hikari.connection-init-sql")) {
        throw new InvalidRegistrationConfiguration();
      }
      fixed.putAll(REGISTRATION_SETTINGS);
      fixed.put("spring.jpa.properties.hibernate.show_sql", "false");
      fixed.put(
          "spring.jpa.properties.jakarta.persistence.schema-generation.database.action", "none");
      fixed.put(
          "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action", "none");
      for (String logger :
          Binder.get(environment)
              .bind("logging.level", Bindable.mapOf(String.class, String.class))
              .orElse(Map.of())
              .keySet()) {
        fixed.put("logging.level." + logger, "OFF");
      }
    }
  }

  private static final class InvalidRegistrationConfiguration extends IllegalArgumentException {
    private InvalidRegistrationConfiguration() {
      super("Select registration explicitly and use compatible startup settings.");
    }
  }
}
