package com.patient.io;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.patient.io.mapper")
public class PatientIoApplication {
    public static void main(String[] args) {
        SpringApplication.run(PatientIoApplication.class, args);
    }
}
