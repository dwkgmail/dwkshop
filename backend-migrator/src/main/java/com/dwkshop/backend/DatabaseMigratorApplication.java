package com.dwkshop.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DatabaseMigratorApplication {

    public static void main(String[] args) {
        int exitCode = SpringApplication.exit(SpringApplication.run(DatabaseMigratorApplication.class, args));
        System.exit(exitCode);
    }
}
