package zw.ac.uz.emhare.studentrecords.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class StudentReferenceNumberPropertiesTest {

    private final StudentReferenceNumberProperties properties = new StudentReferenceNumberProperties();

    @Test
    void formatsLocalAndForeignStudentNumbersWithTwoDigitYearAndFourDigitSerial() {
        String localPrefix = properties.prefixFor("LOCAL");
        String foreignPrefix = properties.prefixFor("INTERNATIONAL");

        String localNumber = properties.format(localPrefix, 2026, 42);
        String foreignNumber = properties.format(foreignPrefix, 2026, 42);

        assertEquals(8, localNumber.length());
        assertEquals(9, foreignNumber.length());
        assertEquals("R260042", localNumber.substring(0, 7));
        assertEquals("FR260042", foreignNumber.substring(0, 8));
    }

    @Test
    void changesTheSafeCheckLetterWhenTheFourDigitRangeOverflows() {
        String prefix = properties.prefixFor("LOCAL");

        String initial = properties.format(prefix, 2026, 42);
        String overflow = properties.format(prefix, 2026, 10_042);

        assertEquals(initial.substring(0, 7), overflow.substring(0, 7));
        assertNotEquals(initial.substring(7), overflow.substring(7));
    }

    @Test
    void rejectsAConfiguredAlphabetContainingNumberLikeLetters() {
        properties.setCheckLetters("AILO");

        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
    }

    @Test
    void supportsCustomLocalCategoriesPrefixesAndLayout() {
        properties.setLocalCategoryCodes(" local, domestic, ");
        properties.setLocalPrefix("uz-");
        properties.setForeignPrefix("int-");
        properties.setYearDigits(4);
        properties.setSerialDigits(2);
        properties.setFormat("{year}/{prefix}/{serial}/{check}");

        assertEquals("UZ-", properties.prefixFor(" DOMESTIC "));
        assertEquals("INT-", properties.prefixFor("regional"));
        String reference = properties.format("UZ-", 2026, 9);
        assertEquals("2026/UZ-/09/", reference.substring(0, reference.length() - 1));
    }

    @Test
    void rejectsMissingStudentNumberInputsAndPrefixes() {
        assertThrows(IllegalStateException.class, () -> properties.prefixFor(null));
        assertThrows(IllegalStateException.class, () -> properties.prefixFor(" "));

        properties.setLocalCategoryCodes(null);
        assertThrows(IllegalStateException.class, () -> properties.prefixFor("LOCAL"));
        properties.setLocalCategoryCodes("LOCAL");
        properties.setLocalPrefix(" ");
        assertThrows(IllegalStateException.class, () -> properties.prefixFor("LOCAL"));
        properties.setLocalPrefix("R");
        properties.setForeignPrefix(null);
        assertThrows(IllegalStateException.class, () -> properties.prefixFor("FOREIGN"));
    }

    @Test
    void rejectsInvalidYearSerialAndAllocationRanges() {
        properties.setYearDigits(0);
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setYearDigits(5);
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setYearDigits(2);
        properties.setSerialDigits(0);
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setSerialDigits(10);
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setSerialDigits(4);
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, -1));
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 180_000));
    }

    @Test
    void rejectsUnsafeOrInvalidCheckLetterAlphabets() {
        properties.setCheckLetters(null);
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setCheckLetters(" ");
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setCheckLetters("A1");
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setCheckLetters("AAC");
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setCheckLetters("AZ");
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
    }

    @Test
    void rejectsIncompleteStudentNumberFormatsAndBlankFormatPrefixes() {
        for (String placeholder : new String[] {"{prefix}", "{year}", "{serial}", "{check}"}) {
            StudentReferenceNumberProperties incomplete = new StudentReferenceNumberProperties();
            incomplete.setFormat("{prefix}{year}{serial}{check}".replace(placeholder, ""));
            assertThrows(IllegalStateException.class, () -> incomplete.format("R", 2026, 1));
        }

        properties.setFormat(null);
        assertThrows(IllegalStateException.class, () -> properties.format("R", 2026, 1));
        properties.setFormat("{prefix}{year}{serial}{check}");
        assertThrows(IllegalStateException.class, () -> properties.format(" ", 2026, 1));
    }
}
