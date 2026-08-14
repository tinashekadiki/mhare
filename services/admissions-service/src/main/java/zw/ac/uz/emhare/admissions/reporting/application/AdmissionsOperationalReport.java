package zw.ac.uz.emhare.admissions.reporting.application;

import java.time.Instant;
import java.util.List;

/** Generic tabular and chart contract shared by all admissions report families. @author Tinashe K */
public record AdmissionsOperationalReport(
        AdmissionsReportDefinition definition,
        Instant generatedAt,
        List<Metric> metrics,
        List<Column> columns,
        List<List<String>> rows,
        List<ChartPoint> chart,
        List<String> notes) {

    public AdmissionsOperationalReport {
        metrics = List.copyOf(metrics);
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
        chart = List.copyOf(chart);
        notes = List.copyOf(notes);
    }

    public record Metric(String label, String value) {}
    public record Column(String key, String label) {}
    public record ChartPoint(String label, long value, String series) {}
}
