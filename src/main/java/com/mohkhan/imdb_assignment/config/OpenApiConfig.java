package com.mohkhan.imdb_assignment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author M_Khandan
 * Date: 5/2/2026
 * Time: 4:50 PM
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI imdbApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IMDb High Performance API")
                        .version("1.0")
                        .description("Spring Boot IMDb assignment API"));
    }
}
