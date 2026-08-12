package zw.ac.uz.emhare.examstimetabling.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import zw.ac.uz.emhare.testsupport.architecture.EmhareArchitectureRules;

/** Canonical eMhare service package boundaries. @author Tinashe K */
@AnalyzeClasses(packages = "zw.ac.uz.emhare.examstimetabling", importOptions = ImportOption.DoNotIncludeTests.class)
class ExamsTimetablingArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_TRANSPORT_ISOLATION =
            EmhareArchitectureRules.domainMustNotDependOnTransportOrMessaging();

    @ArchTest
    static final ArchRule DOMAIN_LAYERING = EmhareArchitectureRules.domainMustNotDependOnOuterLayers();

    @ArchTest
    static final ArchRule BUSINESS_ENTITY_LOCATION =
            EmhareArchitectureRules.businessEntitiesMustResideInDomainModelPackages();

    @ArchTest
    static final ArchRule TECHNICAL_ENTITY_LOCATION =
            EmhareArchitectureRules.technicalEntitiesMustUseInfrastructureModelPackages();

    @ArchTest
    static final ArchRule REPOSITORY_LOCATION =
            EmhareArchitectureRules.repositoriesMustResideInInfrastructurePersistencePackages();

    @ArchTest
    static final ArchRule SERVICE_BOUNDARY =
            EmhareArchitectureRules.serviceMustNotImportAnotherBusinessService("zw.ac.uz.emhare.examstimetabling");

    @ArchTest
    static final ArchRule CONTROLLER_LOCATION = EmhareArchitectureRules.controllersMustResideInApiControllerPackages();

    @ArchTest
    static final ArchRule CONTROLLER_PACKAGE_CONTENTS =
            EmhareArchitectureRules.apiControllerPackagesMustContainOnlyControllers();

    @ArchTest
    static final ArchRule COMMAND_LOCATION = EmhareArchitectureRules.commandsMustResideInApplicationCommandPackages();

    @ArchTest
    static final ArchRule CONTROLLER_COMMAND_BINDING =
            EmhareArchitectureRules.controllersMustNotAcceptApplicationCommands();
}
