package com.netlink.onemep_feature.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI featureServiceOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ONE-MEP Feature Service API")
                .description("Projects and Master Data (tiers, team roles, categories, units).")
                .version("v1"));
  }
}
