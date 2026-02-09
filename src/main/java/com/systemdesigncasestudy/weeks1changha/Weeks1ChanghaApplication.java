package com.systemdesigncasestudy.weeks1changha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableScheduling
public class Weeks1ChanghaApplication {

    public static void main(String[] args) {
        SpringApplication.run(Weeks1ChanghaApplication.class, args);
    }
}
