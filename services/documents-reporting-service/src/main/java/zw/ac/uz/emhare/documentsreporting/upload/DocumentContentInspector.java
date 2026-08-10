package zw.ac.uz.emhare.documentsreporting.upload;

import java.util.Arrays;
import org.springframework.stereotype.Component;

/** @author Tinashe K */
@Component
public class DocumentContentInspector {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};

    public InspectedContent inspect(byte[] content) {
        if (startsWith(content, PDF)) return new InspectedContent("application/pdf", "pdf");
        if (startsWith(content, JPEG)) return new InspectedContent("image/jpeg", "jpg");
        if (startsWith(content, PNG)) return new InspectedContent("image/png", "png");
        throw new IllegalArgumentException("Only genuine PDF, JPEG, and PNG documents are accepted.");
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length
                && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
    }

    public record InspectedContent(String mimeType, String extension) {
    }
}
