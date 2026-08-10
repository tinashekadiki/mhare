package zw.ac.uz.emhare.documentsreporting.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class DocumentContentInspectorTest {
    private final DocumentContentInspector inspector = new DocumentContentInspector();

    @Test
    void detectsSupportedContentFromBytesInsteadOfClientMimeType() {
        assertEquals("application/pdf", inspector.inspect("%PDF-1.7 evidence".getBytes()).mimeType());
        assertEquals("image/jpeg", inspector.inspect(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01}).mimeType());
        assertEquals("image/png", inspector.inspect(new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0x01
        }).mimeType());
    }

    @Test
    void rejectsExecutablesAndUnknownContent() {
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect("MZ executable".getBytes()));
    }
}
