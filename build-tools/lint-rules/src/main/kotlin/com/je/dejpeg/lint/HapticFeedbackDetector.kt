package com.je.dejpeg.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.tryResolve

class HapticFeedbackDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    private val clickParamNames = setOf(
        "onClick", "onCheckedChange", "onLongClick", "onValueChange"
    )

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                node.valueArguments.forEachIndexed { _, arg ->
                    val paramName = node.getParameterForArgument(arg)?.name ?: return@forEachIndexed
                    if (paramName in clickParamNames) {
                        when (arg) {
                            is ULambdaExpression -> checkLambdaForHaptic(
                                arg, context, node, paramName
                            )

                            is UCallableReferenceExpression, is USimpleNameReferenceExpression -> reportIfFunctionRefLacksHaptic(
                                arg, context, node, paramName
                            )

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun checkLambdaForHaptic(
        lambda: ULambdaExpression, context: JavaContext, node: UCallExpression, paramLabel: String
    ) {
        val bodyText = lambda.body.asSourceString()
        val hasHaptic = bodyText.contains("HapticPatterns.")

        if (!hasHaptic) {
            context.report(
                ISSUE,
                node,
                context.getLocation(lambda),
                "`$paramLabel` handler is missing a HapticPatterns.* call"
            )
        }
    }

    private fun reportIfFunctionRefLacksHaptic(
        arg: UExpression, context: JavaContext, node: UCallExpression, paramLabel: String
    ) {
        val resolved = arg.tryResolve() as? UMethod
        val bodyText = resolved?.uastBody?.asSourceString().orEmpty()
        if (!bodyText.contains("HapticPatterns.")) {
            context.report(
                ISSUE,
                node,
                context.getLocation(arg),
                "`$paramLabel` references a function that doesn't appear to call HapticPatterns.* (resolution may be incomplete)"
            )
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "MissingHapticFeedback",
            briefDescription = "Click handler missing haptic feedback",
            explanation = "Main interaction handlers should call a respective HapticPatterns.* function",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                HapticFeedbackDetector::class.java, Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
