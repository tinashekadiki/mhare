package zw.ac.uz.emhare.documentsreporting.upload.ocr;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** Scheduled worker delegation coverage. @author Tinashe K */
class DocumentOcrWorkerTest {

  @Test
  void delegatesOneBoundedQueueAttempt() {
    DocumentOcrProcessor processor = mock(DocumentOcrProcessor.class);

    new DocumentOcrWorker(processor).processQueuedExtraction();

    verify(processor).processNext();
  }
}
