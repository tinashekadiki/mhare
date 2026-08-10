package zw.ac.uz.emhare.dining.operations;

import java.util.Locale;

/** @author Tinashe K */
final class DiningOperationValues {
    private DiningOperationValues() {}
    static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    static String code(String value,String label){return required(value,label).toUpperCase(Locale.ROOT);}
    static void version(long actual,long expected,String label){if(actual!=expected)throw new IllegalStateException(label+" was changed by another operator. Refresh and try again.");}
}
