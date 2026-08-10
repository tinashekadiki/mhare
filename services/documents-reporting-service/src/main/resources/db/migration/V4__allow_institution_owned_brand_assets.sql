-- Author: Tinashe K

ALTER TABLE uploaded_documents
    DROP CONSTRAINT ck_uploaded_documents_owner_type;

ALTER TABLE uploaded_documents
    ADD CONSTRAINT ck_uploaded_documents_owner_type CHECK (owner_type IN (
        'APPLICANT', 'APPLICATION', 'STUDENT', 'STAFF', 'FINANCE_RECORD',
        'ACADEMIC_WORKFLOW', 'INSTITUTION'
    ));
