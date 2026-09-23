package com.metalcor.procurement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI procurementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Metalcor Procurement API")
                .version("0.4.0")
                .description("Endpoints of a procure-to-pay mini-ERP. Writes require a provisional X-User-Id header. "
                        + "Study project: companies, suppliers and data are fictional. Not SAP."));
    }
}
