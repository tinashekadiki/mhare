package zw.ac.uz.emhare.documentsreporting.document;

import java.awt.Color;
import java.io.InputStream;
import java.util.List;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignImage;
import net.sf.jasperreports.engine.design.JRDesignLine;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ScaleImageEnum;
import net.sf.jasperreports.engine.type.SplitTypeEnum;
import net.sf.jasperreports.engine.type.TextAdjustEnum;

/** Faithful JasperReports reproduction of the legacy UZ CakePHP offer letter. @author Tinashe K */
final class UzOfferLetterJasperTemplate {
    private static final Color LEGACY_BLUE = new Color(0, 148, 251);
    private static final Color BLACK = Color.BLACK;
    private static final Color MUTED = new Color(95, 95, 95);
    private static final int CONTENT_WIDTH = 539;

    private UzOfferLetterJasperTemplate() { }

    static JasperReport compile() {
        try {
            JasperDesign design = new JasperDesign();
            design.setName("UZ_OFFER_LETTER_LEGACY_EQUIVALENT_2026_02");
            design.setPageWidth(595);
            design.setPageHeight(842);
            design.setTopMargin(18);
            design.setBottomMargin(18);
            design.setLeftMargin(28);
            design.setRightMargin(28);
            design.setColumnWidth(CONTENT_WIDTH);
            stringParameters().forEach(name -> addParameter(design, name, String.class));
            addParameter(design, "LOGO", InputStream.class);
            addParameter(design, "SIGNATURE_IMAGE", InputStream.class);
            design.setTitle(page());
            return JasperCompileManager.compileReport(design);
        } catch (JRException exception) {
            throw new IllegalStateException("The governed offer-letter Jasper template is invalid.", exception);
        }
    }

    private static List<String> stringParameters() {
        return List.of("INSTITUTION_ADDRESS", "INSTITUTION_TITLE", "ISSUE_DATE", "APPLICANT_ADDRESS",
                "SALUTATION", "ADMISSION_HEADING", "OPENING_PARAGRAPH", "REGISTRATION_INSTRUCTION",
                "FEE_INTRODUCTION", "FEE_ITEMS_1", "FEE_ITEMS_2", "FEE_ITEMS_3", "FEE_AMOUNTS_1",
                "FEE_AMOUNTS_2", "FEE_AMOUNTS_3", "FEE_TOTAL", "PAYMENT_INSTRUCTION",
                "COMMENCEMENT_INSTRUCTION", "EQUALITY_TERM", "AMENDMENT_TERM", "REGISTRATION_TERM",
                "BLENDED_LEARNING_TERM", "CONGRATULATIONS", "SIGNATORY", "ACCEPTANCE_INSTRUCTION",
                "ACCEPTANCE_DECLARATION", "DOCUMENT_NUMBER");
    }

    private static void addParameter(JasperDesign design, String name, Class<?> type) {
        try {
            JRDesignParameter parameter = new JRDesignParameter();
            parameter.setName(name);
            parameter.setValueClass(type);
            design.addParameter(parameter);
        } catch (JRException exception) {
            throw new IllegalStateException("Duplicate offer-letter parameter " + name + ".", exception);
        }
    }

