package com.example.sftp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SftpMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SftpMicroserviceApplication.class, args);
    }
}
