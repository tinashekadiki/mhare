package zw.ac.uz.emhare.admissions.reporting.application;

import java.util.List;

/** Stable filter options for the admissions pipeline report. @author Tinashe K */
public record AdmissionsPipelineFilterOptions(
        List<AdmissionsPipelineReport.FilterOption> intakes,
        List<AdmissionsPipelineReport.FilterOption> applicationTypes,
        List<AdmissionsPipelineReport.FilterOption> programmes,
        List<AdmissionsPipelineReport.FilterOption> categories,
        List<AdmissionsPipelineReport.FilterOption> genders) {}
