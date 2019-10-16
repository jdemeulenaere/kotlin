/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.checkers

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.isEffectivelyFinal

object TailrecFunctionChecker : DeclarationChecker {
    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (declaration !is KtNamedFunction || descriptor !is FunctionDescriptor || !descriptor.isTailrec) return

        if (!descriptor.isEffectivelyFinal(false)) {

            if (!context.languageVersionSettings.supportsFeature(LanguageFeature.ProhibitTailrecOnVirtualMember)) {
                context.trace.report(Errors.TAILREC_ON_VIRTUAL_MEMBER.on(declaration))
            } else {
                context.trace.report(Errors.TAILREC_ON_VIRTUAL_MEMBER_ERROR.on(declaration))
            }
        }

        val defaultValues = descriptor.valueParameters.filter { it.declaresDefaultValue() }.filter {
            val declaration = DescriptorToSourceUtils.descriptorToDeclaration(it)
            if (declaration is KtParameter) {
                context.trace.bindingContext.get(BindingContext.COMPILE_TIME_VALUE, declaration.defaultValue)?.isPure != true
            } else true
        }

        if (defaultValues.size > 1) {
            context.trace.report(Errors.TAILREC_WITH_DEFAULTS.on(declaration))
        }
    }
}