package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded local OCR queue worker. @author Tinashe K */
@Component
public class DocumentOcrWorker {
  private final DocumentOcrProcessor processor;

  public DocumentOcrWorker(DocumentOcrProcessor processor) {
    this.processor = processor;
  }

  @Scheduled(fixedDelayString = "${emhare.documents.ocr.processing-interval-ms:1500}")
  public void processQueuedExtraction() {
    processor.processNext();
  }
}
