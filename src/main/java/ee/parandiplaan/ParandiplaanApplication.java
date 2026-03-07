package ee.parandiplaan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ParandiplaanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParandiplaanApplication.class, args);
    }
}
