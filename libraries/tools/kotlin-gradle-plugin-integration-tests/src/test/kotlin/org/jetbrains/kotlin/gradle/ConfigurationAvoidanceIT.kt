/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.junit.Test

open class ConfigurationAvoidanceIT : BaseGradleIT() {

    @Test
    fun testUnrelatedTaskNotConfigured() = with(Project("simpleProject", GradleVersionRequired.AtLeast("4.10.2"))) {
        setupWorkingDir()

        val expensivelyConfiguredTaskName = "expensivelyConfiguredTask"
        val triggeredExpensiveConfigurationText = "Triggered expensive configuration!"

        gradleBuildScript().appendText("\n" + """
            tasks.register("$expensivelyConfiguredTaskName") {
                println("$triggeredExpensiveConfigurationText")
            }
        """.trimIndent())

        build("compileKotlin") {
            assertSuccessful()
            assertNotContains(triggeredExpensiveConfigurationText)
        }
    }

}