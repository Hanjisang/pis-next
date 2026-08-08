package com.hanjisang.pis.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.hanjisang.pis.v2.registration.domain.Case;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class V2ArchitectureDriftTest {

    private static final Set<String> FORBIDDEN_V2_CORE_TYPES = Set.of(
            "BusinessRecord", "PlannedBlock", "ActualBlock", "PlannedSlide", "ActualSlide", "ReportVersion");

    @Test
    void v2DoesNotDefineDownstreamCoreTypes() {
        Set<String> v2TypeNames = new ClassFileImporter().importPackages("com.hanjisang.pis.v2").stream()
                .map(JavaClass::getSimpleName).collect(java.util.stream.Collectors.toSet());

        assertThat(v2TypeNames).doesNotContainAnyElementsOf(FORBIDDEN_V2_CORE_TYPES);
    }

    @Test
    void caseHasOnlyTheTwoAllowedLifecycleValues() {
        Set<String> lifecycleConstants = Arrays.stream(Case.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType().equals(String.class))
                .map(Field::getName).collect(java.util.stream.Collectors.toSet());

        assertThat(lifecycleConstants).containsExactlyInAnyOrder("ACTIVE", "CANCELLED");
    }
}
