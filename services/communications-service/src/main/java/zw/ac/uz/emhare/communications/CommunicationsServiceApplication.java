package zw.ac.uz.emhare.communications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Communications service entry point. @author Tinashe K */
@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
public class CommunicationsServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(CommunicationsServiceApplication.class, args);
  }
}
