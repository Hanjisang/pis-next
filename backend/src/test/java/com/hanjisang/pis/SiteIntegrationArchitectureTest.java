package com.hanjisang.pis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class SiteIntegrationArchitectureTest {

    private static final Set<String> FORBIDDEN_DOMAIN_NAME_FRAGMENTS = Set.of(
            "HospitalA", "HospitalB", "HisDto", "EmrDto", "LisDto", "Gk888", "Zebra", "ScannerSdk");

    private final JavaClasses productionClasses = new ClassFileImporter().importPackages("com.hanjisang.pis");

    @Test
    void coreDomainDoesNotDependOnSiteAdaptersDtosOrDeviceImplementations() {
        noClasses().that().resideInAPackage("com.hanjisang.pis.v2..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.hanjisang.pis.integration..",
                        "com.hanjisang.pis.presentation.configuration..",
                        "com.hanjisang.pis.security.identity..")
                .check(productionClasses);

        noClasses().that().resideInAPackage("com.hanjisang.pis.integration..")
                .should().dependOnClassesThat().resideInAPackage("com.hanjisang.pis.v2..domain..")
                .check(productionClasses);

        Set<String> domainTypeNames = productionClasses.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith("com.hanjisang.pis.v2.")
                        && javaClass.getPackageName().contains(".domain"))
                .map(JavaClass::getSimpleName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(domainTypeNames).noneMatch(name -> FORBIDDEN_DOMAIN_NAME_FRAGMENTS.stream().anyMatch(name::contains));
    }

    @Test
    void v2DoesNotDependOnRetiredBusinessModules() {
        noClasses().that().resideInAPackage("com.hanjisang.pis.v2..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.hanjisang.pis.accession..", "com.hanjisang.pis.specimen..",
                        "com.hanjisang.pis.technical..", "com.hanjisang.pis.diagnosis..",
                        "com.hanjisang.pis.frozen..", "com.hanjisang.pis.cytology..",
                        "com.hanjisang.pis.molecular..", "com.hanjisang.pis.referral..")
                .check(productionClasses);
    }
}
