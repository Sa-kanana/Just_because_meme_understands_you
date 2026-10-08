package com.sakana.just_because_meme_understands_you;

import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = "com.sakana.just_because_meme_understands_you.modules", annotationClass = Mapper.class)
@EnableScheduling
public class JustBecauseMemeUnderstandsYouApplication {

    public static void main(String[] args) {
        SpringApplication.run(JustBecauseMemeUnderstandsYouApplication.class, args);
    }

}
