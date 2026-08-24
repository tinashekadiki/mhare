package zw.ac.uz.emhare.documentsreporting.upload.ocr;

/** Durable OCR lifecycle exposed to document owners. @author Tinashe K */
public enum DocumentOcrStatus {
  QUEUED,
  PROCESSING,
  COMPLETED,
  FAILED,
  UNSUPPORTED
}
