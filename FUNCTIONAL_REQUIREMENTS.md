# University Student Management System - Functional Requirements Document

**Version:** 1.0
**Date:** January 7, 2026
**Document Type:** Functional Requirements Specification
**System:** Comprehensive University Management System

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Overview](#2-system-overview)
3. [User Roles and Permissions](#3-user-roles-and-permissions)
4. [Functional Requirements by Module](#4-functional-requirements-by-module)
5. [Data Models and Entities](#5-data-models-and-entities)
6. [Core Business Workflows](#6-core-business-workflows)
7. [Reporting Requirements](#7-reporting-requirements)
8. [Integration Requirements](#8-integration-requirements)
9. [Security and Audit Requirements](#9-security-and-audit-requirements)
10. [Non-Functional Requirements](#10-non-functional-requirements)

---

## 1. Executive Summary

### 1.1 Purpose
This document defines the complete functional requirements for rebuilding a comprehensive University Student Management System. The system manages all aspects of university operations from student admissions through graduation, including financial management, accommodation, dining services, and academic administration.

---

## 2. System Overview

### 2.1 System Purpose
A comprehensive university management platform that handles:
- Student admissions and applicant processing
- Academic registration and course management
- Examination scheduling and results management
- Financial operations (billing, payments, receipts)
- Student accommodation and dining services
- Staff and departmental administration
- Reporting and analytics
- Compliance and audit tracking

---

## 3. User Roles and Permissions

### 3.1 User Roles

#### 3.1.1 Administrative Roles
- **System Administrator**
  - Full system access and configuration
  - User management and role assignment
  - System-wide settings and parameters
  - Database maintenance and backups

- **Internal Audit**
  - Access to all audit trails and logs
  - Financial transaction reviews
  - Accommodation allocation audits
  - Compliance monitoring

#### 3.1.2 Academic Roles
- **Admissions Officers**
  - Applicant data entry and verification
  - Qualification assessment
  - Selection committee management
  - Offer letter generation and dispatch

- **Registration Officers**
  - Student registration processing
  - Course enrollment management
  - Registration period configuration
  - Class list generation

- **Faculty Deans/Staff**
  - Faculty-level approvals
  - Programme oversight
  - Result review and approval
  - Faculty-specific configurations

- **Department Heads/Chairs**
  - Department course approvals
  - Staff assignment to courses
  - Departmental deadlines management
  - Student progression monitoring

- **Course Lecturers/Instructors**
  - Course assignment viewing
  - Student enrollment lists
  - Result entry and modification
  - Course material management

- **Examination Officers**
  - Exam session setup
  - Timetable generation
  - Venue allocation
  - Invigilator assignment
  - Result publication

#### 3.1.3 Financial Roles
- **Finance Manager**
  - Fee structure definition
  - Invoice generation oversight
  - Payment processing approval
  - Financial reporting

- **Accounts Staff**
  - Payment receipt processing
  - General ledger management
  - Refund processing
  - Bank reconciliation

- **Billing Officers**
  - Invoice generation
  - Student account management
  - Payment plan setup
  - Waiver processing

#### 3.1.4 Student Services Roles
- **Accommodation Officers**
  - Room allocation management
  - Waiting list processing
  - Check-in/check-out procedures
  - Facility management

- **Hostel Wardens**
  - Daily resident management
  - Room inspection
  - Damage assessment
  - Student welfare

- **Dining Services Staff**
  - Meal planning
  - Dietary requirement tracking
  - Attendance monitoring
  - Dining facility management

#### 3.1.5 End User Roles
- **Students**
  - View personal academic records
  - View exam timetables
  - View financial statements
  - Update contact information
  - Apply for accommodation
  - Check dining hall assignments

- **Applicants**
  - Submit application data
  - Upload qualifications
  - Track application status
  - Accept/decline offers

### 3.2 Permission Model
- **Role-Based Access Control (RBAC)** using ACL (Access Control Lists)
- **Action-Level Permissions** (view, add, edit, delete) per controller
- **Hierarchical Permissions** with inheritance
- **Audit Logging** of all privileged actions

---

## 4. Functional Requirements by Module

### 4.1 Admissions & Applicant Management

#### 4.1.1 Applicant Registration
**FR-ADM-001:** The system shall allow applicants to register with personal details including:
- Full names (first, middle, last)
- Date of birth and age calculation
- National ID number
- Contact information (phone, email, address)
- Gender and marital status
- Disability status and special needs
- Next of kin details

**User Roles:** Applicants (self-entry), Admissions Officers (data entry/verification)

**FR-ADM-002:** The system shall capture applicant programme choices with:
- First, second, and third choice programmes
- Faculty preferences
- Intake selection (August, February)
- Application type (undergraduate, postgraduate, transfer)

**User Roles:** Applicants (selection), Admissions Officers (verification/modification)

**FR-ADM-003:** The system shall track applicant employment history:
- Current employer details
- Position and dates of employment
- Professional achievements
- Work experience documentation

**User Roles:** Applicants (self-entry), Admissions Officers (verification)

#### 4.1.2 Qualification Management
**FR-ADM-010:** The system shall record O-level qualifications:
- Examination board (ZIMSEC, Cambridge, etc.)
- Subject name
- Grade achieved
- Year of examination
- Multiple sitting support

**User Roles:** Applicants (self-entry), Admissions Officers (data entry/verification)

**FR-ADM-011:** The system shall record A-level qualifications:
- Examination board
- Subject name and code
- Points/grades
- Year of examination
- Principal and subsidiary subjects

**User Roles:** Applicants (self-entry), Admissions Officers (data entry/verification)

**FR-ADM-012:** The system shall record additional academic qualifications:
- Degree certificates
- Diplomas and certificates
- Professional qualifications
- Institution and year of award

**User Roles:** Applicants (self-entry), Admissions Officers (data entry/verification)

**FR-ADM-013:** The system shall validate qualifications against programme requirements

**User Roles:** System (automatic validation), Admissions Officers (manual review)

#### 4.1.3 Applicant Assessment
**FR-ADM-020:** The system shall calculate applicant points based on:
- A-level grades weighted by subject
- O-level grades (minimum requirements)
- Additional qualifications
- Programme-specific weighting rules

**User Roles:** System (automatic calculation), Admissions Officers (view/review)

**FR-ADM-021:** The system shall support multiple selection methods:
- Point-based ranking
- Faculty-specific criteria
- Special admissions routes (SAR)
- Quota allocations (disability, sports, etc.)

**User Roles:** Admissions Officers (selection), Faculty Deans (faculty-specific approvals), System Administrator (configure criteria)

**FR-ADM-022:** The system shall maintain applicant status:
- Pending review
- Shortlisted
- Selected
- Accepted
- Declined
- Withdrawn

**User Roles:** Admissions Officers (update status), Applicants (accept/decline offers)

#### 4.1.4 Reference Management
**FR-ADM-030:** The system shall track applicant references:
- Referee name and title
- Institution/organization
- Contact details
- Reference letter upload
- Verification status

**User Roles:** Applicants (provide referee details), Admissions Officers (verification/follow-up)

#### 4.1.5 Offer Letter Management
**FR-ADM-040:** The system shall generate offer letters with:
- Programme offered
- Intake and year
- Conditions of acceptance
- Registration requirements
- Fee structure summary
- Acceptance deadline

**User Roles:** Admissions Officers (generate/approve), System (auto-generate), Faculty Deans (approve for faculty)

**FR-ADM-041:** The system shall track offer letter dispatch:
- Date generated
- Date sent
- Delivery method (email, postal)
- Acceptance status
- Acceptance date

**User Roles:** Admissions Officers (dispatch tracking), System (email delivery), Applicants (receive/respond)

**FR-ADM-042:** The system shall support conditional offers with specific requirements

**User Roles:** Admissions Officers (create conditional offers), Faculty Deans (approve conditions)

**FR-ADM-043:** The system shall generate batch offer letters by:
- Faculty
- Programme
- Intake
- Selection round

**User Roles:** Admissions Officers (batch generation), Faculty Deans (approve batches)

#### 4.1.6 Application Payment
**FR-ADM-050:** The system shall process application fees:
- Fee amount by application type
- Payment reference generation
- Payment confirmation
- Receipt issuance

**User Roles:** Applicants (make payments), Billing Officers (process payments), Accounts Staff (reconciliation)

---

### 4.2 Student Records & Registration

#### 4.2.1 Student Profile Management
**FR-STU-001:** The system shall maintain comprehensive student profiles:
- Student registration number (auto-generated)
- Personal details (name, DOB, ID, gender)
- Contact information (personal and next of kin)
- Demographic data
- Student photograph
- Emergency contact details
- Medical information and disabilities

**User Roles:** Registration Officers (create/edit), Students (view/update contact info), System Administrator (full access)

**FR-STU-002:** The system shall track student programme assignment:
- Current programme
- Faculty and department
- Year of study / Level
- Entry year and intake
- Expected graduation year
- Programme specialisation

**User Roles:** Registration Officers (assign/modify), Faculty Deans (approve changes), Department Heads (review)

**FR-STU-003:** The system shall maintain student status:
- Active
- Suspended (with reason and dates)
- Withdrawn (with reason and dates)
- Deferred
- Graduated
- Deceased
- Expelled

**User Roles:** Registration Officers (update status), Faculty Deans (approve suspensions/expulsions), System Administrator (all status changes)

**FR-STU-004:** The system shall track student type:
- Regular/Conventional
- Part-time
- Block release
- Distance learning
- Occasional student
- Winter school student
- Exchange student

**User Roles:** Registration Officers (assign type), Admissions Officers (initial assignment)

**FR-STU-005:** The system shall maintain complete history of:
- Programme changes
- Status changes
- Personal detail updates
- With date stamps and user who made changes

**User Roles:** System (automatic logging), Internal Audit (review audit trails), System Administrator (access all history)

#### 4.2.2 Student ID Card Management
**FR-STU-010:** The system shall generate student ID cards with:
- Student photo
- Registration number
- Name and programme
- Faculty
- Barcode for system integration
- Validity period

**User Roles:** Registration Officers (generate/print cards), Students (request cards)

**FR-STU-011:** The system shall track ID card:
- Issue date
- Expiry date
- Reprint history
- Lost/stolen status
- Replacement requests

**User Roles:** Registration Officers (issue/reissue), Students (report lost/request replacement), System (track history)

#### 4.2.3 Student Registration Process
**FR-REG-001:** The system shall support multi-stage registration:
- Pre-registration (intent to register)
- Course selection
- Department approval
- Faculty review
- Chairperson confirmation
- Final registration confirmation

**User Roles:** Students (initiate/select courses), Department Heads (approve), Faculty Deans (review), Registration Officers (finalize), System (workflow management)

**FR-REG-002:** The system shall validate registration eligibility:
- Student must be active
- Financial clearance check
- Previous level completion check
- Academic progression requirements met
- Registration period must be open

**User Roles:** System (automatic validation), Registration Officers (review validation results), Finance Manager (financial clearance)

**FR-REG-003:** The system shall enforce registration periods:
- Normal registration window
- Late registration window (with penalties)
- Registration by programme
- Registration by year/level
- Override capabilities for authorized users

**User Roles:** Registration Officers (configure periods), System Administrator (system-wide configuration), System (enforce deadlines)

**FR-REG-004:** The system shall support registration approval workflow:
- Student submits course selection
- Department approves/rejects courses
- Faculty reviews registration
- Chair confirms registration
- System finalizes and generates invoice

**User Roles:** Students (submit), Department Heads (approve courses), Faculty Deans (review), Department Chairs (confirm), System (finalize/invoice)

**FR-REG-005:** The system shall handle late registration:
- Late registration dates by programme
- Late registration penalties
- Approval requirements
- Cut-off date enforcement

**User Roles:** Students (request late registration), Registration Officers (process), Department Heads (approve), Billing Officers (apply penalties)

**FR-REG-006:** The system shall support registration overrides:
- Bypass financial holds
- Bypass prerequisite checks
- Extend registration deadline
- With authorization and audit trail

**User Roles:** Registration Officers (override with justification), Faculty Deans (authorize overrides), System Administrator (full override capability), Internal Audit (review override usage)

#### 4.2.4 Course Registration
**FR-REG-020:** The system shall allow students to register for courses based on:
- Programme curriculum requirements
- Year/level appropriate courses
- Prerequisites completion
- Course capacity limits
- Timetable conflicts

**User Roles:** Students (select courses), System (validate eligibility), Course Lecturers (view enrollments), Department Heads (manage capacity)

**FR-REG-021:** The system shall support different course types:
- Compulsory/Core courses (auto-assigned)
- Elective courses (student choice)
- Prerequisite courses
- Corequisite courses
- Supplementary courses

**User Roles:** System (auto-assign compulsory), Students (select electives), Department Heads (configure course types), Registration Officers (manage assignments)

**FR-REG-022:** The system shall enforce course limits:
- Maximum courses per semester
- Minimum courses per semester
- Maximum credit hours
- Course capacity (enrollment cap)

**User Roles:** System (enforce limits), Registration Officers (override limits with approval), System Administrator (configure limits per programme)

**FR-REG-023:** The system shall handle repeat courses:
- Identify failed courses requiring repeat
- Allow registration for repeat courses
- Track repeat attempts
- Apply repeat course fees

**User Roles:** System (identify repeat courses), Students (register for repeats), Registration Officers (verify/approve), Billing Officers (apply repeat fees)

**FR-REG-024:** The system shall support carry courses:
- Courses carried from previous level
- Automatic inclusion in current registration
- Tracking of carry course completion

**User Roles:** System (auto-include carry courses), Registration Officers (manage carry courses), Faculty Deans or Admin (approve carry decisions)

**FR-REG-025:** The system shall handle course additions/drops:
- Add course (within add/drop period)
- Drop course (within add/drop period)
- Financial adjustments for changes
- Approval workflow for late changes

**User Roles:** Students (add/drop courses), Department Heads (approve late changes), Registration Officers (process changes), Billing Officers (adjust fees)

#### 4.2.5 Programme Changes
**FR-REG-030:** The system shall support student programme transfers:
- Transfer within faculty
- Transfer between faculties
- Change of specialisation
- Approval workflow (department, faculty, registry)
- Credit transfer evaluation
- Course equivalency mapping

**User Roles:** Students (request transfer), Department Heads (approve department-level), Faculty Deans (approve faculty-level), Registration Officers (process transfer/credit evaluation)

**FR-REG-031:** The system shall maintain programme change history:
- Previous programme
- New programme
- Date of change
- Reason for change
- Approving authority
- Audit trail

**User Roles:** System (automatic logging), Internal Audit (review changes), Registration Officers (view history)

#### 4.2.6 Registration Reporting
**FR-REG-040:** The system shall generate class lists:
- By course
- By programme and level
- By faculty
- Formatted for lecturers
- With student photos
- Export to Excel/PDF

**User Roles:** Course Lecturers (generate/view own courses), Department Heads (view department courses), Registration Officers (generate all lists), Faculty Deans (view faculty lists)

**FR-REG-041:** The system shall generate enrollment registers:
- Total enrollment by faculty
- Enrollment by programme
- Enrollment by level/year
- Gender distribution
- Student type distribution
- Trend analysis by intake

**User Roles:** Registration Officers (generate reports), Faculty Deans (view faculty reports), System Administrator (generate system-wide reports), Internal Audit (access all reports)

**FR-REG-042:** The system shall provide registration dashboards:
- Registration progress tracking
- Approval queue monitoring
- Registration statistics
- Outstanding approvals

**User Roles:** Registration Officers (monitor all), Department Heads (view department queue), Faculty Deans (view faculty statistics), System Administrator (system-wide dashboard)

---

### 4.3 Academic Records & Results Management

#### 4.3.1 Exam Session Management
**FR-EXM-001:** The system shall create and manage exam sessions with:
- Academic year and semester/period association
- A unique session name (e.g., "November 2025 Final Exams")
- Definable start and end dates for the entire session
- Session type (e.g., Final, Supplementary, Deferred, Special)
- Status (e.g., Planned, Open for Registration, In Progress, Moderation, Completed, Archived)

**User Roles:** Examination Officers (create/configure), Faculty Deans (approve session plans), System Administrator (system-wide configuration)

**FR-EXM-001a:** The system shall allow cloning of an existing exam session to create a new one, copying over key parameters like test types and invigilator lists.

**User Roles:** Examination Officers

**FR-EXM-001b:** The system shall allow locking of an exam session after completion to prevent any further changes to results or associated data, with overrides only available to authorized roles.

**User Roles:** Examination Officers, System Administrator (override)

**FR-EXM-002:** The system shall configure detailed exam parameters per course:
- Exam duration in minutes
- Exam format/test type (e.g., Written, Practical, Oral, Multiple Choice)
- Contribution weights of continuous assessment and final exam to the overall course mark
- Pass marks and grading scales (which may be inherited from faculty or programme settings)

**User Roles:** Examination Officers (configure), Department Heads (approve parameters), Faculty Deans (set faculty-level standards)

#### 4.3.2 Exam Timetabling
**FR-EXM-010:** The system shall generate a master exam timetable using an automated scheduling engine that considers:
- Course exam scheduling based on student enrollment to avoid conflicts
- Date, time, and duration assignment for each exam
- Venue allocation based on capacity and suitability (e.g., lab for practicals)
- Student and programme groupings
- Automated conflict detection and reporting for students and invigilators

**User Roles:** Examination Officers (generate/manage), System (automatic scheduling), Faculty Deans (approve timetable)

**FR-EXM-011:** The system shall enforce configurable timetabling rules and constraints:
- Hard constraints (must not be violated):
  - No student scheduled for two exams simultaneously
  - No invigilator assigned to two venues simultaneously
- Soft constraints (should be minimized):
  - Ensure adequate spacing between a student's exams (e.g., at least one day apart)
  - Ensure venue capacity is not exceeded by more than a configurable percentage
  - Grouping students from the same programme/level in the same venue
- Prioritization of large-enrollment courses

**User Roles:** System (rule enforcement), Examination Officers (configure rules/handle exceptions)

**FR-EXM-012:** The system shall provide a visual interface for manual timetable adjustments (drag-and-drop):
- Manually move an exam to a different date/time or venue
- Immediately highlight any conflicts caused by manual adjustments
- Log all manual overrides with user and timestamp for audit purposes

**User Roles:** Examination Officers (make adjustments), Faculty Deans (authorize major changes), Internal Audit (review changes)

#### 4.3.3 Exam Venue and Invigilator Management
**FR-EXM-020:** The system shall manage a database of exam venues with details on:
- Venue name, code, and location
- Standard seating capacity and maximum (exam) seating capacity
- Venue type (e.g., Hall, Classroom, Lab)
- Available facilities (e.g., projectors, computers) and accessibility features

**User Roles:** Examination Officers (manage venues), System Administrator (configure database)

**FR-EXM-021:** The system shall intelligently allocate students to venues:
- Automated allocation based on seating capacity and enrollment numbers
- Support for multi-venue allocation for large courses
- Consideration for special needs (e.g., accessibility) and extra time accommodations
- Generation of venue-specific student lists and seating plans

**User Roles:** Examination Officers (oversee allocation), System (automatic allocation)

**FR-EXM-025:** The system shall manage a database of invigilators:
- Invigilator details (name, staff ID, department, contact)
- Availability tracking and constraints
- Automated assignment to exam sessions and venues, avoiding conflicts
- Generation of personalized invigilator schedules and duty rosters

**User Roles:** Examination Officers (manage invigilators/assignments), System (auto-assign/detect conflicts)

#### 4.3.4 Student Exam Registration and Special Cases
**FR-EXM-030:** The system shall automatically register students for exams based on their final course registration for the semester.

**User Roles:** System (automatic registration)

**FR-EXM-031:** The system shall manage special exam cases through a formal application and approval workflow:
- **Deferred Exams:** For students unable to sit for an exam due to documented illness or bereavement.
- **Aegrotat Pass:** Awarding a pass based on coursework for students who fall ill during an exam.
- **Special Exams:** For students with other valid, documented reasons for missing a final exam.
- The workflow shall capture supporting documentation and record the final decision.

**User Roles:** Students (submit requests), Examination Officers (process), Faculty Deans (approve), Department Heads (recommend)

#### 4.3.5 Results Entry and Processing
**FR-RES-001:** The system shall provide multiple interfaces for secure result entry:
- An online interface for individual entry by course instructors.
- A standardized Excel/CSV template for secure batch upload of results for a course.
- The upload process shall validate data formats, student IDs, and mark ranges, providing a clear error report for failed records.

**User Roles:** Course Lecturers (enter/upload), Examination Officers (upload/modify), Department Heads (review/enter)

**FR-RES-002:** The system shall record detailed and component-based exam results:
- Student identifier, course code, exam session
- Marks and/or grades for each assessment component (e.g., Assignment 1, Mid-term Test, Final Exam)
- Automatically calculate the final weighted mark based on pre-configured component weights.
- Automatically convert the final percentage mark into a grade based on the applicable grading scale (e.g., A, B+, C).

**User Roles:** Course Lecturers (record marks), System (calculate final mark/grade), Examination Officers (verify)

**FR-RES-004:** The system shall support flexible result components:
- Define multiple, weighted assessment components per course (e.g., Coursework 40%, Final Exam 60%).
- Support for "best of N" calculations (e.g., best 2 of 3 assignments).
- Automatic calculation of the final weighted total based on component marks.

**User Roles:** Department Heads (configure weights), Course Lecturers (enter component marks), System (calculate totals)

**FR-RES-006:** The system shall maintain a complete and immutable audit trail for every result entry, modification, or deletion:
- Original mark/grade, new mark/grade
- User who made the change, with their role
- Timestamp of the change
- A mandatory, descriptive reason for the change
- IP address of the user making the change

**User Roles:** System (automatic logging), Internal Audit (review trails), Examination Officers (track integrity)

#### 4.3.6 Result Moderation and Approval
**FR-RES-020:** The system shall facilitate a multi-level result moderation workflow:
- **Stage 1 (Department):** Department Head reviews results for all courses in the department, checking for consistency and anomalies.
- **Stage 2 (Faculty):** Faculty Moderation Committee reviews results, often with statistical analysis.
- **Stage 3 (External Examiner):** External examiners are given secure, read-only access to review results and add comments.
- Each stage shall be documented with approver, timestamp, and comments.

**User Roles:** Department Heads, Faculty Deans, External Examiners, Examination Officers (coordinate)

**FR-RES-021:** The system shall provide statistical analysis tools to support moderation:
- Generate reports on mean, median, mode, and standard deviation for any set of results.
- Produce grade distribution histograms and compare them against historical averages for the course.
- Flag outliers (unusually high or low marks) for review.

**User Roles:** System (generate reports), Department Heads, Faculty Deans (use reports for moderation)

**FR-RES-022:** The system shall support controlled result adjustments during moderation:
- Individual mark adjustments with mandatory justification.
- Bulk adjustments (e.g., scaling all marks by a percentage or adding a fixed value) with high-level authorization.
- All adjustments must be logged in the audit trail, linking them to the moderation session.

**User Roles:** Department Heads (propose adjustments), Faculty Deans (authorize bulk adjustments), Examination Officers (process approved adjustments)

#### 4.3.7 Result Publication
**FR-RES-030:** The system shall support controlled, phased, and batch publication of results once approved by the Faculty Board.

**User Roles:** Faculty Deans (authorize publication), Examination Officers (execute publication)

**FR-RES-031:** The system shall enforce strict control over result visibility:
- Results must remain hidden from students and non-essential staff until officially published.
- Publication can be done by faculty, by programme, or for the entire university at once.
- Once published, results are immediately visible on the student self-service portal.

**User Roles:** System (control visibility), Examination Officers (manage publication process)

**FR-RES-033:** The system shall automatically generate individual student result slips for the semester in PDF format, including:
- All courses taken with marks, grades, and credits obtained.
- Semester Grade Point Average (GPA).
- Cumulative Grade Point Average (CGPA).
- Any academic decisions made (e.g., "Proceed to Next Level").

**User Roles:** System (generate slips), Students (download), Examination Officers (generate for any student)

**FR-RES-035:** The system shall manage result appeals through a formal workflow:
- Student submits a formal appeal via the portal within a defined period.
- The appeal is routed to the department, then faculty, then examinations board for review.
- The system shall track the status of the appeal and record the final outcome, triggering a result change if the appeal is successful.

**User Roles:** Students (submit), Examination Officers (coordinate), Department Heads, Faculty Deans (review/decide)

#### 4.3.8 Academic Decisions
**FR-DEC-001:** The system shall automatically compute end-of-level/end-of-programme academic decisions for each student based on configurable rules, including:
- Overall pass/fail status based on GPA and failed courses.
- Determination of courses to be repeated or carried over.
- Assignment of academic standing (e.g., Good Standing, Probation, Discontinue).
- Identification of eligibility for supplementary exams.

**User Roles:** System (automatic computation), Examination Officers (process), Faculty Deans (review/approve)

**FR-DEC-002:** The system shall support a comprehensive list of decision types:
- **Proceed:** Proceed to the next level of study.
- **Proceed with Carry:** Proceed while repeating specific failed courses.
- **Repeat Level:** Repeat the entire academic level.
- **Supplementary Exams:** Eligible to sit for supplementary exams in failed courses.
- **Probation:** Academic warning, may have registration restrictions.
- **Discontinue:** Required to withdraw from the programme.
- **Aegrotat Pass:** Pass awarded due to special circumstances.
- **Graduating:** All requirements met, eligible for graduation.

**User Roles:** Faculty Deans (approve decisions), Examination Officers (assign decisions)

**FR-DEC-003:** The system shall allow Faculty Decision Boards to review, override, and approve the system-generated decisions in a dedicated interface before publication.

**User Roles:** Faculty Deans, Department Heads, Examination Officers (coordinate)

#### 4.3.9 Transcripts
**FR-TRA-001:** The system shall generate official academic transcripts in a secure, standardized PDF format, showing:
- A complete, chronological record of all courses and results.
- Semester, yearly, and cumulative performance statistics (GPA, credits).
- A record of all academic decisions, honors, and awards.
- The final degree awarded and classification (if graduated).
- A key explaining the grading system.

**User Roles:** Registration Officers (generate/certify), Students (request), System (format)

**FR-TRA-003:** The system shall enhance transcript security and verification through:
- A unique, verifiable reference number on each transcript.
- An encrypted QR code that links to a secure online verification portal.
- The portal shall confirm the authenticity of the transcript details when the QR code is scanned.

**User Roles:** System (generate codes), Registration Officers (verify), External Parties (verify via portal)

**FR-TRA-004:** The system shall implement advanced security features for both digital and printed transcripts:
- Use of encrypted digital signatures for electronic transcripts.
- Support for printing on secure, tamper-evident paper with watermarks.
- Blockchain-based verification as a future enhancement for immutable records.

**User Roles:** System (generate secure transcripts), Registration Officers (issue), System Administrator (configure security)

#### 4.3.10 Graduation Management
**FR-GRD-001:** The system shall run a graduation audit process to automatically identify potential graduands by:
- Verifying completion of all required courses and credits.
- Checking fulfillment of GPA and programme-specific requirements.
- Flagging any outstanding fees or disciplinary actions.
- Generating a list of eligible and near-eligible students for review.

**User Roles:** System (identify graduands), Examination Officers (verify eligibility), Faculty Deans (approve lists)

**FR-GRD-002:** The system shall automatically compute the final degree classification based on pre-configured, programme-specific rules, such as:
- Weighted GPA calculations (e.g., higher weight for final year courses).
- Thresholds for First Class, Upper Second (2.1), Lower Second (2.2), Pass, etc.
- Requirements for Distinction, Merit, or Pass for non-honours degrees.

**User Roles:** System (calculate), Faculty Deans (approve), Examination Officers (verify)

**FR-GRD-005:** The system shall record final graduation details, including:
- Official graduation/conferment date.
- The final degree and classification awarded.
- A unique certificate number for tracking.
- A flag indicating attendance at the graduation ceremony.

**User Roles:** Registration Officers (record details), System (generate certificate numbers)

#### 4.3.11 Academic Progression
**FR-PRG-001:** The system shall define and enforce clear academic progression rules for each programme, specifying:
- The minimum number of credits or courses required to pass a level.
- The maximum number of failed courses that can be carried to the next level.
- The minimum GPA required to proceed.
- Conditions that lead to academic probation or discontinuation.

**User Roles:** Faculty Deans (define rules), System Administrator (configure rules), System (enforce rules)

**FR-PRG-002:** The system shall automatically evaluate each student's record against progression rules at the end of each academic year/level.

**User Roles:** System (automatic evaluation), Examination Officers (review evaluation)

**FR-PRG-003:** The system's automated evaluation shall result in a clear, system-generated academic decision for each student (e.g., "Proceed," "Repeat Level"), which is then fed into the formal decision-making process.

**User Roles:** System (generate decision), Faculty Deans (review and approve)

---

### 4.4 Financial Management

#### 4.4.1 Fee Structure Management
**FR-FIN-001:** The system shall define fee structures:
- By programme
- By level/year
- By semester
- By student type (full-time, part-time)
- By faculty
- Effective dates

**User Roles:** Finance Manager (define/modify fee structures), System Administrator (configure fee rules), Faculty Deans (approve faculty fees), Internal Audit (review fee changes)

**FR-FIN-002:** The system shall support fee types:
- Tuition fees
- Course fees (per course)
- Registration fees
- Examination fees
- Late registration fees
- Repeat course fees
- Supplementary exam fees
- Application fees
- Graduation fees
- ID card fees
- Transcript fees
- Library fees
- ICT fees
- Student union fees

**User Roles:** Finance Manager (configure fee types), Billing Officers (apply fees), System Administrator (manage fee catalog)

**FR-FIN-003:** The system shall support fee parameters:
- Base fee amount
- Incremental fees
- Discount rules
- Waiver policies
- Penalty fees

**User Roles:** Finance Manager (set parameters), System Administrator (configure rules), Internal Audit (review parameters)

#### 4.4.2 Student Billing
**FR-FIN-020:** The system shall generate student invoices:
- Automatically upon registration
- Based on registered courses
- Based on student programme and level
- Include all applicable fees
- Unique invoice number

**User Roles:** System (automatic generation), Billing Officers (manual generation/verification), Students (view invoices)

**FR-FIN-021:** The system shall create invoice line items:
- Fee description
- Amount
- Quantity
- Total
- GL account allocation

**User Roles:** System (create line items), Billing Officers (verify/modify line items), Accounts Staff (verify GL allocations)

**FR-FIN-022:** The system shall support invoice adjustments:
- Add additional charges
- Remove charges
- Modify amounts
- Credit notes for overpayment
- With authorization and reason

**User Roles:** Billing Officers (make adjustments), Finance Manager (authorize major adjustments), Internal Audit (review adjustments), System (track changes)

**FR-FIN-023:** The system shall generate invoice documents:
- Official invoice format
- Student details
- Itemized fee breakdown
- Payment due date
- Payment instructions
- Bank account details
- Invoice quotation (preliminary)

**User Roles:** System (generate documents), Students (view/download/print invoices), Billing Officers (regenerate invoices)

**FR-FIN-024:** The system shall track invoice status:
- Unpaid (outstanding)
- Partially paid
- Fully paid
- Overdue
- Written off
- Waived

**User Roles:** System (update status automatically), Billing Officers (view/monitor status), Accounts Staff (reconcile status), Finance Manager (approve write-offs)

#### 4.4.3 Payment Processing
**FR-FIN-040:** The system shall process payments via:
- Bank deposits
- Electronic funds transfer (EFT)
- Mobile money (EcoCash, etc.)
- Online payment gateway
- Cash payments (cashier)
- Credit card
- Third-party payments (sponsors, employers)

**User Roles:** Students (make payments), Accounts Staff (process payments), Billing Officers (record payments), System (process online payments)

**FR-FIN-041:** The system shall record payment details:
- Payment reference number
- Payment date
- Payment method
- Amount paid
- Currency
- Bank and branch
- Depositor name
- Payment suspense (if student unclear)

**User Roles:** Accounts Staff (record details), Billing Officers (enter payment data), System (capture online payment details), Internal Audit (review payment records)

**FR-FIN-042:** The system shall allocate payments:
- To specific invoices
- By invoice line item
- Partial payment allocation
- Automatic oldest debt first
- Manual allocation override

**User Roles:** System (automatic allocation), Accounts Staff (manual allocation), Billing Officers (verify allocations), Finance Manager (approve allocation overrides)

**FR-FIN-043:** The system shall handle payment suspense:
- Unallocated payments
- Search by amount, date, reference
- Allocate to correct student
- Return unclaimed payments

**User Roles:** Accounts Staff (manage suspense accounts), Billing Officers (research/allocate suspense), Finance Manager (authorize returns), Internal Audit (review suspense)

**FR-FIN-044:** The system shall integrate with payment gateways:
- Real-time payment notification
- Payment verification
- Automatic receipt generation
- Payment failure handling
- Reconciliation with gateway reports

**User Roles:** System (gateway integration), Accounts Staff (reconcile gateway transactions), Finance Manager (review gateway reports), System Administrator (configure gateway settings)

#### 4.4.4 Receipt Management
**FR-FIN-060:** The system shall generate receipts:
- Official receipt number
- Receipt date
- Student details
- Amount received
- Payment method
- Balance remaining
- Cashier/user details
- Print and email capability

**User Roles:** System (generate receipts), Accounts Staff (issue receipts), Billing Officers (verify receipts), Students (receive/download receipts)

**FR-FIN-061:** The system shall support receipt types:
- Official receipt
- Provisional receipt
- Temporary receipt
- Duplicate receipt (marked as duplicate)

**User Roles:** Accounts Staff (determine receipt type), System (mark duplicates), Finance Manager (authorize official receipts)

**FR-FIN-062:** The system shall track receipt printing:
- Print date and time
- Number of prints
- User who printed
- Prevent unauthorized reprints

**User Roles:** System (track printing), Internal Audit (review print history), Accounts Staff (print receipts), Finance Manager (authorize reprints)

#### 4.4.5 Refund Management
**FR-FIN-080:** The system shall process refunds:
- Overpayments
- Withdrawals
- Programme changes
- Duplicate payments
- Accommodation forfeitures

**User Roles:** Students (request refunds), Billing Officers (process requests), Finance Manager (approve refunds), Accounts Staff (issue refunds)

**FR-FIN-081:** The system shall create refund journals:
- Refund reason
- Original payment reference
- Refund amount
- Approval workflow
- Refund method (bank transfer, cash, etc.)
- Beneficiary details

**User Roles:** Billing Officers (create journals), Finance Manager (approve journals), Accounts Staff (execute refunds), Internal Audit (review journals)

**FR-FIN-082:** The system shall track refund transactions:
- Refund request date
- Approval date
- Payment date
- Transaction reference
- Audit trail

**User Roles:** System (track transactions), Accounts Staff (record payment dates), Finance Manager (review refunds), Internal Audit (audit refund trail)

#### 4.4.6 Financial Clearance
**FR-FIN-100:** The system shall determine financial status:
- Current balance (debit/credit)
- Overdue amounts
- Payment history
- Clearance status

**User Roles:** System (calculate status), Students (view own status), Billing Officers (review student status), Finance Manager (authorize clearance)

**FR-FIN-101:** The system shall enforce financial holds:
- Block registration if balance exceeds limit
- Block exam attendance
- Block transcript issuance
- Block graduation
- With override capability for authorized users

**User Roles:** System (enforce holds automatically), Finance Manager (authorize overrides), Billing Officers (manage holds), Registration Officers (view hold status)

**FR-FIN-102:** The system shall issue clearance certificates:
- Financial clearance for graduation
- Financial clearance for transfer
- No-debt certificates

**User Roles:** Students (request certificates), Billing Officers (generate certificates), Finance Manager (sign certificates), Registration Officers (verify clearance)

#### 4.4.7 Waivers and Adjustments
**FR-FIN-120:** The system shall process fee waivers:
- Full or partial waiver
- Waiver type (sponsor, scholarship, hardship, staff dependent)
- Approval workflow
- Effective period
- Documentation requirements

**User Roles:** Students (request waivers), Billing Officers (process waiver applications), Finance Manager (approve waivers), Faculty Deans (recommend waivers), Internal Audit (review waivers)

**FR-FIN-121:** The system shall record waiver details:
- Waiver amount or percentage
- Waiver reason
- Sponsor/benefactor
- Approval date and authority
- Audit trail

**User Roles:** Billing Officers (record details), System (track audit trail), Finance Manager (approve details), Internal Audit (review waiver records)

**FR-FIN-122:** The system shall support account adjustments:
- Credit adjustments
- Debit adjustments
- Adjustment reason
- Approval workflow
- GL account impact

**User Roles:** Billing Officers (request adjustments), Finance Manager (approve adjustments), Accounts Staff (post adjustments), Internal Audit (review adjustments)

#### 4.4.8 General Ledger Integration
**FR-FIN-140:** The system shall post transactions to GL:
- Invoice generation → Debit student account, Credit revenue
- Payment receipt → Debit cash/bank, Credit student account
- Refund → Debit student account, Credit cash/bank
- Fee waivers → Debit waiver account, Credit student account

**User Roles:** System (automatic GL posting), Accounts Staff (verify postings), Finance Manager (review GL entries), Internal Audit (audit GL transactions)

**FR-FIN-141:** The system shall maintain GL accounts:
- Chart of accounts
- Account types (asset, liability, revenue, expense)
- Account grouping
- Account balances

**User Roles:** Finance Manager (manage chart of accounts), Accounts Staff (maintain accounts), System Administrator (configure account structure), Internal Audit (review account structure)

**FR-FIN-142:** The system shall generate GL reports:
- Trial balance
- General ledger detail
- Account transactions
- Cashbook records
- Bank account reconciliation

**User Roles:** Accounts Staff (generate reports), Finance Manager (review reports), Internal Audit (audit reports), System Administrator (configure report formats)

#### 4.4.9 Financial Reporting
**FR-FIN-160:** The system shall generate financial reports:
- Student billing summary (by faculty, programme, level)
- Payment collection report (daily, weekly, monthly)
- Outstanding debt report
- Fee waiver report
- Refund journal
- Cash book
- Revenue analysis
- Payment method analysis
- Age analysis of debt

**User Roles:** Finance Manager (generate/review reports), Accounts Staff (prepare reports), Faculty Deans (view faculty reports), System Administrator (access all reports), Internal Audit (audit reports)

**FR-FIN-161:** The system shall support financial dashboards:
- Total revenue by period
- Collection rate
- Outstanding debt trends
- Payment method distribution
- Faculty/programme revenue comparison

**User Roles:** Finance Manager (view all dashboards), Faculty Deans (view faculty dashboards), Accounts Staff (monitor collection dashboards), System Administrator (configure dashboards)

---

### 4.5 Accommodation Management

#### 4.5.1 Accommodation Facility Management
**FR-ACC-001:** The system shall maintain accommodation facilities:
- Halls of residence
- Facility name and code
- Location and address
- Total capacity
- Facility type (on-campus, off-campus)
- Gender allocation
- Facility rating

**User Roles:** Accommodation Officers (manage facilities), Hostel Wardens (update facility info), System Administrator (configure facilities), Internal Audit (review facility records)

**FR-ACC-002:** The system shall manage facility premises:
- Building/block name
- Floor numbers
- Wing/section
- Accessibility features
- Amenities

**User Roles:** Accommodation Officers (manage premises), Hostel Wardens (update premise details), System Administrator (configure premise structure)

**FR-ACC-003:** The system shall track accommodation landlords:
- Off-campus accommodation providers
- Landlord details and contact
- Agreement terms
- Payment arrangements

**User Roles:** Accommodation Officers (manage landlord relationships), Finance Manager (review agreements), System Administrator (maintain landlord database)

#### 4.5.2 Room Management
**FR-ACC-020:** The system shall manage rooms:
- Room number/code
- Hall/facility assignment
- Floor and wing
- Room type (single, double, triple, etc.)
- Capacity
- Gender allocation
- Room status (available, occupied, under repair, condemned)
- Room rating (standard, premium, etc.)

**User Roles:** Accommodation Officers (manage rooms), Hostel Wardens (update room status), System Administrator (configure room database)

**FR-ACC-021:** The system shall track room features:
- En-suite bathroom
- Shared facilities
- Furniture inventory
- Accessibility features
- Special equipment

**User Roles:** Hostel Wardens (track features/inventory), Accommodation Officers (verify features), System (maintain feature database)

**FR-ACC-022:** The system shall support room fees:
- Fee by room type
- Fee by facility rating
- Fee by semester/period
- Effective dates

**User Roles:** Finance Manager (set room fees), Accommodation Officers (recommend fees), Billing Officers (apply fees), System Administrator (configure fee structure)

#### 4.5.3 Room Allocation Process
**FR-ACC-040:** The system shall manage allocation periods:
- Allocation window opening
- Application deadline
- Allocation processing period
- Check-in period
- Check-out period
- By semester

**User Roles:** Accommodation Officers (configure periods), System Administrator (set system-wide dates), Hostel Wardens (coordinate check-in/out periods)

**FR-ACC-041:** The system shall accept allocation applications:
- Student applies for accommodation
- Preference selection (facility, room type)
- Special requirements (ground floor, disability)
- Dietary requirements
- Medical needs

**User Roles:** Students (submit applications), Accommodation Officers (review applications), System (process applications)

**FR-ACC-042:** The system shall manage waiting lists:
- Automatic waiting list placement
- Priority ranking calculation
- Queue position tracking
- Notification when room available

**User Roles:** System (manage waiting list/calculate priority), Accommodation Officers (review/adjust priority), Students (view queue position)

**FR-ACC-043:** The system shall calculate allocation priority:
- Student type weighting
- Programme weighting
- Year of study weighting
- Distance from home
- Disability priority
- Sponsored student priority
- Previous allocation history
- First-year priority

**User Roles:** System (calculate priority), Accommodation Officers (configure priority rules/override priority), System Administrator (set weighting formulas)

**FR-ACC-044:** The system shall process allocation groups:
- Group-based allocation rules
- By student type
- By programme
- By faculty
- By disability
- By sponsor
- Allocation percentages per group

**User Roles:** Accommodation Officers (configure groups/set percentages), Faculty Deans (review faculty allocations), System Administrator (manage group structure)

**FR-ACC-045:** The system shall allocate rooms:
- Automatic allocation based on priority
- Manual allocation override
- Room matching (gender, group, needs)
- Capacity enforcement
- Conflict detection

**User Roles:** System (automatic allocation), Accommodation Officers (manual allocation/override), Hostel Wardens (verify allocations)

**FR-ACC-046:** The system shall notify students:
- Allocation outcome (success/waiting)
- Room assignment details
- Payment requirements
- Check-in instructions
- Deadline for acceptance

**User Roles:** System (send notifications), Accommodation Officers (manage notifications), Students (receive notifications/respond)

#### 4.5.4 Room Occupancy Management
**FR-ACC-060:** The system shall track room occupancy:
- Current occupants
- Bed/space allocation within room
- Occupancy start date
- Occupancy end date
- Occupancy rate by facility

**User Roles:** System (track occupancy), Hostel Wardens (update occupancy status), Accommodation Officers (monitor occupancy rates)

**FR-ACC-061:** The system shall process check-in:
- Verify payment of accommodation fees
- Room condition inspection
- Key/access card issuance
- Occupancy agreement signing
- Inventory verification

**User Roles:** Hostel Wardens (process check-in/inspect rooms), Students (complete check-in), Billing Officers (verify payment), System (record check-in)

**FR-ACC-062:** The system shall process check-out:
- Check-out date recording
- Room condition assessment
- Damage charges
- Key/access card return
- Clearance certificate

**User Roles:** Hostel Wardens (process check-out/assess damage), Students (complete check-out), Billing Officers (assess damage charges), System (issue clearance)

**FR-ACC-063:** The system shall support room swaps:
- Student-initiated swap requests
- Approval workflow
- Room availability verification
- Effective date
- Notification to affected students

**User Roles:** Students (request swaps), Accommodation Officers (approve swaps), Hostel Wardens (facilitate swaps), System (verify availability/process swaps)

**FR-ACC-064:** The system shall handle room changes:
- Eviction (disciplinary)
- Administrative move
- Upgrade/downgrade
- Emergency relocation
- Reason documentation

**User Roles:** Accommodation Officers (authorize changes), Hostel Wardens (process moves), Faculty Deans (approve evictions), System (track changes/reasons)

#### 4.5.5 Accommodation Fees
**FR-ACC-080:** The system shall bill accommodation fees:
- Based on room type and facility
- Based on allocation period (semester, year)
- Automatic invoice generation
- Integration with student billing

**User Roles:** System (generate bills automatically), Billing Officers (verify billing), Accommodation Officers (coordinate billing), Students (receive invoices)

**FR-ACC-081:** The system shall enforce accommodation payment:
- Verify payment before check-in
- Payment plans allowed
- Forfeit room if not paid by deadline
- Return to waiting list if forfeited

**User Roles:** System (verify payment/enforce deadlines), Billing Officers (process payments), Accommodation Officers (manage forfeitures), Students (make payments)

#### 4.5.6 Accommodation Blacklist
**FR-ACC-100:** The system shall maintain blacklist:
- Students with disciplinary issues
- Students with damage charges unpaid
- Students who forfeited without notice
- Blacklist reason and duration
- Blacklist prevents future allocation

**User Roles:** Accommodation Officers (add to/remove from blacklist), Hostel Wardens (recommend blacklisting), Faculty Deans (approve blacklist entries), System (enforce blacklist restrictions)

#### 4.5.7 Damage and Maintenance
**FR-ACC-120:** The system shall record accommodation damages:
- Damage description
- Date discovered
- Room and student(s) involved
- Damage category
- Estimated cost
- Responsibility determination
- Billing for damages

**User Roles:** Hostel Wardens (record damages/assess responsibility), Accommodation Officers (review damage reports), Billing Officers (bill for damages), Students (dispute charges)

**FR-ACC-121:** The system shall track maintenance:
- Maintenance requests
- Room repair status
- Rooms out of service
- Maintenance completion date

**User Roles:** Hostel Wardens (submit maintenance requests), Accommodation Officers (coordinate maintenance), System (track status), Maintenance staff (update completion)

#### 4.5.8 Accommodation Reporting
**FR-ACC-140:** The system shall generate accommodation reports:
- Occupancy statistics by facility
- Vacant rooms report
- Waiting list report by priority
- Allocation by student type/programme
- Revenue by facility
- Damage reports
- Gender distribution
- Blacklist report

**User Roles:** Accommodation Officers (generate/review reports), Hostel Wardens (view facility reports), Finance Manager (review revenue reports), System Administrator (access all reports), Internal Audit (audit reports)

---

### 4.6 Dining Services Management

#### 4.6.1 Dining Hall Management
**FR-DIN-001:** The system shall manage dining halls:
- Hall name and location
- Capacity
- Operating hours
- Hall-to-hostel assignments
- Meal service times

**User Roles:** Dining Services Staff (manage halls), Accommodation Officers (coordinate hostel assignments), System Administrator (configure hall database)

**FR-DIN-002:** The system shall assign staff:
- Dining hall attendants
- Attendant schedules
- Attendant contact details

**User Roles:** Dining Services Staff (assign attendants/manage schedules), System Administrator (maintain staff database)

**FR-DIN-003:** The system shall configure meal options:
- Breakfast menu
- Lunch menu
- Dinner menu
- Special diet options (vegetarian, halal, allergies)

**User Roles:** Dining Services Staff (configure menus/options), System Administrator (manage meal configuration)

#### 4.6.2 Student Dining Assignment
**FR-DIN-020:** The system shall assign students to dining halls:
- Based on accommodation facility
- Based on programme/faculty
- Automatic assignment
- Manual override

**User Roles:** System (automatic assignment), Dining Services Staff (manual assignment/overrides), Accommodation Officers (coordinate assignments), Students (view assignments)

**FR-DIN-021:** The system shall track student dietary requirements:
- Vegetarian
- Vegan
- Halal
- Allergies and restrictions
- Medical diet requirements

**User Roles:** Students (submit dietary requirements), Dining Services Staff (manage requirements), System (track requirements/generate reports)

#### 4.6.3 Meal Attendance
**FR-DIN-040:** The system shall track meal attendance:
- Student check-in at meal times
- Meal type (breakfast, lunch, dinner)
- Date and time
- Dining hall
- Biometric/card swipe integration

**User Roles:** System (track attendance via biometric/card), Students (check in for meals), Dining Services Staff (monitor attendance/resolve issues)

**FR-DIN-041:** The system shall enforce meal eligibility:
- Student must be registered
- Student must have accommodation or meal plan
- Student assigned to correct dining hall
- Valid during meal service times

**User Roles:** System (enforce eligibility rules), Dining Services Staff (override eligibility/resolve issues), Accommodation Officers (verify accommodation status)

#### 4.6.4 Dining Reporting
**FR-DIN-060:** The system shall generate dining reports:
- Daily meal attendance
- Weekly consumption statistics
- Dining hall utilization
- Special diet tracking
- Cost analysis per student

**User Roles:** Dining Services Staff (generate/review reports), Finance Manager (review cost analysis), System Administrator (access all reports), Accommodation Officers (view related reports)

---

### 4.7 Staff and Departmental Management

#### 4.7.1 Staff Management
**FR-STF-001:** The system shall maintain staff records:
- Staff number
- Full names and title
- Department and faculty
- Position/role
- Contact information
- Qualifications
- Specialization areas

**User Roles:** System Administrator (create/edit staff records), Department Heads (view department staff), Faculty Deans (view faculty staff), Course Lecturers (view own record)

**FR-STF-002:** The system shall link staff to courses:
- Course instructor assignment
- Course coordinator designation
- Multiple instructors per course support
- Teaching load tracking

**User Roles:** Department Heads (assign instructors), Course Lecturers (view assignments), System Administrator (manage all assignments), Faculty Deans (review teaching loads)

#### 4.7.2 Department Management
**FR-DEP-001:** The system shall manage departments:
- Department name and code
- Faculty assignment
- Department head/chair
- Contact details
- Department office location

**User Roles:** System Administrator (manage departments), Faculty Deans (view/edit faculty departments), Department Heads (update department info)

**FR-DEP-002:** The system shall manage chairperson nominations:
- Nomination period
- Nominee details
- Election process
- Appointment date
- Term duration

**User Roles:** Faculty Deans (manage nominations/appointments), System Administrator (configure nomination process), Department staff (nominate candidates)

**FR-DEP-003:** The system shall configure department deadlines:
- Course approval deadlines
- Result submission deadlines
- Registration approval deadlines
- By semester

**User Roles:** Department Heads (set deadlines), Faculty Deans (approve deadlines), System Administrator (configure deadline rules), Examination Officers (coordinate deadlines)

#### 4.7.3 Faculty Management
**FR-FAC-001:** The system shall manage faculties:
- Faculty name and code
- Dean details
- Faculty office
- Sub-categories

**User Roles:** System Administrator (manage faculties), Faculty Deans (update faculty info), Registration Officers (view faculty structure)

**FR-FAC-002:** The system shall support faculty-specific rules:
- Grading scales
- Progression requirements
- Result weight configurations
- Decision rules

**User Roles:** Faculty Deans (configure faculty rules), System Administrator (manage rule structure), Examination Officers (apply rules), Department Heads (view rules)

---

### 4.8 Academic Planning and Curriculum

#### 4.8.1 Programme Management
**FR-PRG-001:** The system shall define programmes:
- Programme name and code
- Programme type (undergraduate, postgraduate, certificate, diploma)
- Faculty and department
- Duration (years/semesters)
- Mode of study (full-time, part-time, block, distance)
- Entry requirements
- Programme objectives
- Exit qualifications

**User Roles:** Faculty Deans (define/approve programmes), Department Heads (propose programmes), System Administrator (configure programme database), Registration Officers (manage programme records)

**FR-PRG-002:** The system shall configure programme structure:
- Levels/years
- Semesters per year
- Minimum and maximum credit requirements per level
- Total credits for graduation
- Compulsory courses
- Elective courses
- Specialisation options

**User Roles:** Faculty Deans (configure structure), Department Heads (recommend structure), System Administrator (set up structure parameters), Examination Officers (review structure)

**FR-PRG-003:** The system shall support programme requirements:
- Subject requirements (combinations)
- Credit accumulation rules
- Grade point average requirements
- Specific course prerequisites
- Graduation requirements

**User Roles:** Faculty Deans (set requirements), Department Heads (recommend requirements), System (enforce requirements), Registration Officers (verify compliance)

**FR-PRG-004:** The system shall manage programme capacity:
- Maximum enrollment per intake
- Resource availability
- Staff capacity

**User Roles:** Faculty Deans (set capacity), Department Heads (assess capacity), Admissions Officers (monitor enrollment), System Administrator (configure capacity limits)

#### 4.8.2 Course Management now Module Management
**FR-CRS-001:** The system shall define courses:
- Course code and name
- Course description
- Credit hours/units
- Course level (100, 200, 300, etc.)
- Department offering
- Contact hours (lectures, tutorials, practicals)

**User Roles:** Department Heads (define courses), Course Lecturers (propose courses), Faculty Deans (approve courses), System Administrator (manage course catalog)

**FR-CRS-002:** The system shall configure course prerequisites:
- Prerequisite courses required
- Corequisite courses
- Level restrictions
- Programme restrictions

**User Roles:** Department Heads (set prerequisites), Course Lecturers (recommend prerequisites), Faculty Deans (approve prerequisites), System (enforce prerequisites)

**FR-CRS-003:** The system shall set course requirements:
- Attendance requirements
- Assessment components (assignments, tests, exams)
- Component weights
- Pass marks
- Exam requirements (written, practical, oral)

**User Roles:** Course Lecturers (set requirements), Department Heads (approve requirements), Faculty Deans (review requirements), Examination Officers (coordinate assessment requirements)

**FR-CRS-004:** The system shall manage course offerings:
- Courses offered per semester
- Course capacity (maximum students)
- Multiple sections support
- Instructor assignment

**User Roles:** Department Heads (plan offerings/assign instructors), Course Lecturers (view assignments), Faculty Deans (approve offerings), Registration Officers (monitor capacity)

**FR-CRS-005:** The system shall support course amendments:
- Course code changes
- Course name changes
- Credit changes
- Prerequisite changes
- With effective dates and version control

**User Roles:** Department Heads (propose amendments), Faculty Deans (approve amendments), System Administrator (implement amendments), System (track version history)

#### 4.8.3 Specialisation Management
**FR-SPC-001:** The system shall define specialisations:
- Specialisation name and code
- Programme linkage
- Available from which level
- Required courses
- Elective courses
- Entry requirements

**User Roles:** Faculty Deans (define specialisations), Department Heads (propose specialisations), System Administrator (configure specialisations), Registration Officers (manage assignments)

**FR-SPC-002:** The system shall assign students to specialisations:
- Student choice submission
- Approval process
- Capacity management
- Change of specialisation

**User Roles:** Students (select specialisation), Department Heads (approve selections), Faculty Deans (review capacity), Registration Officers (process assignments)

#### 4.8.4 Subject Management and Programme Entry Requirements

##### 4.8.4.1 Subject Catalog Management
**FR-SUB-001:** The system shall manage a comprehensive subject catalog:
- Subject name (e.g., Physics, Mathematics, Chemistry, Biology)
- Subject code/identifier
- Subject category (Science, Arts, Commerce, Technical)
- Subject level (O-level, A-level, Degree-level)
- Subject description
- Active/inactive status
- Effective dates

**User Roles:** System Administrator (manage subject catalog), Faculty Deans (approve subjects), Department Heads (recommend subjects), Admissions Officers (use for admissions)

**FR-SUB-002:** The system shall categorize subjects by discipline:
- **Science subjects**: Mathematics, Physics, Chemistry, Biology, Computer Science, etc.
- **Arts subjects**: Literature, History, Geography, Languages, Religious Studies, etc.
- **Commerce subjects**: Accounts, Economics, Commerce, Business Studies, etc.
- **Technical subjects**: Technical Drawing, Engineering Science, Agriculture, etc.
- **General subjects**: English Language, Additional Mathematics, etc.

**User Roles:** System Administrator (define categories), Faculty Deans (assign subjects to categories), Admissions Officers (filter by category)

**FR-SUB-003:** The system shall manage subject hierarchies:
- Parent-child subject relationships (e.g., Pure Mathematics → Mathematics)
- Subject groups (e.g., Physical Sciences group includes Physics, Chemistry)
- Subject families (e.g., all Mathematics variants)
- Related subjects mapping

**User Roles:** System Administrator (configure hierarchies), Faculty Deans (define relationships), Department Heads (recommend groupings)

##### 4.8.4.2 O-Level Subject Requirements
**FR-SUB-010:** The system shall manage O-level subject requirements:
- Minimum number of O-level subjects required
- Required specific subjects (e.g., English, Mathematics)
- Required subject grades (e.g., minimum Grade C)
- Subject sitting restrictions (one sitting, two sittings allowed)
- Examination board preferences (ZIMSEC, Cambridge, etc.)

**User Roles:** Admissions Officers (configure requirements), Faculty Deans (set faculty standards), Department Heads (recommend requirements), System (validate applicants)

**FR-SUB-011:** The system shall support O-level subject grade requirements:
- Minimum grade per subject (A, B, C, D, E)
- Aggregate grade requirements (e.g., 5 O-levels at Grade C or better)
- Specific subject grade requirements (e.g., Mathematics Grade B minimum)
- Grade point calculation (A=1, B=2, C=3, etc.)
- Maximum grade point thresholds

**User Roles:** Admissions Officers (set grade requirements), Faculty Deans (approve requirements), System (calculate grade points/validate)

**FR-SUB-012:** The system shall handle O-level subject combinations:
- Acceptable subject combinations for different programmes
- Mandatory subjects (e.g., English + Mathematics)
- Optional subjects from specified list
- Minimum number from each category
- Incompatible subject combinations

**User Roles:** Faculty Deans (define combinations), Admissions Officers (configure rules), Department Heads (recommend combinations), System (validate combinations)

##### 4.8.4.3 A-Level Subject Requirements
**FR-SUB-020:** The system shall manage A-level subject requirements:
- Minimum number of A-level subjects (Principal + Subsidiary)
- Required principal subjects
- Required subsidiary subjects
- Acceptable subject combinations
- Minimum points per subject
- Total points threshold

**User Roles:** Admissions Officers (configure requirements), Faculty Deans (set standards), Department Heads (define subject needs), System (validate eligibility)

**FR-SUB-021:** The system shall support A-level subject combinations by programme:
- **Science programmes**: Require Mathematics, Physics, Chemistry combinations
- **Engineering programmes**: Mathematics, Physics, and one of Chemistry/Technical Drawing
- **Medical programmes**: Biology, Chemistry, and one of Physics/Mathematics
- **Business programmes**: Mathematics, Economics, and one other
- **Arts programmes**: Any three A-level subjects
- **Mixed programmes**: Specific combinations defined per programme

**User Roles:** Faculty Deans (define programme combinations), Department Heads (specify department requirements), Admissions Officers (configure rules), System (validate applicant subjects)

**FR-SUB-022:** The system shall calculate A-level points:
- Point allocation per grade (A=5, B=4, C=3, D=2, E=1)
- Principal subject weighting (may be worth more points)
- Subsidiary subject weighting (may be worth fewer points)
- Subject-specific point bonuses (e.g., extra points for Mathematics)
- Total points calculation
- Programme-specific point thresholds

**User Roles:** Admissions Officers (configure point system), System (calculate points), Faculty Deans (set thresholds), Examination Officers (verify calculations)

**FR-SUB-023:** The system shall support A-level subject alternatives:
- Alternative subjects accepted for specific programmes
- Subject equivalencies (e.g., Further Mathematics equivalent to Mathematics)
- Subject substitutions with approval
- Conditional acceptance with subject requirements
- Subject deficiency handling

**User Roles:** Faculty Deans (approve alternatives), Department Heads (define equivalencies), Admissions Officers (process substitutions), System (track alternatives)

##### 4.8.4.4 Programme-Subject Requirements
**FR-SUB-030:** The system shall link programmes to required subject combinations:
- Programme code and name
- Minimum O-level subjects with grades
- Minimum A-level subjects with points
- Compulsory subject requirements
- Alternative subject combinations (OR logic)
- Preferred subjects (bonus points)
- Excluded subjects (if any)

**User Roles:** Faculty Deans (define programme requirements), Department Heads (specify subject needs), Admissions Officers (configure requirements), System (enforce requirements)

**FR-SUB-031:** The system shall support multiple subject requirement sets per programme:
- **Standard entry requirements**: For typical applicants
- **Mature entry requirements**: For applicants over certain age
- **Transfer entry requirements**: For students transferring from other institutions
- **Diploma holder entry**: For diploma holders
- **International qualifications**: For non-standard qualifications
- **Special entry routes**: For exceptional cases

**User Roles:** Admissions Officers (configure requirement sets), Faculty Deans (approve sets), Department Heads (recommend sets), System (apply appropriate set)

**FR-SUB-032:** The system shall enforce subject prerequisites by programme level:
- Level 100 (Year 1) entry requirements
- Level 200 (Year 2) entry requirements for transfers
- Level 300 (Year 3) entry requirements for transfers
- Postgraduate entry requirements
- Professional programme entry requirements

**User Roles:** Faculty Deans (set level requirements), Department Heads (define prerequisites), Registration Officers (verify prerequisites), System (validate entry eligibility)

##### 4.8.4.5 Subject Weighting and Scoring
**FR-SUB-040:** The system shall support subject-specific weighting:
- Base weight for all subjects (e.g., 1.0)
- Enhanced weight for critical subjects (e.g., Mathematics = 1.5)
- Reduced weight for less relevant subjects (e.g., 0.5)
- Programme-specific subject weights
- Faculty-specific subject weights
- Variable weighting by grade achieved

**User Roles:** Admissions Officers (configure weights), Faculty Deans (set faculty weights), Department Heads (recommend weights), System (apply weights in calculations)

**FR-SUB-041:** The system shall calculate applicant scores based on subjects:
- O-level score calculation with subject weights
- A-level points calculation with subject weights
- Combined O-level and A-level score
- Programme-specific scoring formulas
- Bonus points for relevant subjects
- Penalty for missing critical subjects
- Normalized scoring across different qualification types

**User Roles:** System (perform calculations), Admissions Officers (configure formulas), Faculty Deans (set scoring rules), Internal Audit (verify calculations)

**FR-SUB-042:** The system shall rank applicants by subject-weighted scores:
- Overall score ranking per programme
- Programme choice priority consideration
- Tie-breaking rules based on specific subjects
- Merit list generation by score
- Quota consideration in ranking
- Selection threshold determination

**User Roles:** System (generate rankings), Admissions Officers (review rankings), Faculty Deans (approve selections), Department Heads (verify subject relevance)

##### 4.8.4.6 Subject Combination Validation
**FR-SUB-050:** The system shall validate subject combinations for applicants:
- Check compulsory subjects are present
- Verify minimum number of subjects met
- Validate subject combination rules (AND/OR logic)
- Check for incompatible subject combinations
- Verify subject grades meet minimum requirements
- Calculate total points/scores
- Generate validation report with pass/fail reasons

**User Roles:** System (automatic validation), Admissions Officers (review validations), Department Heads (investigate failures), Faculty Deans (approve exceptions)

**FR-SUB-051:** The system shall handle subject combination exceptions:
- Manual override for near-miss cases
- Special consideration for exceptional applicants
- Alternative combination approval workflow
- Subject deficiency conditional offers
- Bridging course requirements
- Supplementary subject requirements

**User Roles:** Admissions Officers (identify exceptions), Faculty Deans (approve exceptions), Department Heads (recommend exceptions), System (track exceptions)

**FR-SUB-052:** The system shall provide subject combination feedback:
- Real-time validation as applicants enter subjects
- Clear error messages for invalid combinations
- Suggestions for valid alternative combinations
- Programme recommendations based on subjects
- Subject gap identification
- Advice on additional subjects needed

**User Roles:** System (provide feedback), Applicants (receive feedback), Admissions Officers (configure messages)

##### 4.8.4.7 Subject Equivalencies and Conversions
**FR-SUB-060:** The system shall manage subject equivalencies:
- Equivalent subjects across examination boards (ZIMSEC, Cambridge, etc.)
- Equivalent subjects across qualification types (O-level, IGCSE, etc.)
- Subject name variations (e.g., "Maths" = "Mathematics")
- International qualification equivalencies
- Professional qualification equivalencies
- Subject credit transfer equivalencies

**User Roles:** Admissions Officers (configure equivalencies), System Administrator (maintain equivalency tables), Faculty Deans (approve equivalencies), Registration Officers (apply equivalencies)

**FR-SUB-061:** The system shall convert international qualifications:
- Grade conversion tables (e.g., IGCSE A* to O-level A)
- Point conversion for different systems
- Subject mapping for international qualifications
- Credit hour conversions
- Qualification level mapping
- Automatic conversion where possible, manual review for exceptions

**User Roles:** Admissions Officers (process conversions), System (apply conversion tables), Faculty Deans (approve conversions), International Office (verify qualifications)

**FR-SUB-062:** The system shall track qualification verification:
- Verification status per subject
- Verification source (examination board, institution)
- Verification date
- Verified by (user)
- Discrepancies and resolutions
- Fraudulent qualification flagging

**User Roles:** Admissions Officers (verify qualifications), System (track verification), Internal Audit (review verifications), External verifiers (provide verification)

##### 4.8.4.8 Subject-Based Programme Recommendations
**FR-SUB-070:** The system shall recommend programmes based on subjects:
- Analyze applicant's subject combination
- Match subjects to programme requirements
- Calculate fit percentage for each programme
- Rank programmes by suitability
- Identify best-fit programmes
- Suggest alternative programmes if primary choice unsuitable

**User Roles:** System (generate recommendations), Applicants (view recommendations), Admissions Officers (configure recommendation algorithms), Career counselors (interpret recommendations)

**FR-SUB-071:** The system shall support subject-based career guidance:
- Subject-to-career pathway mapping
- Programme-to-career outcome mapping
- Industry demand for subject combinations
- Employment statistics by subject combination
- Subject prerequisite chains for advanced programmes
- Subject skills mapping

**User Roles:** System Administrator (configure pathways), Career counselors (provide guidance), Admissions Officers (advise applicants), Students/Applicants (explore pathways)

##### 4.8.4.9 Subject Requirement Reporting
**FR-SUB-080:** The system shall generate subject-related reports:
- Programme entry requirements summary by faculty
- Subject combination statistics by programme
- Applicant subject profile analysis
- Subject deficiency reports
- Subject-based admission trends
- Subject demand analysis
- Grade distribution by subject
- Subject performance correlations with academic success

**User Roles:** Admissions Officers (generate reports), Faculty Deans (review faculty reports), Department Heads (analyze subject trends), System Administrator (access all reports), Management (strategic planning)

**FR-SUB-081:** The system shall provide subject requirement documentation:
- Programme prospectus generation with subject requirements
- Admission brochure subject requirement tables
- Online programme finder with subject filters
- Subject requirement comparison across programmes
- Subject requirement change history
- Subject requirement version control

**User Roles:** Admissions Officers (generate documentation), Marketing staff (use in publications), System (maintain versions), Applicants (access information)

##### 4.8.4.10 Subject Data Integrity
**FR-SUB-090:** The system shall maintain subject data quality:
- Subject name standardization
- Duplicate subject detection
- Subject merge capabilities
- Subject archival (for discontinued subjects)
- Subject usage tracking (where used in requirements)
- Impact analysis before subject changes
- Subject requirement validation

**User Roles:** System Administrator (maintain data quality), Admissions Officers (report issues), System (detect duplicates/validate), Internal Audit (verify integrity)

**FR-SUB-091:** The system shall support subject requirement versioning:
- Track changes to subject requirements over time
- Effective date management for requirement changes
- Historical requirement retrieval
- Applicant qualification under rules at time of application
- Grandfather clause support
- Requirement change notification

**User Roles:** System Administrator (manage versions), Admissions Officers (update requirements), Faculty Deans (approve changes), System (apply correct version), Internal Audit (track changes)

---

### 4.9 Examination Administration

#### 4.9.1 Exam Session Configuration
**FR-EXS-001:** The system shall configure exam sessions:
- Session name and code
- Academic year and semester
- Exam period (start and end dates)
- Exam type (final, supplementary, special, deferred)
- Registration deadline

**User Roles:** Examination Officers (configure sessions), Faculty Deans (approve exam periods), System Administrator (set up session structure)

**FR-EXS-002:** The system shall define exam test types:
- Test type name (assignment, mid-term, final, practical)
- Weight/contribution to final grade
- Maximum marks
- Test date and time

**User Roles:** Examination Officers (define test types), Course Lecturers (request test types), Department Heads (approve test types), Faculty Deans (set faculty standards)

#### 4.9.2 Exam Scheduling
**FR-EXS-020:** The system shall create master timetable:
- Course exam dates and times
- Duration per course
- Venue allocation
- Programme grouping
- Conflict detection

**User Roles:** Examination Officers (create timetable), System (automatic scheduling/conflict detection), Faculty Deans (approve timetable), Department Heads (review department schedule)

**FR-EXS-021:** The system shall generate student timetables:
- Extract courses for each student
- Combine into personal timetable
- Show date, time, venue, duration
- Export to PDF
- Email/print/SMS delivery

**User Roles:** System (generate timetables), Students (view/download timetables), Examination Officers (verify timetables), Registration Officers (coordinate distribution)

**FR-EXS-022:** The system shall support exam slot tracking:
- Track timeslots used
- Programme-specific slots
- Resource allocation per slot

**User Roles:** Examination Officers (track slots), System (monitor slot usage), Faculty Deans (review slot allocation)

#### 4.9.3 Exam Venue Allocation
**FR-EXS-040:** The system shall allocate exam venues:
- Based on course enrollment
- Venue capacity constraints
- Programme/level grouping
- Accessibility requirements
- Equipment needs (labs for practical exams)

**User Roles:** Examination Officers (allocate venues), System (automatic allocation), Accommodation Officers (coordinate venue access), Department Heads (review allocations)

**FR-EXS-041:** The system shall generate venue lists:
- Students per venue
- Seating plan
- Invigilator assignment
- Special requirements

**User Roles:** Examination Officers (generate lists), Invigilators (receive lists), Course Lecturers (view course venue lists)

**FR-EXS-042:** The system shall track venue occupancy:
- Real-time capacity usage
- Over-booking detection
- Venue utilization reports

**User Roles:** Examination Officers (monitor occupancy), System (detect over-booking/generate alerts), Faculty Deans (review utilization)

#### 4.9.4 Invigilator Management
**FR-EXS-060:** The system shall assign invigilators:
- Staff invigilator pool
- Invigilator per venue
- Invigilator schedules
- Conflict detection (staff teaching elsewhere)

**User Roles:** Examination Officers (assign invigilators), Course Lecturers (participate as invigilators), System (detect conflicts), Department Heads (coordinate staff availability)

#### 4.9.5 Exam Materials
**FR-EXS-080:** The system shall manage exam papers:
- Paper submission by instructors
- Moderation and approval
- Secure storage
- Printing instructions
- Distribution tracking

**User Roles:** Course Lecturers (submit papers), Department Heads (moderate papers), Examination Officers (approve/manage distribution), Faculty Deans (final approval)

---

### 4.10 Awards and Recognition

#### 4.10.1 Awards Management
**FR-AWD-001:** The system shall manage university awards:
- Award name and description
- Award category
- Eligibility criteria
- Award value/prize
- Sponsor details

**User Roles:** System Administrator (manage awards), Faculty Deans (recommend awards), Registration Officers (administer awards), Finance Manager (manage award budgets)

**FR-AWD-002:** The system shall process award nominations:
- Nomination submission
- Nominee details
- Nomination justification
- Supporting documents
- Selection committee review

**User Roles:** Faculty Deans (nominate students), Course Lecturers (submit nominations), Selection Committee (review nominations), System Administrator (manage nomination process)

**FR-AWD-003:** The system shall record award recipients:
- Student awardee
- Award received
- Date awarded
- Award ceremony details

**User Roles:** Registration Officers (record recipients), Faculty Deans (approve recipients), System (track award history), Students (view awards received)

---

### 4.11 Data Management and Integration

#### 4.11.1 Data Import/Export
**FR-DAT-001:** The system shall support bulk data import:
- Excel file upload
- CSV file import
- Data validation
- Error reporting
- Rollback on failure

**User Roles:** System Administrator (import data), Registration Officers (import student data), Admissions Officers (import applicant data), Internal Audit (verify imports)

**FR-DAT-002:** The system shall support data export:
- Export to Excel
- Export to CSV
- Export to PDF
- Filtered export by criteria

**User Roles:** System Administrator (export all data), Registration Officers (export reports), Faculty Deans (export faculty data), Finance Manager (export financial data), Internal Audit (export audit data)

#### 4.11.2 Previous Student Data
**FR-DAT-020:** The system shall import previous student records:
- From legacy systems
- Student history preservation
- Course equivalency mapping
- Credit transfer
- Integration with Sybase/SQL Server databases

**User Roles:** System Administrator (manage legacy integration), Registration Officers (verify imported data), Department Heads (verify course equivalencies), Internal Audit (audit imports)

#### 4.11.3 Archive Management
**FR-DAT-040:** The system shall archive old records:
- Graduated students
- Withdrawn students
- Old invoices and payments
- Old accommodation records
- Archival retention policy
- Archive search and retrieval

**User Roles:** System Administrator (manage archives), Internal Audit (access archives), Registration Officers (archive student records), Finance Manager (archive financial records), System (automatic archival)

---

### 4.12 Communications

#### 4.12.1 Email Notifications
**FR-COM-001:** The system shall send automated emails for:
- Registration confirmation
- Exam timetable release
- Result publication
- Invoice generation
- Payment receipt
- Accommodation allocation
- Accommodation reminders
- General announcements

**User Roles:** System (send automated emails), System Administrator (configure email settings), Registration Officers (send manual notifications), Examination Officers (send exam notifications), Students (receive emails)

**FR-COM-002:** The system shall support email templates:
- Customizable templates
- Merge fields (student name, etc.)
- HTML formatting
- Attachment support

**User Roles:** System Administrator (manage templates), Registration Officers (customize templates), Faculty Deans (approve templates), System (populate merge fields)

#### 4.12.2 SMS Notifications
**FR-COM-020:** The system shall send SMS for:
- Result publication alerts
- Payment confirmations
- Registration reminders
- Urgent announcements

**User Roles:** System (send SMS automatically), System Administrator (configure SMS gateway), Registration Officers (send manual SMS), Students (receive SMS)

**FR-COM-021:** The system shall integrate with SMS gateway:
- Bulk SMS sending
- Delivery reports
- Cost tracking

**User Roles:** System Administrator (configure gateway/monitor costs), System (send bulk SMS/track delivery), Finance Manager (review SMS costs)

---

### 4.13 Student Self-Service Portal

#### 4.13.1 Student Portal Features
**FR-PRT-001:** Students shall be able to:
- View personal profile
- View current registration
- View exam timetable
- View results and transcript
- View financial statement
- View accommodation status
- Download receipts
- Download invoices
- Update contact information
- Change password

**User Roles:** Students (access all portal features), System (provide portal access), Registration Officers (support portal issues), System Administrator (configure portal)

**FR-PRT-002:** The portal shall require authentication:
- Student number
- Password
- Forgot password functionality
- Session timeout

**User Roles:** System (authenticate users/manage sessions), Students (login), Registration Officers (reset passwords/unlock accounts), System Administrator (configure authentication settings)

---

### 4.14 Reporting and Analytics

#### 4.14.1 Standard Reports
**FR-REP-001:** The system shall provide standard reports:
- Enrollment statistics (by faculty, programme, level, gender, intake)
- Examination statistics (pass rates, grade distribution)
- Financial reports (billing, collections, outstanding debt)
- Accommodation occupancy
- Dining statistics
- Application statistics
- Award listings

**User Roles:** System Administrator (access all reports), Faculty Deans (view faculty reports), Department Heads (view department reports), Finance Manager (view financial reports), Registration Officers (generate student reports), Examination Officers (generate exam reports), Accommodation Officers (generate accommodation reports)

**FR-REP-002:** The system shall support report filtering:
- By academic year
- By semester
- By faculty
- By programme
- By level
- By student type
- Date ranges

**User Roles:** All report users (apply filters), System Administrator (configure filter options), System (execute filtered queries)

**FR-REP-003:** The system shall support report export:
- PDF format
- Excel format
- Print-ready formatting
- Email delivery

**User Roles:** All report users (export reports), System (generate exports in various formats), System Administrator (configure export settings)

#### 4.14.2 Dashboards
**FR-REP-020:** The system shall provide analytical dashboards:
- Registration progress dashboard
- Financial performance dashboard
- Examination dashboard
- Accommodation dashboard

**User Roles:** System Administrator (access all dashboards), Faculty Deans (view faculty dashboards), Finance Manager (view financial dashboards), Registration Officers (view registration dashboards), Examination Officers (view exam dashboards), Accommodation Officers (view accommodation dashboards)

**FR-REP-021:** Dashboards shall include visualizations:
- Charts (bar, line, pie)
- Trend analysis
- Comparison graphs
- Summary statistics

**User Roles:** System (generate visualizations), All dashboard users (view visualizations), System Administrator (configure dashboard layouts)

---

### 4.15 Configuration and System Settings

#### 4.15.1 Academic Calendar
**FR-CFG-001:** The system shall manage academic calendar:
- Academic years
- Semesters/periods
- Intake dates
- Registration periods
- Exam periods
- Holiday periods
- Graduation dates

**User Roles:** System Administrator (configure calendar), Registration Officers (coordinate calendar), Examination Officers (set exam periods), Faculty Deans (approve calendar), System (enforce calendar rules)

**FR-CFG-002:** The system shall define cut-off dates:
- Registration cut-off
- Course add/drop deadlines
- Fee payment deadlines
- Result submission deadlines
- Accommodation application deadlines

**User Roles:** System Administrator (set system-wide deadlines), Registration Officers (set registration deadlines), Examination Officers (set exam deadlines), Finance Manager (set payment deadlines), Accommodation Officers (set accommodation deadlines), System (enforce deadlines)

#### 4.15.2 System Parameters
**FR-CFG-020:** The system shall support configuration of:
- Grading scales
- Pass marks
- GPA calculation methods
- Academic progression rules
- Financial hold thresholds
- Student number formats
- Invoice number formats
- Receipt number formats

**User Roles:** System Administrator (configure all parameters), Faculty Deans (recommend grading parameters), Finance Manager (set financial parameters), Examination Officers (configure assessment parameters), System (apply parameters)

#### 4.15.3 Approval Workflows

##### 4.15.3.1 General Workflow Configuration
**FR-CFG-040:** The system shall support configurable approval workflows with:
- Multiple approval stages/levels
- Sequential approval flow
- Parallel approval flow
- Conditional approval routing
- Escalation rules
- Timeout handling
- Automatic reminders

**User Roles:** System Administrator (configure workflow engine), Faculty Deans (define faculty-specific workflows), Finance Manager (define financial workflows), Department Heads (define department workflows)

**FR-CFG-041:** The system shall track workflow status:
- Pending initiation
- In progress
- Awaiting approval at specific stage
- Approved
- Rejected
- Cancelled
- Expired

**User Roles:** System (track status/send notifications), All workflow participants (view status), System Administrator (monitor all workflows), Internal Audit (audit workflow history)

**FR-CFG-042:** The system shall maintain workflow audit trail:
- Workflow initiation date/time and user
- Each approval/rejection action with date/time and user
- Comments/reasons at each stage
- Workflow completion date/time
- Workflow duration metrics
- Version history of workflow changes

**User Roles:** System (automatic logging), Internal Audit (review audit trails), System Administrator (access all audit data), Workflow participants (view workflow history)

##### 4.15.3.2 Registration Approval Workflow
**FR-CFG-050:** The system shall support multi-level registration approval workflow:
- **Level 1**: Student submits course registration
- **Level 2**: Department reviews and approves/rejects courses
- **Level 3**: Faculty reviews registration (if required)
- **Level 4**: Chairperson confirms registration (if required)
- **Level 5**: System finalizes registration and generates invoice

**User Roles:** Students (initiate workflow), Department Heads (Level 2 approval), Faculty Deans (Level 3 approval), Department Chairs (Level 4 approval), Registration Officers (monitor/override), System (finalize registration)

**FR-CFG-051:** The registration workflow shall support:
- Automatic approval for standard registrations
- Conditional routing based on student status (probation, new, returning)
- Conditional routing based on course load (overload, underload)
- Approval delegation when primary approver unavailable
- Bulk approval capabilities
- Deadline enforcement with auto-rejection

**User Roles:** System Administrator (configure workflow rules), Department Heads (configure department approval criteria), Faculty Deans (set faculty approval thresholds), Registration Officers (manage workflow exceptions)

**FR-CFG-052:** The registration workflow shall handle exceptions:
- Late registration routing
- Course capacity override requests
- Prerequisite waiver requests
- Financial hold override requests
- Special approval for non-standard registrations

**User Roles:** Registration Officers (process exceptions), Faculty Deans (approve exceptions), System Administrator (configure exception rules), Finance Manager (approve financial overrides)

##### 4.15.3.3 Programme Change Approval Workflow
**FR-CFG-060:** The system shall support programme change approval workflow:
- **Stage 1**: Student initiates programme change request with justification
- **Stage 2**: Current department reviews and recommends
- **Stage 3**: Target department reviews and accepts/rejects
- **Stage 4**: Current faculty approves/rejects
- **Stage 5**: Target faculty approves/rejects (if different)
- **Stage 6**: Registration office processes credit transfer evaluation
- **Stage 7**: Final approval and programme change execution

**User Roles:** Students (initiate request), Department Heads (Stage 2 & 3 reviews), Faculty Deans (Stage 4 & 5 approvals), Registration Officers (Stage 6 processing and final execution), System (workflow routing/tracking)

**FR-CFG-061:** The programme change workflow shall validate:
- Student eligibility (minimum credits completed, GPA requirements)
- Available capacity in target programme
- Credit transfer feasibility
- Financial clearance
- Outstanding academic obligations

**User Roles:** System (automatic validation), Registration Officers (manual verification), Department Heads (verify eligibility), Faculty Deans (authorize exceptions)

**FR-CFG-062:** The programme change workflow shall handle:
- Within-faculty transfers (simplified workflow)
- Cross-faculty transfers (full workflow)
- Programme type changes (e.g., full-time to part-time)
- Specialisation changes within same programme
- Appeals process for rejected changes

**User Roles:** Registration Officers (route appropriately), Faculty Deans (handle appeals), System Administrator (configure routing rules), Students (submit appeals)

##### 4.15.3.4 Financial Waiver Approval Workflow
**FR-CFG-070:** The system shall support fee waiver approval workflow:
- **Stage 1**: Student submits waiver request with supporting documentation
- **Stage 2**: Faculty Dean reviews and recommends
- **Stage 3**: Finance Manager reviews financial impact
- **Stage 4**: Final approval by authorized signatory
- **Stage 5**: Billing Office applies waiver to student account

**User Roles:** Students (submit requests), Faculty Deans (Stage 2 recommendation), Finance Manager (Stage 3 review and Stage 4 approval), Billing Officers (Stage 5 application), System (workflow management)

**FR-CFG-071:** The fee waiver workflow shall support different waiver types:
- **Full waiver**: Requires highest level approval
- **Partial waiver**: May have delegated approval limits
- **Scholarship waiver**: Pre-approved sponsor validation
- **Hardship waiver**: Social welfare documentation required
- **Staff dependent waiver**: Automatic with HR verification
- **Merit-based waiver**: Academic performance validation

**User Roles:** System Administrator (configure waiver types/approval levels), Finance Manager (set approval limits), Faculty Deans (recommend merit waivers), HR staff (verify staff dependents), Students (provide documentation)

**FR-CFG-072:** The fee waiver workflow shall track:
- Total waiver amount by student
- Cumulative waivers by waiver type
- Waiver budget utilization
- Waiver approval rates
- Sponsor obligations and payments

**User Roles:** Finance Manager (monitor budgets), System (calculate totals), Internal Audit (review waiver usage), Billing Officers (track sponsor payments)

##### 4.15.3.5 Refund Approval Workflow
**FR-CFG-080:** The system shall support refund approval workflow:
- **Stage 1**: Student/Staff submits refund request with reason
- **Stage 2**: Billing Officer verifies eligibility and calculates refund amount
- **Stage 3**: Finance Manager reviews and approves/rejects
- **Stage 4**: Accounts Staff processes refund payment
- **Stage 5**: System updates student account

**User Roles:** Students (submit requests), Billing Officers (Stage 2 verification), Finance Manager (Stage 3 approval), Accounts Staff (Stage 4 payment processing), System (Stage 5 account update)

**FR-CFG-081:** The refund workflow shall handle different refund scenarios:
- **Overpayment refunds**: Automatic approval up to certain limit
- **Withdrawal refunds**: Pro-rated based on refund policy and withdrawal date
- **Programme change refunds**: Based on fee differential
- **Duplicate payment refunds**: Require payment verification
- **Accommodation forfeit refunds**: Partial refund based on timing
- **Error correction refunds**: Require error documentation

**User Roles:** System Administrator (configure refund policies/approval limits), Finance Manager (approve non-standard refunds), Billing Officers (calculate refund amounts), Internal Audit (review refund patterns)

**FR-CFG-082:** The refund workflow shall enforce refund policies:
- Refund eligibility deadlines
- Non-refundable fee categories
- Refund percentage schedules
- Minimum refund amounts
- Refund method restrictions
- Documentation requirements

**User Roles:** Finance Manager (set policies), System (enforce policies), Billing Officers (apply policies), System Administrator (configure policy rules)

##### 4.15.3.6 Accommodation Allocation Approval Workflow
**FR-CFG-090:** The system shall support accommodation allocation approval workflow:
- **Stage 1**: System automatically allocates based on priority ranking
- **Stage 2**: Accommodation Officer reviews automatic allocations
- **Stage 3**: Manual allocations for special cases
- **Stage 4**: Hostel Warden reviews facility-specific allocations
- **Stage 5**: Final approval and notification to students

**User Roles:** System (Stage 1 automatic allocation), Accommodation Officers (Stage 2 review and Stage 3 manual allocation), Hostel Wardens (Stage 4 facility review), System (Stage 5 notifications)

**FR-CFG-091:** The accommodation workflow shall support approval for:
- **Standard allocations**: Automatic approval based on priority
- **Special needs allocations**: Manual review required
- **Out-of-turn allocations**: Senior management approval
- **Off-campus accommodation**: Additional verification
- **Room upgrades**: Based on availability and payment
- **Blacklist exceptions**: Faculty Dean approval required

**User Roles:** Accommodation Officers (process special cases), Hostel Wardens (verify facilities), Faculty Deans (approve exceptions), System Administrator (configure allocation rules)

**FR-CFG-092:** The accommodation workflow shall handle waiting list management:
- Automatic notification when rooms become available
- Priority-based offer workflow
- Response deadline enforcement
- Automatic reallocation if no response
- Appeals process for allocation decisions

**User Roles:** System (automatic notifications/reallocation), Accommodation Officers (manage waiting list), Students (respond to offers), Faculty Deans (handle appeals)

##### 4.15.3.7 Result Moderation Approval Workflow
**FR-CFG-100:** The system shall support result moderation approval workflow:
- **Stage 1**: Course Lecturer submits results
- **Stage 2**: Department Head reviews results for consistency
- **Stage 3**: Faculty moderation committee reviews results
- **Stage 4**: External examiner reviews (if applicable)
- **Stage 5**: Faculty Dean approves results for publication
- **Stage 6**: Examination Officer publishes results

**User Roles:** Course Lecturers (Stage 1 submission), Department Heads (Stage 2 review), Faculty moderation committee (Stage 3 review), External Examiners (Stage 4 review), Faculty Deans (Stage 5 approval), Examination Officers (Stage 6 publication)

**FR-CFG-101:** The result moderation workflow shall support:
- Statistical analysis reports (mean, standard deviation, pass rates)
- Grade distribution comparisons
- Historical trend analysis
- Outlier detection and flagging
- Moderation recommendations
- Result adjustment proposals with justification

**User Roles:** System (generate analysis), Department Heads (review statistics), Faculty Deans (approve adjustments), Examination Officers (coordinate moderation), Internal Audit (monitor adjustments)

**FR-CFG-102:** The result moderation workflow shall handle:
- Individual result corrections
- Bulk result adjustments (scaling)
- Missing result reminders
- Late result submission approvals
- Result appeal workflow integration
- Grade change authorizations

**User Roles:** Course Lecturers (submit corrections), Department Heads (approve corrections), Faculty Deans (authorize bulk changes), Examination Officers (process changes), System (track all modifications)

##### 4.15.3.8 General Workflow Features
**FR-CFG-110:** All workflows shall support:
- Email notifications at each stage
- SMS notifications for critical actions
- Mobile-friendly approval interfaces
- Batch approval capabilities
- Approval delegation during leave
- Workflow analytics and reporting
- SLA monitoring and alerts

**User Roles:** System (send notifications/monitor SLAs), All approvers (receive notifications/delegate), System Administrator (configure notifications/SLAs), Management (review analytics)

**FR-CFG-111:** All workflows shall provide:
- Dashboard showing pending approvals
- Overdue approval alerts
- Workflow bottleneck identification
- Approval time metrics
- Rejection rate analysis
- User performance metrics

**User Roles:** All approvers (view dashboards), System Administrator (access all metrics), Management (review performance), Internal Audit (analyze patterns)

**FR-CFG-112:** All workflows shall enforce:
- Role-based access control at each stage
- Conflict of interest prevention (cannot approve own requests)
- Dual authorization for high-value transactions
- Approval limits by role/position
- Mandatory comments for rejections
- Complete audit trail

**User Roles:** System (enforce rules), System Administrator (configure rules), Internal Audit (verify enforcement), All workflow participants (comply with rules)

---

## 5. Data Models and Entities

### 5.1 Core Entities

#### 5.1.1 Student Domain
- **StudentDetail**: Core student demographic and biographical information
- **StudentRegister**: Student course registration records per semester
- **StudentAssignment**: Student programme and level assignments
- **StudentExamResult**: Individual exam results and grades
- **StudentOverallDecision**: Academic progression decisions per level
- **StudentProgramme**: Student-programme linkage and history
- **StudentDetailsHistory**: Audit trail of student record changes
- **StudentIdCard**: ID card issuance and tracking
- **StudentWeight**: Academic performance weights and calculations
- **StudentSubject**: Subject selections and specializations

#### 5.1.2 Academic Domain
- **Programme**: Academic programmes offered
- **Course**: Individual course definitions
- **Subject**: Subject areas and prerequisites
- **Specialisation**: Programme specialisations
- **ProgrammeChoice**: Student programme preferences
- **CourseAssignment**: Course-programme linkages
- **ProgrammeRequirement**: Academic requirements per programme
- **Semester**: Academic semester definitions
- **Period**: Academic periods
- **Intake**: Admission intake periods

#### 5.1.3 Examination Domain
- **ExamSession**: Exam session definitions
- **ExamMasterTimetable**: Master exam schedule
- **ExamStudentTimetable**: Individual student exam schedules
- **ExamVenue**: Exam venue definitions
- **ExamTestType**: Types of assessments
- **ExamDuration**: Exam duration by course
- **ExamCourse**: Course-exam linkage
- **CourseResult**: Aggregated course results

#### 5.1.4 Financial Domain
- **StudentInvoice**: Student billing invoices
- **InvoiceLineItem**: Individual invoice charges
- **StudentPayment**: Payment records
- **Receipt**: Payment receipts
- **Fee**: Fee structure definitions
- **ProgrammeFee**: Programme-specific fees
- **CourseFee**: Course-specific fees
- **RefundJournal**: Refund transactions
- **GeneralLedger**: GL accounts
- **GlRecord**: GL transaction records
- **StudentAccountAdjustment**: Account adjustments

#### 5.1.5 Accommodation Domain
- **Room**: Room definitions
- **RoomAllocation**: Student room assignments
- **RoomOccupancy**: Current room occupancy
- **HallOfResidence**: Accommodation facilities
- **AccommodationWaitingList**: Waiting list entries
- **AccommodationGroup**: Allocation priority groups
- **AccomodationSwap**: Room swap requests
- **AccomodationBlacklist**: Blacklisted students
- **AccomodationDamage**: Damage records
- **AccommodationFacility**: Facility definitions
- **AccommodationPremise**: Building/premise details
- **RoomType**: Room type definitions

#### 5.1.6 Dining Domain
- **DiningHall**: Dining facility definitions
- **StudentDiningHall**: Student-dining hall assignments
- **StudentDiet**: Dietary requirements
- **DiningMealCheck**: Meal attendance records
- **MealOption**: Available meal options
- **MealTypeTime**: Meal service times

#### 5.1.7 Applicant Domain
- **ApplicantsDetail**: Applicant personal information
- **ApplicantOlevelQualification**: O-level results
- **ApplicantAlevelQualification**: A-level results
- **ApplicantAcademicRecord**: Other academic qualifications
- **ApplicantReferee**: Reference information
- **OfferLetter**: Offer letter records
- **ApplicantPayment**: Application fee payments
- **ApplicantsPoint**: Calculated applicant points

#### 5.1.8 Staff and Organization Domain
- **StaffDetail**: Staff information
- **Department**: Department definitions
- **Faculty**: Faculty definitions
- **CourseInstructor**: Course-instructor assignments
- **DepartmentChairperson**: Department leadership

#### 5.1.9 User Management Domain
- **User**: System user accounts
- **SystemGroup**: User role groups
- **LoginHistory**: User login audit trail

#### 5.1.10 Award Domain
- **Award**: Award definitions
- **AwardNominee**: Award nominees
- **AwardSponsor**: Award sponsors
- **UniversityAward**: University award types
- **StudentUniversityAward**: Student award recipients

### 5.2 Relationship Summary

#### Key Relationships:
- **Student ↔ Programme**: Many-to-many through StudentAssignment
- **Student ↔ Course**: Many-to-many through StudentRegister
- **Programme ↔ Course**: Many-to-many through CourseAssignment
- **Student ↔ Invoice**: One-to-many
- **Invoice ↔ Payment**: Many-to-many
- **Student ↔ Room**: Many-to-many through RoomAllocation (temporal)
- **Course ↔ ExamSession**: Many-to-many
- **Student ↔ ExamResult**: Many-to-many through StudentExamResult
- **Applicant → Student**: One-to-one transformation
- **Student ↔ DiningHall**: Many-to-one

---

## 6. Core Business Workflows

### 6.1 Applicant-to-Student Pipeline

```
1. Application Submission
   ├─ Applicant creates account
   ├─ Completes personal details
   ├─ Enters O-level qualifications
   ├─ Enters A-level qualifications
   ├─ Selects programme choices (1st, 2nd, 3rd)
   ├─ Uploads supporting documents
   └─ Pays application fee

2. Application Assessment
   ├─ Admissions office verifies data
   ├─ System calculates applicant points
   ├─ Qualifications validated against requirements
   └─ Application marked as complete

3. Selection Process
   ├─ Selection committee reviews applications
   ├─ System ranks by points and criteria
   ├─ Faculty-specific selection rules applied
   ├─ Quota allocations considered
   └─ Applicants selected for offer

4. Offer Letter Generation
   ├─ System generates offer letter
   ├─ Programme and intake specified
   ├─ Conditions stated
   ├─ Offer dispatched (email/postal)
   └─ Acceptance deadline set

5. Offer Response
   ├─ Applicant accepts offer
   └─ Applicant declines offer (frees slot)

6. Student Record Creation
   ├─ Accepted applicant converted to student
   ├─ Student number assigned
   ├─ Student profile created
   └─ Programme assignment recorded

7. Initial Registration
   ├─ Student registers for first time
   ├─ Courses assigned (compulsory)
   ├─ Invoice generated
   └─ Student becomes active
```

### 6.2 Student Registration Workflow

```
1. Pre-Registration Setup
   ├─ Registration periods defined
   ├─ Late registration fees configured
   ├─ Course offerings confirmed
   └─ Registration window opened

2. Student Initiates Registration
   ├─ Student logs into portal
   ├─ Selects courses for semester
   ├─ Compulsory courses auto-added
   ├─ Elective courses selected
   └─ Submits for approval

3. Eligibility Validation
   ├─ System checks financial clearance
   ├─ System checks previous level completion
   ├─ System checks course prerequisites
   ├─ System checks course capacity
   └─ System checks timetable conflicts

4. Department Approval
   ├─ Department reviews course selection
   ├─ Ensures courses align with programme
   ├─ Approves or rejects courses
   └─ Student notified of approval status

5. Faculty Review (if required)
   ├─ Faculty reviews registrations
   ├─ Checks compliance with regulations
   └─ Approves batch registrations

6. Chair Confirmation (if required)
   ├─ Chairperson confirms registrations
   └─ Final authorization

7. Registration Finalization
   ├─ Registration locked
   ├─ Invoice automatically generated
   ├─ Class lists updated
   ├─ Student receives confirmation
   └─ Student appears on course rolls

8. Late Registration (if applicable)
   ├─ Student requests late registration
   ├─ Late fee added to invoice
   ├─ Special approval required
   └─ Follow normal approval workflow
```

### 6.3 Examination Workflow

```
1. Exam Session Creation
   ├─ Exam officer creates session
   ├─ Defines exam period
   ├─ Links to semester
   └─ Sets registration deadline

2. Course Exam Setup
   ├─ Define test types (assignment, mid-term, final)
   ├─ Set weights for each component
   ├─ Define exam duration
   └─ Assign exam test type to courses

3. Student Registration for Exams
   ├─ Students automatically registered based on course registration
   ├─ Late registrations handled
   └─ Special exam cases (deferred, aegrotat)

4. Timetable Generation
   ├─ System generates master timetable
   ├─ Allocates courses to date/time slots
   ├─ Detects and resolves conflicts
   ├─ Assigns venues based on enrollment
   └─ Timetable approved by exam board

5. Student Timetable Distribution
   ├─ System generates personal timetables
   ├─ Students access via portal
   ├─ Email/SMS notifications sent
   └─ Print timetables available

6. Venue Preparation
   ├─ Venue lists generated
   ├─ Seating plans created
   ├─ Invigilators assigned
   └─ Exam materials distributed

7. Exam Execution
   ├─ Students write exams per timetable
   ├─ Attendance recorded
   └─ Exam papers collected

8. Result Entry
   ├─ Lecturers enter marks
   ├─ Component marks recorded (assignments, tests, finals)
   ├─ System calculates weighted totals
   ├─ Grades calculated based on marks
   └─ Result entry deadline enforced

9. Result Moderation
   ├─ Department reviews results
   ├─ Faculty moderates
   ├─ External examiner input
   ├─ Adjustments made if necessary
   └─ Results approved for publication

10. Result Publication
    ├─ Results published by course/programme
    ├─ Students notified
    ├─ Results accessible via portal
    └─ Result slips available for download

11. Overall Decision Computation
    ├─ System computes decisions per student
    ├─ Based on programme progression rules
    ├─ Decision types assigned (proceed, repeat, discontinue)
    └─ Faculty decision board reviews

12. Decision Publication
    ├─ Decisions published
    ├─ Students notified
    └─ Decision slips issued

13. Graduation Processing (for finalists)
    ├─ Graduands identified
    ├─ Degree classifications computed
    ├─ Graduation fees invoiced
    └─ Graduation register created
```

### 6.4 Accommodation Allocation Workflow

```
1. Configuration Phase
   ├─ Define allocation period
   ├─ Set application deadlines
   ├─ Configure priority rules
   ├─ Set allocation group percentages
   └─ Define room rates

2. Application Phase
   ├─ Allocation window opens
   ├─ Students submit applications
   ├─ Preferences recorded (facility, room type)
   ├─ Special needs captured
   └─ Applications close at deadline

3. Waiting List Creation
   ├─ All applicants added to waiting list
   ├─ Priority calculated per allocation rules
   ├─ Queue positions assigned
   └─ Students can view queue position

4. Allocation Processing
   ├─ System allocates rooms by priority
   ├─ Allocation groups processed in order
   ├─ Rooms assigned to students
   ├─ Capacity constraints enforced
   └─ Gender restrictions enforced

5. Allocation Notification
   ├─ Successful applicants notified
   ├─ Room details provided
   ├─ Payment instructions sent
   ├─ Acceptance deadline set
   └─ Unsuccessful applicants remain on waiting list

6. Payment and Confirmation
   ├─ Student pays accommodation fees
   ├─ Payment verified
   ├─ Allocation confirmed
   └─ If not paid by deadline, room forfeited

7. Check-In Process
   ├─ Student arrives at facility
   ├─ Financial clearance verified
   ├─ Room condition inspection
   ├─ Inventory checked
   ├─ Keys/access cards issued
   └─ Occupancy recorded

8. Ongoing Management
   ├─ Room swaps processed
   ├─ Damage reports filed
   ├─ Maintenance requests handled
   └─ Mid-year allocations from waiting list

9. Check-Out Process
   ├─ Student checks out
   ├─ Room inspection
   ├─ Damage charges assessed
   ├─ Keys/access cards returned
   └─ Clearance issued
```

### 6.5 Financial Cycle

```
1. Fee Structure Definition
   ├─ Finance department defines fees
   ├─ Fees by programme, level, semester
   ├─ Special fees configured
   └─ Effective dates set

2. Invoice Generation
   ├─ Triggered by student registration
   ├─ System calculates applicable fees
   ├─ Line items created
   ├─ Invoice number assigned
   └─ Student notified

3. Invoice Delivery
   ├─ Invoice available on student portal
   ├─ Email notification sent
   └─ Print invoice available

4. Payment Processing
   ├─ Student makes payment
   ├─ Payment recorded in system
   ├─ Payment method captured
   ├─ Payment allocated to invoice
   └─ Balance updated

5. Receipt Generation
   ├─ System generates receipt
   ├─ Receipt number assigned
   ├─ Receipt emailed to student
   └─ Receipt available for download/print

6. Financial Clearance
   ├─ System checks student balance
   ├─ If balance below threshold, clearance granted
   └─ If balance exceeds limit, hold placed

7. Refund Processing (if applicable)
   ├─ Refund request submitted
   ├─ Approval workflow
   ├─ Refund journal created
   ├─ Payment issued
   └─ Student account adjusted

8. Accounting Integration
   ├─ Transactions posted to GL
   ├─ Revenue recognized
   └─ Financial reports generated

9. Collections and Follow-Up
   ├─ Outstanding debt reports generated
   ├─ Reminders sent to students
   └─ Holds placed on services
```

---

## 7. Reporting Requirements

### 7.1 Student and Academic Reports

| Report Name | Description | Parameters | Output Format |
|-------------|-------------|------------|---------------|
| Enrollment Register | List of all enrolled students | Faculty, Programme, Level, Semester | Excel, PDF |
| Class Lists | Students registered for a course | Course, Semester | Excel, PDF |
| Registration Dashboard | Registration progress and statistics | Faculty, Semester | HTML Dashboard |
| Student Result Slip | Individual student results for semester | Student ID, Semester | PDF |
| Academic Transcript | Complete academic history | Student ID | PDF |
| Programme Change Report | Audit of programme changes | Date Range, Faculty | Excel, PDF |
| Student Demographics | Statistical analysis of student body | Gender, Programme, Intake | Excel with Charts |
| Graduation Register | List of graduands | Faculty, Programme, Year | Excel, PDF |
| Degree Classification Report | Students by degree class | Programme, Year | Excel |

### 7.2 Admission Reports

| Report Name | Description | Parameters | Output Format |
|-------------|-------------|------------|---------------|
| Application Statistics | Number of applications received | Programme, Intake, Year | Excel with Charts |
| Selected vs Accepted | Comparison of offers made vs accepted | Faculty, Programme, Intake | Excel, PDF |
| Admission Trends | Multi-year admission analysis | Years, Faculty | Excel with Graphs |
| Faculty Statistics | Applications by faculty | Faculty, Year | Excel |
| Supplement Admissions | Special admission route tracking | Programme, Type | Excel |
| Applicant Point Distribution | Statistical distribution of applicant points | Programme | Excel with Charts |

### 7.3 Financial Reports

| Report Name | Description | Parameters | Output Format |
|-------------|-------------|------------|---------------|
| Student Billing Summary | Total billing by various dimensions | Faculty, Programme, Level, Period | Excel, PDF |
| Payment Collection Report | Payments received | Date Range, Payment Method | Excel, PDF |
| Outstanding Debt Report | Students with outstanding balances | Faculty, Programme, Age of Debt | Excel |
| Fee Waiver Report | List of students with waivers | Waiver Type, Period | Excel |
| Refund Journal | Refund transactions | Date Range | Excel, PDF |
| Cashbook Report | Daily cash transactions | Date Range | Excel, PDF |
| Revenue Analysis | Revenue by source | Period, Faculty, Fee Type | Excel with Charts |
| Payment Method Analysis | Distribution of payment methods | Period | Excel with Charts |
| Debt Age Analysis | Aging of outstanding debt | As of Date | Excel |
| Invoice Audit Trail | Changes to invoices | Date Range, User | Excel |

### 7.4 Examination Reports

| Report Name | Description | Parameters | Output Format |
|-------------|-------------|------------|---------------|
| Student Exam Timetable | Personalized exam schedule | Student ID, Exam Session | PDF |
| Master Exam Timetable | Complete exam schedule | Exam Session, Faculty | Excel, PDF |
| Exam Occupancy Report | Venue utilization | Exam Session, Date | Excel |
| Venue Exception Report | Over/under capacity venues | Exam Session | Excel |
| Results Analysis | Pass rates and grade distribution | Course, Programme, Session | Excel with Charts |
| Outstanding Results | Courses without submitted results | Exam Session | Excel |

### 7.5 Accommodation Reports

| Report Name | Description | Parameters | Output Format |
|-------------|-------------|------------|---------------|
| Room Occupancy Status | Current occupancy by facility | Date | Excel |
| Waiting List Report | Students on waiting list | Allocation Period, Priority Order | Excel |
| Allocation by Student Type | Allocation distribution | Allocation Period | Excel with Charts |
| Vacancy Report | Available rooms | Date, Facility | Excel |
| Accommodation Revenue | Revenue by facility | Period | Excel |
| Damage Report | Damages recorded | Period, Facility | Excel |
| Gender Distribution | Occupancy by gender | Date | Excel with Charts |

### 7.6 Dining Reports

| Report Name | Description | Parameters | Output Format |
|-------------|-------------|------------|---------------|
| Daily Meal Attendance | Meal check-ins per day | Date, Dining Hall | Excel |
| Weekly Consumption | Weekly meal statistics | Week, Dining Hall | Excel |
| Dining Hall Utilization | Capacity usage | Period | Excel with Charts |
| Special Diet Tracking | Students with dietary needs | Dining Hall | Excel |

### 7.7 Administrative Reports

| Report Name | Description | Parameters | Output Format |
|-------------|-------------|------------|---------------|
| Course Statistics | Course enrollment and performance | Semester, Faculty | Excel |
| Staff Listing | Staff directory | Department, Faculty | Excel, PDF |
| User Access Log | System access audit | Date Range, User | Excel |
| System Audit Trail | Critical changes to data | Date Range, Entity Type | Excel |
| Department Workload | Teaching load by department | Semester | Excel |

---

## 8. Integration Requirements

### 8.1 Database Integration

**INT-001:** The system shall support multiple database connections:
- Primary MySQL database for operational data
- SQL Server (Sybase) connection for legacy data import
- Connection pooling for performance
- Transaction management across connections

**INT-002:** The system shall support data migration:
- Import historical student records from legacy systems
- Course equivalency mapping
- Data validation during import
- Error handling and logging

### 8.2 Payment Gateway Integration

**INT-010:** The system shall integrate with online payment gateways:
- Real-time payment processing
- Payment notification webhooks
- Automatic receipt generation
- Payment verification
- Reconciliation with gateway reports
- Support for mobile money (EcoCash, etc.)

### 8.3 SMS Gateway Integration

**INT-020:** The system shall integrate with SMS gateway:
- Send SMS notifications
- Bulk SMS capability
- Delivery status tracking
- Cost management
- Message templates

### 8.4 Email Service Integration

**INT-030:** The system shall integrate with email service:
- SMTP configuration
- Email templates
- Bulk email sending
- Attachment support
- Email queue management
- Delivery tracking

### 8.5 Biometric/Access Control Integration

**INT-040:** The system shall support integration with:
- Biometric systems for meal attendance
- Access card systems for dining and accommodation
- Student ID card barcode scanning

### 8.6 Document Management

**INT-050:** The system shall support document handling:
- PDF generation (TCPDF library)
- Excel generation and parsing (PHPExcel)
- File upload and storage
- Document verification

### 8.7 Chart and Graph Generation

**INT-060:** The system shall generate visualizations:
- Bar charts, line graphs, pie charts
- Statistical plots
- Report visualizations
- JPGraph library integration

---

## 9. Security and Audit Requirements

### 9.1 Authentication

**SEC-001:** The system shall implement secure authentication:
- Username/password authentication
- Password hashing (bcrypt or similar)
- Password strength requirements
- Password expiry policy
- Account lockout after failed attempts
- Forgot password functionality with email verification
- Session management with timeout

### 9.2 Authorization

**SEC-010:** The system shall implement role-based access control:
- User roles/groups
- Permission assignment at action level
- Hierarchical permissions
- Deny by default approach
- Permission checking on every request

**SEC-011:** Access control shall be enforced for:
- Controller actions
- Data visibility
- Report access
- Administrative functions

### 9.3 Data Security

**SEC-020:** The system shall protect sensitive data:
- Encryption of passwords
- Secure storage of payment information
- HTTPS enforcement for web access
- SQL injection prevention
- XSS prevention
- CSRF protection

### 9.4 Audit Trail

**SEC-030:** The system shall maintain comprehensive audit trails:
- User login/logout tracking
- All data modifications (create, update, delete)
- Audit fields: user, timestamp, action, old value, new value
- Immutable audit logs
- Audit trail for financial transactions
- Audit trail for grade changes
- Audit trail for critical administrative actions

**SEC-031:** Audit trails shall be maintained for:
- Student detail changes
- Course registration changes
- Exam result modifications
- Financial transactions (invoices, payments, refunds)
- Fee structure changes
- Programme changes
- Accommodation allocations
- User account changes

### 9.5 Data Privacy

**SEC-040:** The system shall comply with data privacy requirements:
- Student data confidentiality
- Access to student data limited by role
- Consent for data usage
- Data retention policies
- Right to access personal data
- Right to correct personal data

### 9.6 Backup and Recovery

**SEC-050:** The system shall support data backup:
- Regular automated backups
- Backup verification
- Disaster recovery procedures
- Database backup and restoration

---

## 10. Non-Functional Requirements

### 10.1 Performance

**NFR-001:** The system shall support:
- Minimum 1000 concurrent users
- Page load time < 3 seconds for standard operations
- Report generation < 30 seconds for standard reports
- Database query optimization
- Caching for frequently accessed data

### 10.2 Scalability

**NFR-010:** The system shall be scalable:
- Support for multiple faculties and departments
- Support for 50,000+ active students
- Horizontal scaling capability
- Database partitioning support

### 10.3 Availability

**NFR-020:** The system shall be highly available:
- 99.5% uptime during academic periods
- Scheduled maintenance windows
- Graceful degradation
- Error handling and recovery

### 10.4 Usability

**NFR-030:** The system shall be user-friendly:
- Intuitive navigation
- Consistent user interface
- Responsive design (mobile-friendly)
- Clear error messages
- Contextual help
- Multi-language support (if required)

### 10.5 Maintainability

**NFR-040:** The system shall be maintainable:
- Modular architecture
- Well-documented code
- Consistent coding standards
- Version control
- Automated testing capability

### 10.6 Compatibility

**NFR-050:** The system shall be compatible with:
- Modern web browsers (Chrome, Firefox, Edge, Safari)
- Mobile browsers
- Desktop and tablet devices
- MySQL 5.7+
- PHP 7.4+ (or migrate to PHP 8.x)

### 10.7 Reliability

**NFR-060:** The system shall be reliable:
- Data integrity constraints
- Transaction atomicity
- Error logging
- Graceful error handling
- Data validation

### 10.8 Compliance

**NFR-070:** The system shall comply with:
- Local education regulations
- Financial reporting standards
- Data protection regulations
- Accessibility standards (WCAG)

---

## Appendices

### Appendix A: Glossary

- **Aegrotat:** Pass granted due to illness or special circumstances
- **Carry Course:** Course carried forward to next level
- **Deferred Exam:** Exam postponed for valid reasons
- **GPA:** Grade Point Average
- **GL:** General Ledger
- **O-level:** Ordinary Level (secondary school qualification)
- **A-level:** Advanced Level (pre-university qualification)
- **SAR:** Special Admissions Route
- **Supplementary Exam:** Additional exam opportunity for failed courses

---

**End of Functional Requirements Document**

---
