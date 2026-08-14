package zw.ac.uz.emhare.documentsreporting.document;

import java.awt.Color;
import java.util.List;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignParameter;
import net.sf.jasperreports.engine.design.JRDesignRectangle;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.PositionTypeEnum;
import net.sf.jasperreports.engine.type.SplitTypeEnum;
import net.sf.jasperreports.engine.type.TextAdjustEnum;

/** JasperReports definition for UZ offer-letter content policy version 2026-01. @author Tinashe K */
final class UzOfferLetterJasperTemplate {
    private static final Color UZ_GREEN = new Color(0, 91, 65);
    private static final Color DARK = new Color(28, 37, 42);
    private static final Color MUTED = new Color(87, 96, 101);
    private static final Color PALE_GREEN = new Color(239, 247, 243);
    private static final int CONTENT_WIDTH = 523;

    private UzOfferLetterJasperTemplate() { }

    static JasperReport compile() {
        try {
            JasperDesign design = new JasperDesign();
            design.setName("UZ_OFFER_LETTER_2026_01");
            design.setPageWidth(595);
            design.setPageHeight(842);
            design.setTopMargin(28);
            design.setBottomMargin(28);
            design.setLeftMargin(36);
            design.setRightMargin(36);
            design.setColumnWidth(CONTENT_WIDTH);
            design.setSummaryNewPage(true);
            parameters().forEach(name -> addParameter(design, name));
            design.setTitle(pageOne());
            design.setSummary(pageTwo());
            return JasperCompileManager.compileReport(design);
        } catch (JRException exception) {
            throw new IllegalStateException("The governed offer-letter Jasper template is invalid.", exception);
        }
    }

    private static List<String> parameters() {
        return List.of("INSTITUTION_NAME", "INSTITUTION_CONTACT", "DOCUMENT_TITLE", "OFFER_REFERENCE", "ISSUE_DATE",
                "APPLICANT_ADDRESS", "SALUTATION", "OPENING_PARAGRAPH", "PROGRAMME_DETAILS", "SCHEDULE_DETAILS",
                "CONDITIONS", "RESPONSE_INSTRUCTION", "SIGNATORY", "DOCUMENT_NUMBER", "REQUIRED_EVIDENCE",
                "FEE_SCHEDULE", "PAYMENT_INSTRUCTION", "STANDARD_TERMS", "POLICY_VERSION");
    }

    private static void addParameter(JasperDesign design, String name) {
        try {
            JRDesignParameter parameter = new JRDesignParameter();
            parameter.setName(name);
            parameter.setValueClass(String.class);
            design.addParameter(parameter);
        } catch (JRException exception) {
            throw new IllegalStateException("Duplicate offer-letter parameter " + name + ".", exception);
        }
    }

