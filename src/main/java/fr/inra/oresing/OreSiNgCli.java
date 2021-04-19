package fr.inra.oresing;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"fr.inra.oresing"})
public class OreSiNgCli implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println(ToStringBuilder.reflectionToString(args));
    }

    public static void main(String[] args) {
        SpringApplication.run(OreSiNgCli.class, args);
    }
}