    private static JRDesignBand page() {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(806);
        band.setSplitType(SplitTypeEnum.PREVENT);

        band.addElement(line(0, 0, CONTENT_WIDTH));
        band.addElement(parameter("INSTITUTION_ADDRESS", 8.2f, 0, 5, 190, 62, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(logo(241, 4, 57, 62));
        band.addElement(line(0, 72, CONTENT_WIDTH));
        band.addElement(parameter("INSTITUTION_TITLE", 8.2f, 300, 74, 239, 13, BLACK, true,
                HorizontalTextAlignEnum.RIGHT));
        band.addElement(line(0, 90, CONTENT_WIDTH));

        band.addElement(parameter("ISSUE_DATE", 8.2f, 350, 98, 189, 14, BLACK, false,
                HorizontalTextAlignEnum.RIGHT));
        band.addElement(parameter("APPLICANT_ADDRESS", 8.2f, 0, 116, 320, 38, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("SALUTATION", 8.2f, 0, 158, CONTENT_WIDTH, 15, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("ADMISSION_HEADING", 8.5f, 0, 180, CONTENT_WIDTH, 28, LEGACY_BLUE, true,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(line(0, 210, CONTENT_WIDTH));

        band.addElement(parameter("OPENING_PARAGRAPH", 8.0f, 0, 217, CONTENT_WIDTH, 26, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("REGISTRATION_INSTRUCTION", 7.7f, 0, 247, CONTENT_WIDTH, 48, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("FEE_INTRODUCTION", 7.7f, 0, 299, CONTENT_WIDTH, 34, BLACK, false,
                HorizontalTextAlignEnum.LEFT));

        addFeeColumn(band, 0, "FEE_ITEMS_1", "FEE_AMOUNTS_1");
        addFeeColumn(band, 180, "FEE_ITEMS_2", "FEE_AMOUNTS_2");
        addFeeColumn(band, 360, "FEE_ITEMS_3", "FEE_AMOUNTS_3");
        band.addElement(parameter("FEE_TOTAL", 7.7f, 0, 386, CONTENT_WIDTH, 14, LEGACY_BLUE, true,
                HorizontalTextAlignEnum.LEFT));

        band.addElement(parameter("PAYMENT_INSTRUCTION", 7.5f, 0, 406, CONTENT_WIDTH, 38, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("COMMENCEMENT_INSTRUCTION", 7.7f, 0, 449, CONTENT_WIDTH, 18, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("EQUALITY_TERM", 7.5f, 0, 472, CONTENT_WIDTH, 38, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("AMENDMENT_TERM", 7.5f, 0, 515, CONTENT_WIDTH, 34, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("REGISTRATION_TERM", 7.5f, 0, 554, CONTENT_WIDTH, 38, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("BLENDED_LEARNING_TERM", 7.5f, 0, 597, CONTENT_WIDTH, 24, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("CONGRATULATIONS", 7.5f, 0, 626, CONTENT_WIDTH, 20, BLACK, false,
                HorizontalTextAlignEnum.LEFT));

        band.addElement(staticText("Yours sincerely", 7.7f, 0, 651, 160, 14, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(image("SIGNATURE_IMAGE", 0, 665, 150, 28));
        band.addElement(parameter("SIGNATORY", 7.7f, 0, 695, 260, 28, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("ACCEPTANCE_INSTRUCTION", 7.6f, 0, 727, CONTENT_WIDTH, 27, LEGACY_BLUE, true,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("ACCEPTANCE_DECLARATION", 7.6f, 0, 757, CONTENT_WIDTH, 29, LEGACY_BLUE, true,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter("DOCUMENT_NUMBER", 6.5f, 0, 795, 410, 10, MUTED, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(staticText("1 / 1", 6.5f, 470, 795, 69, 10, MUTED, false,
                HorizontalTextAlignEnum.RIGHT));
        return band;
    }

    private static void addFeeColumn(JRDesignBand band, int x, String items, String amounts) {
        band.addElement(staticText("Cost Item", 7.6f, x, 337, 106, 13, BLACK, true,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(staticText("Amount", 7.6f, x + 111, 337, 54, 13, BLACK, true,
                HorizontalTextAlignEnum.RIGHT));
        band.addElement(parameter(items, 7.3f, x, 352, 106, 32, BLACK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameter(amounts, 7.3f, x + 111, 352, 54, 32, BLACK, false,
                HorizontalTextAlignEnum.RIGHT));
    }

    private static JRDesignLine line(int x, int y, int width) {
        JRDesignLine line = new JRDesignLine();
        line.setX(x);
        line.setY(y);
        line.setWidth(width);
        line.setHeight(1);
        line.getLinePen().setLineWidth(0.8f);
        return line;
    }

    private static JRDesignImage logo(int x, int y, int width, int height) {
        return image("LOGO", x, y, width, height);
    }

    private static JRDesignImage image(String parameterName, int x, int y, int width, int height) {
        JRDesignImage image = new JRDesignImage(null);
        image.setExpression(new JRDesignExpression("$P{" + parameterName + "}"));
        image.setX(x);
        image.setY(y);
        image.setWidth(width);
        image.setHeight(height);
        image.setScaleImage(ScaleImageEnum.RETAIN_SHAPE);
        return image;
    }

    private static JRDesignStaticText staticText(String text, float size, int x, int y, int width, int height,
            Color color, boolean bold, HorizontalTextAlignEnum alignment) {
        JRDesignStaticText element = new JRDesignStaticText();
        element.setText(text);
        element.setX(x);
        element.setY(y);
        element.setWidth(width);
        element.setHeight(height);
        element.setHorizontalTextAlign(alignment);
        style(element, size, color, bold);
        return element;
    }

    private static JRDesignTextField parameter(String name, float size, int x, int y, int width, int height,
            Color color, boolean bold, HorizontalTextAlignEnum alignment) {
        JRDesignTextField element = new JRDesignTextField();
        element.setExpression(new JRDesignExpression("$P{" + name + "}"));
        element.setX(x);
        element.setY(y);
        element.setWidth(width);
        element.setHeight(height);
        element.setBlankWhenNull(true);
        element.setTextAdjust(TextAdjustEnum.SCALE_FONT);
        element.setHorizontalTextAlign(alignment);
        style(element, size, color, bold);
        return element;
    }

    private static void style(net.sf.jasperreports.engine.design.JRDesignTextElement element,
            float size, Color color, boolean bold) {
        element.setFontName("Helvetica");
        element.setFontSize(size);
        element.setForecolor(color);
        element.setBold(bold);
        element.setPdfFontName("Helvetica");
        element.setPdfEncoding("Cp1252");
        element.setPdfEmbedded(false);
    }
}
