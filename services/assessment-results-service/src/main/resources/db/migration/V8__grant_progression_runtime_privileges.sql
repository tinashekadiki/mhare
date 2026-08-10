-- Author: Tinashe K

GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES, TRIGGER
ON TABLE
    progression_rule_sets,
    progression_rule_outcomes,
    student_overall_decisions,
    student_overall_decision_results,
    student_overall_decision_events,
    progression_rule_sets_aud,
    progression_rule_outcomes_aud,
    student_overall_decisions_aud,
    student_overall_decision_results_aud,
    student_overall_decision_events_aud
TO emhare_service;
