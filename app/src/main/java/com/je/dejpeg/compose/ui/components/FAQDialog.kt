package com.je.dejpeg.compose.ui.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.je.dejpeg.R

data class FAQSectionData(
    val title: String,
    val content: String?,
    val subSections: List<Pair<String, String>>?
)

@Composable
fun FAQDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val faqSections = remember { loadFAQSections(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                stringResource(R.string.faqs),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(faqSections.size) {
                    FAQSection(
                        faqSections[it].title,
                        faqSections[it].content,
                        faqSections[it].subSections
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun FAQSection(title: String, content: String?, subSections: List<Pair<String, String>>? = null) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                modifier = Modifier.rotate(if (expanded) 180f else 0f)
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                content?.let {
                    MarkdownText(
                        it,
                        MaterialTheme.typography.bodyMedium,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        context
                    )
                }
                subSections?.forEach { (subTitle, subContent) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        subTitle,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    MarkdownText(
                        subContent,
                        MaterialTheme.typography.bodyMedium,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        context
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
fun MarkdownText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    context: android.content.Context
) {
    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        """\[([^]]+)]\(([^)]+)\)""".toRegex().findAll(text).forEach { m ->
            append(text.substring(lastIndex, m.range.first))
            val start = length
            append(m.groupValues[1])
            val url = m.groupValues[2]
            addStyle(SpanStyle(color = color, textDecoration = TextDecoration.Underline), start, length)
            addLink(
                LinkAnnotation.Clickable(
                    tag = "URL",
                    linkInteractionListener = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.cannot_open_link_detail, url),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ),
                start,
                length
            )
            lastIndex = m.range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
    Text(
        text = annotatedString,
        style = style.copy(color = color)
    )
}

fun loadFAQSections(context: android.content.Context): List<FAQSectionData> {
    val sections = mutableListOf<FAQSectionData>()
    try {
        context.assets.list("faq")?.filter { it.endsWith(".md") }?.forEach { fileName ->
            val lines = context.assets.open("faq/$fileName").bufferedReader().readText().lines()
            var title: String? = null
            var content = StringBuilder()
            var subSections = mutableListOf<Pair<String, String>>()
            var subTitle: String? = null
            var subContent = StringBuilder()
            lines.forEach { line ->
                when {
                    line.startsWith("## ") -> {
                        title?.let {
                            sections.add(
                                FAQSectionData(
                                    it,
                                    content.toString().trim().ifEmpty { null },
                                    subSections.ifEmpty { null }
                                )
                            )
                        }
                        title = line.removePrefix("## ").trim()
                        content = StringBuilder()
                        subSections = mutableListOf()
                        subTitle = null
                        subContent = StringBuilder()
                    }
                    line.startsWith("### ") -> {
                        subTitle?.let { subSections.add(it to subContent.toString().trim()) }
                        subTitle = line.removePrefix("### ").trim()
                        subContent = StringBuilder()
                    }
                    else -> if (subTitle != null) subContent.appendLine(line) else content.appendLine(line)
                }
            }
            title?.let { mainTitle ->
                subTitle?.let { subT -> subSections.add(subT to subContent.toString().trim()) }
                sections.add(
                    FAQSectionData(
                        mainTitle,
                        content.toString().trim().ifEmpty { null },
                        subSections.ifEmpty { null }
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return sections
}
