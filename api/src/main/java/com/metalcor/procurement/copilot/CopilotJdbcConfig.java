package com.metalcor.procurement.copilot;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Separate, small connection pool for the copilot and technical mode, logging in as
 * metalcor_readonly (see V10__roles_and_privileges.sql) — never the API's own metalcor_app
 * datasource. Kept out of the app's default DataSource/JdbcClient beans (ReadonlyJdbcClient is a
 * distinct type) so it cannot be picked up by mistake, and stays under that role's
 * CONNECTION LIMIT 5.
 */
@Configuration
public class CopilotJdbcConfig {

    @Bean(destroyMethod = "close")
    public ReadonlyJdbcClient readonlyJdbcClient(
            @Value("${copilot.datasource.url}") String url,
            @Value("${copilot.datasource.username}") String username,
            @Value("${copilot.datasource.password}") String password,
            @Value("${copilot.datasource.max-pool-size:3}") int maxPoolSize) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setPoolName("copilot-readonly");
        return new ReadonlyJdbcClient(JdbcClient.create(dataSource), dataSource);
    }
}