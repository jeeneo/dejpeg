package com.je.dejpeg.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class HapticIssueRegistry : IssueRegistry() {
    override val issues = listOf(HapticFeedbackDetector.ISSUE)
    override val api = CURRENT_API
}
