package zw.ac.uz.emhare.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @author Tinashe K */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
public class NotificationsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationsServiceApplication.class, args);
    }
}
