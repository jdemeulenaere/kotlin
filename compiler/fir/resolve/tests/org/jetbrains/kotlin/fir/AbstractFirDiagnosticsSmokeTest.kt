/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import com.intellij.openapi.extensions.Extensions
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.checkers.BaseDiagnosticsTest
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.java.FirJavaModuleBasedSession
import org.jetbrains.kotlin.fir.java.FirLibrarySession
import org.jetbrains.kotlin.fir.java.FirProjectSessionProvider
import org.jetbrains.kotlin.fir.resolve.FirProvider
import org.jetbrains.kotlin.fir.resolve.firProvider
import org.jetbrains.kotlin.fir.resolve.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.resolve.transformers.FirTotalResolveTransformer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File
import java.util.*

abstract class AbstractFirDiagnosticsSmokeTest : BaseDiagnosticsTest() {
    override fun analyzeAndCheck(testDataFile: File, files: List<TestFile>) {
        try {
            analyzeAndCheckUnhandled(testDataFile, files)
        } catch (t: AssertionError) {
            throw t
        } catch (t: Throwable) {
            throw t
        }
    }


    override fun createEnvironment(file: File): KotlinCoreEnvironment {
        return super.createEnvironment(file).apply {
            Extensions.getArea(this.project)
                .getExtensionPoint(PsiElementFinder.EP_NAME)
                .unregisterExtension(JavaElementFinder::class.java)
        }
    }

    protected open fun analyzeAndCheckUnhandled(testDataFile: File, files: List<TestFile>) {
        val groupedByModule = files.groupBy(TestFile::module)

        val modules = createModules(groupedByModule)

        val sessionProvider = FirProjectSessionProvider(project)

        //For BuiltIns, registered in sessionProvider automatically
        val allProjectScope = GlobalSearchScope.allScope(project)
        FirLibrarySession.create(
            builtInsModuleInfo, sessionProvider, allProjectScope, project,
            environment.createPackagePartProvider(allProjectScope)
        )

        val configToSession = modules.mapValues { (config, info) ->
            val moduleFiles = groupedByModule.getValue(config)
            val scope = TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, moduleFiles.mapNotNull { it.ktFile })
            FirJavaModuleBasedSession(info, sessionProvider, scope)
        }

        val firFiles = mutableListOf<FirFile>()

        // TODO: make module/session/transformer handling like in AbstractFirMultiModuleTest (IDE)
        for ((testModule, testFilesInModule) in groupedByModule) {
            val ktFiles = getKtFiles(testFilesInModule, true)

            val session = configToSession.getValue(testModule)


            val firBuilder = RawFirBuilder(session, false)

            ktFiles.mapTo(firFiles) {
                val firFile = firBuilder.buildFirFile(it)

                (session.firProvider as FirProviderImpl).recordFile(firFile)

                firFile
            }
        }

        val failure: AssertionError? = try {
            doFirResolveTestBench(firFiles, FirTotalResolveTransformer().transformers, gc = false)
            null
        } catch (e: AssertionError) {
            e
        }
        val failureFile = File(testDataFile.path.replace(".kt", ".fir.fail"))
        if (failure == null) {
            checkResultingFirFiles(firFiles, testDataFile)
            assertFalse("Test is good but there is expected exception", failureFile.exists())
        } else {
            checkFailureFile(failure, failureFile)
        }
    }

    protected open fun checkResultingFirFiles(
        firFiles: MutableList<FirFile>,
        testDataFile: File
    ) {

    }

    private fun checkFailureFile(failure: AssertionError, failureFile: File) {
        val failureMessage = buildString {
            appendln(failure.message)
            failure.cause?.let {
                append("Cause: ")
                appendln(it)
            }
        }
        KotlinTestUtils.assertEqualsToFile(failureFile, failureMessage)
    }

    private fun createModules(
        groupedByModule: Map<TestModule?, List<TestFile>>
    ): MutableMap<TestModule?, ModuleInfo> {
        val modules = HashMap<TestModule?, ModuleInfo>()

        for (testModule in groupedByModule.keys) {
            val module = if (testModule == null)
                createSealedModule()
            else
                createModule(testModule.name)

            modules[testModule] = module
        }

        for (testModule in groupedByModule.keys) {
            if (testModule == null) continue

            val module = modules[testModule]!!
            val dependencies = ArrayList<ModuleInfo>()
            dependencies.add(module)
            for (dependency in testModule.getDependencies()) {
                dependencies.add(modules[dependency]!!)
            }


            dependencies.add(builtInsModuleInfo)
            //dependencies.addAll(getAdditionalDependencies(module))
            (module as TestModuleInfo).dependencies.addAll(dependencies)
        }

        return modules
    }

    private val builtInsModuleInfo = BuiltInModuleInfo(Name.special("<built-ins>"))

    protected open fun createModule(moduleName: String): TestModuleInfo {
        parseModulePlatformByName(moduleName)
        return TestModuleInfo(Name.special("<$moduleName>"))
    }

    class BuiltInModuleInfo(override val name: Name) : ModuleInfo {
        override val platform: TargetPlatform
            get() = JvmPlatforms.unspecifiedJvmPlatform

        override val analyzerServices: PlatformDependentAnalyzerServices
            get() = JvmPlatformAnalyzerServices

        override fun dependencies(): List<ModuleInfo> {
            return listOf(this)
        }
    }

    protected class TestModuleInfo(override val name: Name) : ModuleInfo {
        override val platform: TargetPlatform
            get() = JvmPlatforms.unspecifiedJvmPlatform

        override val analyzerServices: PlatformDependentAnalyzerServices
            get() = JvmPlatformAnalyzerServices

        val dependencies = mutableListOf<ModuleInfo>(this)
        override fun dependencies(): List<ModuleInfo> {
            return dependencies
        }
    }

    protected open fun createSealedModule(): TestModuleInfo =
        createModule("test-module-jvm").apply {
            dependencies += builtInsModuleInfo
        }

}
