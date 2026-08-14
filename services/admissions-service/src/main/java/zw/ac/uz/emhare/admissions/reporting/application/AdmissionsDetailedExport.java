package zw.ac.uz.emhare.admissions.reporting.application;

/** Downloadable detailed admissions report. @author Tinashe K */
public record AdmissionsDetailedExport(byte[] content, String contentType, String fileName) {}
