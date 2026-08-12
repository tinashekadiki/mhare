package zw.ac.uz.emhare.documentsreporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.service.registry.ImportHttpServices;
import zw.ac.uz.emhare.documentsreporting.integration.http.CoreIdentityHttpService;

@SpringBootApplication(scanBasePackages = "zw.ac.uz.emhare")
@ImportHttpServices(group = "core-identity", types = CoreIdentityHttpService.class)
public class DocumentsReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentsReportingServiceApplication.class, args);
    }
}
