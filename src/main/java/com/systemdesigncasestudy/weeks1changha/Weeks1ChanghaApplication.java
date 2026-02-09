package com.systemdesigncasestudy.weeks1changha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Weeks1ChanghaApplication {

    public static void main(String[] args) {
        SpringApplication.run(Weeks1ChanghaApplication.class, args);
    }

    @RestController
    static class PingController {

        @GetMapping("/api/ping")
        public String ping() {
            return "pong";
        }
    }
}
