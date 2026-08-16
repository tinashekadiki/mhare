package zw.ac.uz.emhare.documentsreporting.document;

import java.io.InputStream;

/** Resolves the immutable registrar signature referenced by an offer snapshot. @author Tinashe K */
@FunctionalInterface
public interface OfferLetterSignatureLoader {
    InputStream load(String documentId);
}
