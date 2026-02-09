package com.systemdesigncasestudy.weeks1changha.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("mysql")
public class MysqlDataSourceConfig {

    @Bean(destroyMethod = "close")
    public DataSource dataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password,
        @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}") String driverClassName,
        @Value("${spring.datasource.hikari.maximum-pool-size:30}") int maximumPoolSize,
        @Value("${spring.datasource.hikari.minimum-idle:10}") int minimumIdle,
        @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeoutMs,
        @Value("${spring.datasource.hikari.max-lifetime:1800000}") long maxLifetimeMs
    ) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setConnectionTimeout(connectionTimeoutMs);
        dataSource.setMaxLifetime(maxLifetimeMs);
        dataSource.setPoolName("weeks1-mysql-pool");
        return dataSource;
    }
}
