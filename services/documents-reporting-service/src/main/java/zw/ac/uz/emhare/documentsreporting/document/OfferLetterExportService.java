package zw.ac.uz.emhare.documentsreporting.document;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.OfferLetterExportAuditRepository;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;
import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.OfferLetterExportAudit;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.PublishedOfferLetterProjectionRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedOfferLetterProjection;

/** Deterministic, read-only current published offer-letter export. @author Tinashe K */
@Service
public class OfferLetterExportService {
    private final PublishedOfferLetterProjectionRepository projectionRepository;
    private final OfferLetterExportAuditRepository auditRepository;
    private final S3Client s3Client;
    private final Clock clock;
    public OfferLetterExportService(PublishedOfferLetterProjectionRepository projectionRepository,
            OfferLetterExportAuditRepository auditRepository,S3Client s3Client,Clock clock){
        this.projectionRepository=projectionRepository;this.auditRepository=auditRepository;this.s3Client=s3Client;this.clock=clock;}
    @Transactional(readOnly=true)
    public Preview preview(UUID intakeId,UUID programmeId){return new Preview(current(intakeId,programmeId).size());}
    @Transactional
    public ExportFile export(UUID intakeId,UUID programmeId,String format,UUID actor){
        List<PublishedOfferLetterProjection> projections=current(intakeId,programmeId);
        String normalized="ZIP".equalsIgnoreCase(format)?"ZIP":"MERGED_PDF";
        OfferLetterExportAudit audit=auditRepository.saveAndFlush(new OfferLetterExportAudit(actor,intakeId,programmeId,normalized,projections.size(),clock.instant()));
        byte[] bytes="ZIP".equals(normalized)?zip(projections):merge(projections);
        String checksum=sha256(bytes);audit.complete(checksum,clock.instant());auditRepository.save(audit);
        String extension="ZIP".equals(normalized)?"zip":"pdf";
        return new ExportFile(bytes,"ZIP".equals(normalized)?"application/zip":"application/pdf",
                "offer-letters-"+intakeId+"-"+programmeId+"."+extension,checksum,projections.size());
    }
    private List<PublishedOfferLetterProjection> current(UUID intakeId,UUID programmeId){
        if(intakeId==null||programmeId==null)throw new IllegalArgumentException("Intake and Programme are required.");
        return projectionRepository.findAllByIntakeIdAndProgrammeIdAndCurrentPublicationTrueAndOfferStatusNotOrderByApplicantNameAscApplicationNumberAsc(intakeId,programmeId,"WITHDRAWN");
    }
    private byte[] merge(List<PublishedOfferLetterProjection> projections){
        try{PDFMergerUtility merger=new PDFMergerUtility();ByteArrayOutputStream output=new ByteArrayOutputStream();
            for(var projection:projections)merger.addSource(new RandomAccessReadBuffer(documentBytes(projection.getGeneratedDocument())));
            merger.setDestinationStream(output);merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());return output.toByteArray();
        }catch(Exception exception){throw new IllegalStateException("Published offer PDFs could not be merged.",exception);}
    }
    private byte[] zip(List<PublishedOfferLetterProjection> projections){
        try{ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output)){
            for(var projection:projections){String name=sanitize(projection.getApplicationNumber())+"-"+sanitize(projection.getOfferNumber())+".pdf";
                zip.putNextEntry(new ZipEntry(name));zip.write(documentBytes(projection.getGeneratedDocument()));zip.closeEntry();}}
            return output.toByteArray();}catch(Exception exception){throw new IllegalStateException("Published offer PDFs could not be zipped.",exception);}
    }
    private byte[] documentBytes(GeneratedDocument document){
        if(document.getStatus()!=GeneratedDocument.Status.STORED)throw new IllegalStateException("A published offer document is not stored.");
        byte[] bytes=s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(document.getStorageBucket()).key(document.getStorageKey()).build()).asByteArray();
        if(!sha256(bytes).equalsIgnoreCase(document.getChecksumSha256()))throw new IllegalStateException("A published offer document failed checksum verification.");
        return bytes;
    }
    private String sanitize(String value){String clean=value==null?"offer":value.replaceAll("[^A-Za-z0-9._-]","-").replaceAll("-+","-");return clean.substring(0,Math.min(clean.length(),100));}
    private String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception exception){throw new IllegalStateException("SHA-256 is unavailable.",exception);}}
    public record Preview(int count){}
    public record ExportFile(byte[] bytes,String contentType,String fileName,String checksumSha256,int documentCount){}
}
