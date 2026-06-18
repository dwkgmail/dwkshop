package com.dwkshop.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.dwkshop.backend.domain.repository")
@EnableScheduling
public class AftersaleServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AftersaleServiceApplication.class, args);
    }
}
