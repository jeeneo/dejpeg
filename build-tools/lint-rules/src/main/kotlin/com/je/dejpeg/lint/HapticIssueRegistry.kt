package com.je.dejpeg.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class HapticIssueRegistry : IssueRegistry() {
    override val issues = listOf(HapticFeedbackDetector.ISSUE)
    override val api = CURRENT_API
    override val vendor = Vendor(
        vendorName = "DeJPEG",
        identifier = "lint-rules",
        feedbackUrl = "https://github.com/je/dejpeg/issues",
        contact = null
    )
}
