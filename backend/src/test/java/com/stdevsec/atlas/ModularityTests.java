package com.stdevsec.atlas;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Module boundaries (ADR 0001): fails the build on illegal cross-module dependencies or cycles. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(StAtlasBackendApplication.class);

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
