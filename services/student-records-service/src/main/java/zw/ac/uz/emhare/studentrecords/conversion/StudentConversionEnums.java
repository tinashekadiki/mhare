package zw.ac.uz.emhare.studentrecords.conversion;

/** @author Tinashe K */
final class StudentConversionEnums {
    private StudentConversionEnums() {
    }
}

enum StudentStatus { PROVISIONING, ACTIVE, SUSPENDED, WITHDRAWN, INACTIVE }
enum ProgrammeEnrolmentStatus { PROVISIONING, ACTIVE, DEFERRED, SUSPENDED, TRANSFERRED, WITHDRAWN, COMPLETED }
enum StudentConversionStatus { PROVISIONING, COMPLETED, FAILED }
enum ProvisioningStatus { PENDING, COMPLETED, FAILED }
