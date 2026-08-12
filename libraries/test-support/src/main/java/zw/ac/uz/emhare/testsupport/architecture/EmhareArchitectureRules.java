package zw.ac.uz.emhare.testsupport.architecture;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Shared architecture rules for eMhare business services. @author Tinashe K */
public final class EmhareArchitectureRules {

    private EmhareArchitectureRules() {
    }

    public static ArchRule domainMustNotDependOnTransportOrMessaging() {
        return noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.amqp..",
                        "org.springframework.cloud..")
                .allowEmptyShould(true);
    }

    public static ArchRule domainMustNotDependOnOuterLayers() {
        return noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..api..",
                        "..application..",
                        "..infrastructure..")
                .allowEmptyShould(true);
    }

    public static ArchRule businessEntitiesMustResideInDomainModelPackages() {
        return classes()
                .that().areAnnotatedWith("jakarta.persistence.Entity")
                .and().resideOutsideOfPackage("..infrastructure..")
                .should().resideInAPackage("..domain.model..")
                .allowEmptyShould(true);
    }

    public static ArchRule technicalEntitiesMustUseInfrastructureModelPackages() {
        return classes()
                .that().areAnnotatedWith("jakarta.persistence.Entity")
                .and().resideInAPackage("..infrastructure..")
                .should().resideInAPackage("..infrastructure..model..")
                .allowEmptyShould(true);
    }

    public static ArchRule repositoriesMustResideInInfrastructurePersistencePackages() {
        return classes()
                .that().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..infrastructure.persistence..")
                .allowEmptyShould(true);
    }

    public static ArchRule controllersMustResideInApiControllerPackages() {
        return classes()
                .that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..api.controller..")
                .allowEmptyShould(true);
    }

    public static ArchRule apiControllerPackagesMustContainOnlyControllers() {
        return classes()
                .that().resideInAPackage("..api.controller..")
                .and().areTopLevelClasses()
                .and().doNotHaveSimpleName("package-info")
                .should().haveSimpleNameEndingWith("Controller")
                .allowEmptyShould(true);
    }

    public static ArchRule commandsMustResideInApplicationCommandPackages() {
        return classes()
                .that().haveSimpleNameEndingWith("Command")
                .should().resideInAPackage("..application.command..")
                .allowEmptyShould(true);
    }

    public static ArchRule controllersMustNotAcceptApplicationCommands() {
        return methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should(new ArchCondition<>("not accept application commands as HTTP parameters") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        for (JavaClass parameterType : method.getRawParameterTypes()) {
                            if (parameterType.getSimpleName().endsWith("Command")) {
                                events.add(SimpleConditionEvent.violated(
                                        method,
                                        method.getFullName() + " accepts application command "
                                                + parameterType.getFullName()));
                            }
                        }
                    }
                })
                .allowEmptyShould(true);
    }

    public static ArchRule serviceMustNotImportAnotherBusinessService(String serviceBasePackage) {
        List<String> businessServicePackages = new ArrayList<>(List.of(
                "zw.ac.uz.emhare.coreidentity..",
                "zw.ac.uz.emhare.academicsetup..",
                "zw.ac.uz.emhare.admissions..",
                "zw.ac.uz.emhare.finance..",
                "zw.ac.uz.emhare.studentrecords..",
                "zw.ac.uz.emhare.assessmentresults..",
                "zw.ac.uz.emhare.examstimetabling..",
                "zw.ac.uz.emhare.accommodation..",
                "zw.ac.uz.emhare.dining..",
                "zw.ac.uz.emhare.documentsreporting..",
                "zw.ac.uz.emhare.notifications.."));
        businessServicePackages.remove(serviceBasePackage + "..");
        return noClasses()
                .that().resideInAPackage(serviceBasePackage + "..")
                .should().dependOnClassesThat().resideInAnyPackage(businessServicePackages.toArray(String[]::new));
    }
}
