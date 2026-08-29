package com.mae134.equipmentinspection.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI equipmentInspectionOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("設備点検管理システム API")
                .description("設備、点検項目、点検、点検結果を管理するREST API")
                .version("1.0.0"));
  }
}