    private static JRDesignBand pageOne() {
        JRDesignBand band = band(786);
        band.addElement(rectangle(0, 0, CONTENT_WIDTH, 84, UZ_GREEN));
        band.addElement(parameter("INSTITUTION_NAME", 18, 16, 487, 28, Color.WHITE, true));
        band.addElement(parameter("INSTITUTION_CONTACT", 9, 48, 487, 26, Color.WHITE, false));
        band.addElement(parameter("DOCUMENT_TITLE", 15, 101, CONTENT_WIDTH, 22, UZ_GREEN, true));
        band.addElement(parameter("OFFER_REFERENCE", 9, 130, CONTENT_WIDTH, 16, MUTED, false));
        band.addElement(parameterAt("ISSUE_DATE", 9, 350, 161, 173, 16, MUTED, false,
                HorizontalTextAlignEnum.RIGHT));
        band.addElement(parameter("APPLICANT_ADDRESS", 10, 161, 260, 50, DARK, false));
        band.addElement(parameter("SALUTATION", 10, 224, CONTENT_WIDTH, 18, DARK, false));
        band.addElement(parameter("OPENING_PARAGRAPH", 10, 249, CONTENT_WIDTH, 54, DARK, false));
        band.addElement(rectangle(0, 316, CONTENT_WIDTH, 145, PALE_GREEN));
        band.addElement(staticTextAt("OFFER DETAILS", 9, 16, 329, 140, 16, UZ_GREEN, true,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(parameterAt("PROGRAMME_DETAILS", 10, 16, 352, 487, 98, DARK, false,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(staticText("KEY DATES", 9, 479, 140, 16, UZ_GREEN, true));
        band.addElement(parameter("SCHEDULE_DETAILS", 10, 502, CONTENT_WIDTH, 55, DARK, false));
        band.addElement(parameter("CONDITIONS", 10, 574, CONTENT_WIDTH, 58, DARK, false));
        band.addElement(parameter("RESPONSE_INSTRUCTION", 10, 647, CONTENT_WIDTH, 46, DARK, false));
        band.addElement(parameter("SIGNATORY", 10, 708, 240, 38, DARK, true));
        band.addElement(parameter("DOCUMENT_NUMBER", 8, 758, 350, 14, MUTED, false));
        band.addElement(staticText("Page 1 of 2", 8, 758, CONTENT_WIDTH, 14, MUTED, false, HorizontalTextAlignEnum.RIGHT));
        return band;
    }

    private static JRDesignBand pageTwo() {
        JRDesignBand band = band(786);
        band.setSplitType(SplitTypeEnum.STRETCH);
        band.addElement(rectangle(0, 0, CONTENT_WIDTH, 64, UZ_GREEN));
        band.addElement(staticTextAt("TERMS, FEES AND REGISTRATION", 16, 18, 17, 487, 26, Color.WHITE, true,
                HorizontalTextAlignEnum.LEFT));
        band.addElement(staticText("EVIDENCE TO PRESENT", 10, 85, CONTENT_WIDTH, 18, UZ_GREEN, true));
        band.addElement(stretchingParameter("REQUIRED_EVIDENCE", 10, 111, CONTENT_WIDTH, 80));
        band.addElement(staticText("FEE SCHEDULE", 10, 213, CONTENT_WIDTH, 18, UZ_GREEN, true));
        band.addElement(stretchingParameter("FEE_SCHEDULE", 10, 239, CONTENT_WIDTH, 155));
        band.addElement(staticText("PAYMENT", 10, 380, CONTENT_WIDTH, 18, UZ_GREEN, true));
        band.addElement(stretchingParameter("PAYMENT_INSTRUCTION", 10, 406, CONTENT_WIDTH, 52));
        band.addElement(staticText("STANDARD TERMS", 10, 480, CONTENT_WIDTH, 18, UZ_GREEN, true));
        band.addElement(stretchingParameter("STANDARD_TERMS", 10, 506, CONTENT_WIDTH, 160));
        band.addElement(parameter("POLICY_VERSION", 8, 724, 260, 14, MUTED, false));
        band.addElement(parameter("DOCUMENT_NUMBER", 8, 744, 350, 14, MUTED, false));
        band.addElement(staticText("Page 2 of 2", 8, 744, CONTENT_WIDTH, 14, MUTED, false, HorizontalTextAlignEnum.RIGHT));
        return band;
    }

    private static JRDesignBand band(int height) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(height);
        band.setSplitType(SplitTypeEnum.PREVENT);
        return band;
    }

    private static JRDesignRectangle rectangle(int x, int y, int width, int height, Color color) {
        JRDesignRectangle rectangle = new JRDesignRectangle();
        rectangle.setX(x); rectangle.setY(y); rectangle.setWidth(width); rectangle.setHeight(height);
        rectangle.setBackcolor(color); rectangle.setMode(ModeEnum.OPAQUE);
        rectangle.getLinePen().setLineWidth(0f);
        return rectangle;
    }

    private static JRDesignStaticText staticText(String text, int size, int y, int width, int height,
            Color color, boolean bold) {
        return staticText(text, size, y, width, height, color, bold, HorizontalTextAlignEnum.LEFT);
    }

    private static JRDesignStaticText staticText(String text, int size, int y, int width, int height,
            Color color, boolean bold, HorizontalTextAlignEnum alignment) {
        return staticTextAt(text, size, 0, y, width, height, color, bold, alignment);
    }

    private static JRDesignStaticText staticTextAt(String text, int size, int x, int y, int width, int height,
            Color color, boolean bold, HorizontalTextAlignEnum alignment) {
        JRDesignStaticText element = new JRDesignStaticText();
        element.setText(text); element.setX(x); element.setY(y); element.setWidth(width); element.setHeight(height);
        style(element, size, color, bold); element.setHorizontalTextAlign(alignment);
        return element;
    }

    private static JRDesignTextField parameter(String name, int size, int y, int width, int height,
            Color color, boolean bold) {
        int x = name.equals("INSTITUTION_NAME") || name.equals("INSTITUTION_CONTACT") ? 18 : 0;
        return parameterAt(name, size, x, y, width, height, color, bold, HorizontalTextAlignEnum.LEFT);
    }

    private static JRDesignTextField parameterAt(String name, int size, int x, int y, int width, int height,
            Color color, boolean bold, HorizontalTextAlignEnum alignment) {
        JRDesignTextField element = new JRDesignTextField();
        element.setExpression(new JRDesignExpression("$P{" + name + "}"));
        element.setX(x);
        element.setY(y); element.setWidth(width); element.setHeight(height);
        element.setTextAdjust(TextAdjustEnum.STRETCH_HEIGHT);
        element.setHorizontalTextAlign(alignment);
        style(element, size, color, bold);
        return element;
    }

    private static JRDesignTextField stretchingParameter(String name, int size, int y, int width, int height) {
        JRDesignTextField element = parameter(name, size, y, width, height, DARK, false);
        element.setPositionType(PositionTypeEnum.FLOAT);
        return element;
    }

    private static void style(net.sf.jasperreports.engine.design.JRDesignTextElement element,
            int size, Color color, boolean bold) {
        element.setFontName("Helvetica"); element.setFontSize((float) size); element.setForecolor(color);
        element.setBold(bold); element.setPdfFontName("Helvetica");
        element.setPdfEncoding("Cp1252"); element.setPdfEmbedded(false);
    }
}
