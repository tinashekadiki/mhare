package zw.ac.uz.emhare.studentrecords;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
public class StudentRecordsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentRecordsServiceApplication.class, args);
    }
}
