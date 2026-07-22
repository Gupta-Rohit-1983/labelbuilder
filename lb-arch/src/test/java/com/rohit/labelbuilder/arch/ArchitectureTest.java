package com.rohit.labelbuilder.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Executable architecture rules from docs/architecture.md §2.
 *
 * <p>Maven cannot stop a module from using a class that arrived transitively on its classpath;
 * these bytecode-level rules can. They run in the normal test phase, so a violation fails
 * {@code mvnw verify} and CI.
 */
@AnalyzeClasses(packages = "com.rohit.labelbuilder", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String MODEL = "com.rohit.labelbuilder.model..";
    private static final String CORE = "com.rohit.labelbuilder.core..";
    private static final String BARCODE = "com.rohit.labelbuilder.barcode..";
    private static final String RENDER = "com.rohit.labelbuilder.render..";
    private static final String PRINT = "com.rohit.labelbuilder.print..";
    private static final String DATA = "com.rohit.labelbuilder.data..";
    private static final String PLUGIN = "com.rohit.labelbuilder.plugin..";
    private static final String AUTOMATION = "com.rohit.labelbuilder.automation..";
    private static final String DESKTOP = "com.rohit.labelbuilder.desktop..";
    private static final String SERVER = "com.rohit.labelbuilder.server..";

    /** architecture.md hard rule: "Nothing depends on lb-desktop. Ever." */
    @ArchTest
    static final ArchRule nothingDependsOnDesktop = noClasses()
            .that()
            .resideOutsideOfPackage(DESKTOP)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(DESKTOP);

    /** The server is a leaf application: no other module may reach into it. */
    @ArchTest
    static final ArchRule nothingDependsOnServer = noClasses()
            .that()
            .resideOutsideOfPackage(SERVER)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(SERVER);

    /**
     * JavaFX is confined to the desktop module. lb-core/render/print/data must stay headless so
     * lb-server can use them (FR-A-01), and the reference renderer stays Java2D, not FX.
     */
    @ArchTest
    static final ArchRule javafxOnlyInDesktop = noClasses()
            .that()
            .resideOutsideOfPackage(DESKTOP)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("javafx..");

    /** lb-model is pure data: JDK + Jackson annotations only, no framework, no I/O libraries. */
    @ArchTest
    static final ArchRule modelIsPure = classes()
            .that()
            .resideInAPackage(MODEL)
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "com.fasterxml.jackson.annotation..", MODEL);

    /** Spring stays out of the library modules; only the two applications may use it. */
    @ArchTest
    static final ArchRule springOnlyInApplications = noClasses()
            .that()
            .resideInAnyPackage(MODEL, CORE, BARCODE, RENDER, PRINT, DATA, PLUGIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");

    /** The module dependency graph from architecture.md §2, enforced at bytecode level. */
    @ArchTest
    static final ArchRule moduleLayering = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("model")
            .definedBy(MODEL)
            .layer("core")
            .definedBy(CORE)
            .layer("barcode")
            .definedBy(BARCODE)
            .layer("render")
            .definedBy(RENDER)
            .layer("print")
            .definedBy(PRINT)
            .layer("data")
            .definedBy(DATA)
            .layer("plugin")
            .definedBy(PLUGIN)
            .layer("automation")
            .definedBy(AUTOMATION)
            .layer("desktop")
            .definedBy(DESKTOP)
            .layer("server")
            .definedBy(SERVER)
            .whereLayer("desktop")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("server")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("automation")
            .mayOnlyBeAccessedByLayers("server")
            .whereLayer("data")
            .mayOnlyBeAccessedByLayers("automation", "desktop", "server")
            .whereLayer("print")
            .mayOnlyBeAccessedByLayers("automation", "desktop", "server")
            .whereLayer("render")
            .mayOnlyBeAccessedByLayers("print", "desktop", "server")
            .whereLayer("barcode")
            .mayOnlyBeAccessedByLayers("render", "print", "desktop")
            .whereLayer("core")
            .mayOnlyBeAccessedByLayers("render", "print", "data", "automation", "desktop", "server")
            .whereLayer("model")
            .mayNotAccessAnyLayer();
}
