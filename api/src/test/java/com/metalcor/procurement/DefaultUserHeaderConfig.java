package com.metalcor.procurement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Every /api/v1 request now needs X-User-Id, reads included. Read-only tests do not care who is
 * asking, so MockMvc sends the manager (id 14) by default; a header set explicitly on a request wins.
 */
@TestConfiguration
public class DefaultUserHeaderConfig {

    @Bean
    MockMvcBuilderCustomizer defaultUserHeader() {
        return builder -> builder.defaultRequest(get("/").header("X-User-Id", "14"));
    }
}
