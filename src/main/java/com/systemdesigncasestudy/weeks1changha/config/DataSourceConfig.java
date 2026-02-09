package com.systemdesigncasestudy.weeks1changha.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("mysql")
public class DataSourceConfig {

    @Bean(name = "primaryDataSource")
    @Primary
    public DataSource primaryDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .build();
    }

    @Bean(name = "hotDataSource")
    public DataSource hotDataSource(
            @Value("${app.datasource.hot.jdbc-url}") String url,
            @Value("${app.datasource.hot.username}") String username,
            @Value("${app.datasource.hot.password}") String password,
            @Value("${app.datasource.hot.driver-class-name}") String driverClassName) {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .build();
    }

    @Bean
    @Primary
    @Qualifier("primaryJdbcTemplate")
    public JdbcTemplate primaryJdbcTemplate(@Qualifier("primaryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Qualifier("hotJdbcTemplate")
    public JdbcTemplate hotJdbcTemplate(@Qualifier("hotDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
