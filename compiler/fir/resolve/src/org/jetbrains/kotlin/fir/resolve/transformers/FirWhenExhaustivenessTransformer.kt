/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.impl.FirElseIfTrueCondition
import org.jetbrains.kotlin.fir.resolve.BodyResolveComponents
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeNullability
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.visitors.CompositeTransformResult
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.fir.visitors.compose
import org.jetbrains.kotlin.name.ClassId

class FirWhenExhaustivenessTransformer(private val bodyResolveComponents: BodyResolveComponents) : FirTransformer<Nothing?>() {
    override fun <E : FirElement> transformElement(element: E, data: Nothing?): CompositeTransformResult<E> {
        throw IllegalArgumentException("Should not be there")
    }

    override fun transformWhenExpression(whenExpression: FirWhenExpression, data: Nothing?): CompositeTransformResult<FirStatement> {
        val resultExpression = processExhaustivenessCheck(whenExpression) ?: whenExpression
        return resultExpression.compose()
    }

    private fun processExhaustivenessCheck(whenExpression: FirWhenExpression): FirWhenExpression? {
        if (whenExpression.branches.any { it.condition is FirElseIfTrueCondition }) {
            whenExpression.replaceIsExhaustive(true)
            return whenExpression
        }

        val typeRef = (whenExpression.subjectVariable?.returnTypeRef
            ?: (whenExpression.subject as? FirQualifiedAccessExpression)?.typeRef) as? FirResolvedTypeRef
            ?: return null

        val lookupTag = (typeRef.type as? ConeLookupTagBasedType)?.lookupTag ?: return null
        val isExhaustive = when {
            ((lookupTag as? ConeClassLikeLookupTag)?.classId == bodyResolveComponents.session.builtinTypes.booleanType.id) ->
                checkBooleanExhaustiveness(whenExpression)

            else -> {
                val klass = lookupTag.toSymbol(bodyResolveComponents.session)?.fir as? FirRegularClass ?: return null
                when {
                    klass.classKind == ClassKind.ENUM_CLASS -> checkEnumExhaustiveness(whenExpression, klass, typeRef)
                    klass.modality == Modality.SEALED -> checkSealedClassExhaustiveness(whenExpression)
                    else -> return null
                }
            }
        }

        return if (isExhaustive) {
            whenExpression.replaceIsExhaustive(true)
            whenExpression
        } else {
            null
        }
    }

    // ------------------------ Enum exhaustiveness ------------------------

    private fun checkEnumExhaustiveness(whenExpression: FirWhenExpression, enum: FirRegularClass, typeRef: FirResolvedTypeRef): Boolean {
        val data = EnumExhaustivenessData(
            enum.collectEnumEntries().associateByTo(mutableMapOf(), { it.classId }, { false }),
            typeRef.type.nullability == ConeNullability.NOT_NULL
        )
        for (branch in whenExpression.branches) {
            branch.condition.accept(EnumExhaustivenessVisitor, data)
        }
        return data.containsNull && data.visitedEntries.values.all { it }
    }

    private class EnumExhaustivenessData(val visitedEntries: MutableMap<ClassId, Boolean>, var containsNull: Boolean)

    private object EnumExhaustivenessVisitor : FirVisitor<Unit, EnumExhaustivenessData>() {
        override fun visitElement(element: FirElement, data: EnumExhaustivenessData) {}

        override fun visitOperatorCall(operatorCall: FirOperatorCall, data: EnumExhaustivenessData) {
            if (operatorCall.operation == FirOperation.EQ) {
                operatorCall.arguments[1].accept(this, data)
            }
        }

        override fun visitResolvedQualifier(resolvedQualifier: FirResolvedQualifier, data: EnumExhaustivenessData) {
            val classId = resolvedQualifier.classId ?: return
            data.visitedEntries.replace(classId, true)
        }

        override fun <T> visitConstExpression(constExpression: FirConstExpression<T>, data: EnumExhaustivenessData) {
            if (constExpression.value == null) {
                data.containsNull = true
            }
        }
    }

    // ------------------------ Sealed class exhaustiveness ------------------------

    private fun checkSealedClassExhaustiveness(whenExpression: FirWhenExpression): Boolean {
        // TODO
        return false
    }

    // ------------------------ Boolean exhaustiveness ------------------------

    private fun checkBooleanExhaustiveness(whenExpression: FirWhenExpression): Boolean {
        val flags = BooleanExhaustivenessFlags()
        for (branch in whenExpression.branches) {
            branch.condition.accept(BooleanExhaustivenessVisitor, flags)
        }
        return flags.containsTrue && flags.containsFalse
    }

    private class BooleanExhaustivenessFlags {
        var containsTrue = false
        var containsFalse = false
    }

    private object BooleanExhaustivenessVisitor : FirVisitor<Unit, BooleanExhaustivenessFlags>() {
        override fun visitElement(element: FirElement, data: BooleanExhaustivenessFlags) {}

        override fun visitOperatorCall(operatorCall: FirOperatorCall, data: BooleanExhaustivenessFlags) {
            if (operatorCall.operation == FirOperation.EQ) {
                operatorCall.arguments[1].accept(this, data)
            }
        }

        override fun <T> visitConstExpression(constExpression: FirConstExpression<T>, data: BooleanExhaustivenessFlags) {
            when (constExpression.value) {
                true -> data.containsTrue = true
                false -> data.containsFalse = true
            }
        }
    }
}