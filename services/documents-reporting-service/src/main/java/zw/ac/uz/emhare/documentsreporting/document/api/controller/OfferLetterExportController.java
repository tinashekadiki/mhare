package zw.ac.uz.emhare.documentsreporting.document.api.controller;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.uz.emhare.common.security.EmhareCurrentUserResolver;
import zw.ac.uz.emhare.documentsreporting.document.OfferLetterExportService;
import zw.ac.uz.emhare.documentsreporting.document.OfferLetterExportService.Preview;

/** Staff preview count and deterministic published offer-letter exports. @author Tinashe K */
@RestController
@RequestMapping("/api/documents/offer-letters")
@PreAuthorize("hasAnyAuthority('ROLE_system-admin','ROLE_admissions-officer')")
public class OfferLetterExportController {
    private final OfferLetterExportService exportService;private final EmhareCurrentUserResolver users;
    public OfferLetterExportController(OfferLetterExportService exportService,EmhareCurrentUserResolver users){this.exportService=exportService;this.users=users;}
    @GetMapping("/preview-count")
    public Preview preview(@RequestParam("intakeId") UUID intakeId,@RequestParam("programmeId") UUID programmeId){return exportService.preview(intakeId,programmeId);}
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication authentication,@RequestParam("intakeId") UUID intakeId,
            @RequestParam("programmeId") UUID programmeId,@RequestParam(value="format",defaultValue="MERGED_PDF") String format){
        UUID actor=users.fromAuthentication(authentication).orElseThrow(()->new IllegalStateException("Authenticated user is required.")).auditUserId();
        var file=exportService.export(intakeId,programmeId,format,actor);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+file.fileName()+"\"")
                .header("X-Content-SHA256",file.checksumSha256()).header("X-Document-Count",Integer.toString(file.documentCount()))
                .header(HttpHeaders.CONTENT_TYPE,file.contentType()).body(file.bytes());
    }
}
