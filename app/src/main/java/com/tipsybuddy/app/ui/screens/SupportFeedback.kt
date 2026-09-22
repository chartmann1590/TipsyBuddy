package com.tipsybuddy.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tipsybuddy.app.BuildConfig
import com.tipsybuddy.app.data.feedback.BugReport
import com.tipsybuddy.app.data.feedback.BugReportRepo
import com.tipsybuddy.app.data.feedback.DiagnosticsHelper
import com.tipsybuddy.app.data.feedback.FeedbackComment
import com.tipsybuddy.app.data.feedback.FeedbackIssue
import com.tipsybuddy.app.data.feedback.FeedbackWorkerApi
import com.tipsybuddy.app.data.feedback.buildCommentBody
import com.tipsybuddy.app.data.feedback.buildIssueBody
import com.tipsybuddy.app.data.feedback.extensionForUri
import com.tipsybuddy.app.data.feedback.isFeedbackConfigured
import com.tipsybuddy.app.data.feedback.uniqueCommentFileName
import com.tipsybuddy.app.data.feedback.uniqueIssueFileName
import com.tipsybuddy.app.data.feedback.uriToBase64
import com.tipsybuddy.app.ui.theme.CardBorder
import com.tipsybuddy.app.ui.theme.CoralRed
import com.tipsybuddy.app.ui.theme.NeonCyan
import com.tipsybuddy.app.ui.theme.NeonGold
import com.tipsybuddy.app.ui.theme.NeonGreen
import com.tipsybuddy.app.ui.theme.SurfaceDark
import com.tipsybuddy.app.ui.theme.TextMuted
import com.tipsybuddy.app.ui.theme.TextPrimary
import com.tipsybuddy.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FeedbackFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = NeonGold,
        unfocusedBorderColor = CardBorder,
    )

/**
 * Reusable "Support & Feedback" section. Drop into Settings/Profile/Help screens.
 * Shows a Report button plus locally stored submitted reports with live GitHub status.
 */
@Composable
fun SupportFeedbackSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { BugReportRepo(context.applicationContext) }
    val api = remember { FeedbackWorkerApi() }
    val reports by repo.bugReports.collectAsState(initial = emptyList())

    var showReportDialog by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<BugReport?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF0C1220)) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = NeonGold,
                        modifier = Modifier.padding(8.dp).size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("SUPPORT & FEEDBACK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                    Text("Report bugs — tracked as GitHub issues", fontSize = 12.sp, color = TextSecondary)
                }
            }

            if (!api.isConfigured) {
                Text(
                    "Feedback service is not configured for this build.",
                    fontSize = 12.sp,
                    color = CoralRed,
                )
            }

            Button(
                onClick = { showReportDialog = true },
                enabled = api.isConfigured,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGold, contentColor = Color.Black),
            ) {
                Text("Report a Problem", fontWeight = FontWeight.Bold)
            }

            statusMessage?.let {
                Text(it, fontSize = 12.sp, color = NeonCyan)
            }

            if (reports.isEmpty()) {
                Text("No reports submitted yet on this device.", fontSize = 12.sp, color = TextMuted)
            } else {
                // Refresh known statuses in the background (best-effort, keeps cache on failure).
                LaunchedEffect(reports.map { it.number }) {
                    if (!api.isConfigured) return@LaunchedEffect
                    withContext(Dispatchers.IO) {
                        var changed = false
                        val updated = reports.map { cached ->
                            when (val r = api.getIssue(cached.number)) {
                                is FeedbackWorkerApi.Result.Ok -> {
                                    if (r.value.state != cached.status) {
                                        changed = true
                                        cached.copy(status = r.value.state, title = r.value.title)
                                    } else {
                                        cached
                                    }
                                }
                                else -> cached
                            }
                        }
                        if (changed) {
                            try {
                                repo.updateBugReports(updated)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SUBMITTED REPORTS (${reports.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                    reports.take(10).forEach { report ->
                        ReportRow(
                            report = report,
                            onClick = { selectedReport = report },
                        )
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportProblemDialog(
            api = api,
            onDismiss = { showReportDialog = false },
            onSubmitted = { report ->
                scope.launch {
                    try {
                        repo.saveBugReport(report)
                    } catch (_: Exception) {
                    }
                    statusMessage = "Report #${report.number} submitted."
                }
                showReportDialog = false
            },
        )
    }

    selectedReport?.let { report ->
        IssueDetailsDialog(
            api = api,
            repo = repo,
            report = report,
            onDismiss = { selectedReport = null },
        )
    }
}

@Composable
private fun ReportRow(report: BugReport, onClick: () -> Unit) {
    val isOpen = !report.status.equals("closed", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0C1220),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(report.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 2)
                Text(
                    "#${report.number} • ${formatDate(report.createdAt)}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            StatusBadge(isOpen = isOpen)
        }
    }
}

@Composable
private fun StatusBadge(isOpen: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isOpen) Color(0xFF052E1F) else Color(0xFF1E1B2E),
        border = BorderStroke(1.dp, if (isOpen) NeonGreen else TextMuted),
    ) {
        Text(
            if (isOpen) "Open" else "Closed",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isOpen) NeonGreen else TextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PrivacyWarning() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2A1503),
        border = BorderStroke(1.dp, NeonGold),
    ) {
        Text(
            "Your report will be submitted to this app's GitHub issue tracker. " +
                "Do not include passwords, private keys, medical information, financial information, " +
                "account credentials, or anything you do not want visible to repository maintainers. " +
                "Screenshots may contain private information. If this repository is public, your report " +
                "and attached screenshot may be publicly visible.",
            fontSize = 11.sp,
            color = TextPrimary,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ImagePickerRow(
    imageUri: Uri?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageUri) {
        if (imageUri == null) {
            bitmap = null
        } else {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(context.contentResolver, imageUri),
                        ) { decoder, _, _ -> decoder.setTargetSampleSize(2) }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPick, shape = RoundedCornerShape(12.dp)) {
                Text(if (imageUri == null) "Attach screenshot/image" else "Change image", color = NeonCyan)
            }
            if (imageUri != null) {
                TextButton(onClick = { bitmap = null; onClear() }) {
                    Text("Remove", color = CoralRed)
                }
            }
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Selected attachment preview",
                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun ReportProblemDialog(
    api: FeedbackWorkerApi,
    onDismiss: () -> Unit,
    onSubmitted: (BugReport) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
    }

    Dialog(onDismissRequest = { if (!submitting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f),
        ) {
            Column(modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Report a Problem", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                PrivacyWarning()
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(200); error = null },
                    label = { Text("Title / Subject *") },
                    placeholder = { Text("e.g. Crash when saving a drink") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = FeedbackFieldColors,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = { Text("Description *") },
                    placeholder = { Text("What happened? Steps to reproduce…") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    colors = FeedbackFieldColors,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = includeDiagnostics,
                        onCheckedChange = { includeDiagnostics = it },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Include phone/app diagnostics", fontSize = 13.sp, color = TextPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        modifier = Modifier.weight(1f),
                        colors = FeedbackFieldColors,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email (optional)") },
                        modifier = Modifier.weight(1f),
                        colors = FeedbackFieldColors,
                        singleLine = true,
                    )
                }
                ImagePickerRow(
                    imageUri = imageUri,
                    onPick = { pickImage.launch("image/*") },
                    onClear = { imageUri = null },
                )
                error?.let { Text(it, fontSize = 12.sp, color = CoralRed) }
                if (!isFeedbackConfigured(BuildConfig.FEEDBACK_WORKER_URL)) {
                    Text("Feedback service is not configured for this build.", fontSize = 12.sp, color = CoralRed)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { if (!submitting) onDismiss() }, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            val cleanTitle = title.trim()
                            val cleanDesc = description.trim()
                            when {
                                cleanTitle.isBlank() -> error = "Please enter a title."
                                cleanDesc.isBlank() -> error = "Please enter a description."
                                submitting -> Unit
                                else -> {
                                    submitting = true
                                    error = null
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            // 1. Upload attachment first (if any).
                                            var attachmentUrl: String? = null
                                            imageUri?.let { uri ->
                                                val base64 = uriToBase64(context, uri).getOrElse {
                                                    throw IllegalArgumentException(it.message ?: "Image read failed.")
                                                }
                                                val ext = extensionForUri(context, uri)
                                                when (val up = api.uploadAsset(uniqueIssueFileName(ext), base64)) {
                                                    is FeedbackWorkerApi.Result.Ok ->
                                                        attachmentUrl = up.value.downloadUrl
                                                            ?: throw IllegalStateException("Upload returned no URL.")
                                                    is FeedbackWorkerApi.Result.Err ->
                                                        throw IllegalStateException("Image upload failed: ${up.message}")
                                                }
                                            }
                                            // 2. Build Markdown + diagnostics.
                                            val diagnostics = if (includeDiagnostics) {
                                                DiagnosticsHelper.collect(context)
                                            } else {
                                                null
                                            }
                                            val body = buildIssueBody(cleanDesc, name, email, attachmentUrl, diagnostics)
                                            // 3. Create the real GitHub issue via the Worker.
                                            when (val created = api.createIssue("[Feedback] $cleanTitle", body)) {
                                                is FeedbackWorkerApi.Result.Ok -> {
                                                    val report = BugReport(
                                                        number = created.value.number,
                                                        title = created.value.title,
                                                        status = created.value.state,
                                                        createdAt = created.value.createdAt,
                                                        htmlUrl = created.value.htmlUrl,
                                                    )
                                                    withContext(Dispatchers.Main) { onSubmitted(report) }
                                                }
                                                is FeedbackWorkerApi.Result.Err ->
                                                    throw IllegalStateException(created.message)
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                error = e.message ?: "Submission failed."
                                                submitting = false
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !submitting && api.isConfigured,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGold, contentColor = Color.Black),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("Submitting…", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Submit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueDetailsDialog(
    api: FeedbackWorkerApi,
    repo: BugReportRepo,
    report: BugReport,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var issue by remember { mutableStateOf<FeedbackIssue?>(null) }
    var comments by remember { mutableStateOf<List<FeedbackComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reply by remember { mutableStateOf("") }
    var replyImageUri by remember { mutableStateOf<Uri?>(null) }
    var posting by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf<String?>(null) }

    val pickReplyImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        replyImageUri = uri
    }

    fun refresh() {
        loading = true
        error = null
        scope.launch(Dispatchers.IO) {
            val fetchedIssue = api.getIssue(report.number)
            val fetchedComments = api.getComments(report.number)
            withContext(Dispatchers.Main) {
                when (fetchedIssue) {
                    is FeedbackWorkerApi.Result.Ok -> {
                        issue = fetchedIssue.value
                        // Update cached status if GitHub state changed.
                        if (!fetchedIssue.value.state.equals(report.status, ignoreCase = true)) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    repo.saveBugReport(
                                        report.copy(status = fetchedIssue.value.state, title = fetchedIssue.value.title),
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                    is FeedbackWorkerApi.Result.Err -> error = fetchedIssue.message
                }
                when (fetchedComments) {
                    is FeedbackWorkerApi.Result.Ok -> comments = fetchedComments.value
                    is FeedbackWorkerApi.Result.Err -> if (error == null) error = fetchedComments.message
                }
                loading = false
            }
        }
    }

    LaunchedEffect(report.number) { refresh() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(issue?.title ?: report.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "#${report.number} • ${formatDate(issue?.createdAt ?: report.createdAt)} • GitHub: ${issue?.state ?: report.status}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )
                    }
                    StatusBadge(isOpen = !(issue?.state ?: report.status).equals("closed", ignoreCase = true))
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NeonGold)
                    }
                }
                Text(
                    "View on GitHub: ${(issue?.htmlUrl ?: report.htmlUrl)}",
                    fontSize = 11.sp,
                    color = NeonCyan,
                )
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonGold)
                    }
                } else if (error != null && issue == null) {
                    Text(error ?: "Failed to load.", fontSize = 12.sp, color = CoralRed)
                    Text("Showing cached report. Pull to retry with the refresh button.", fontSize = 12.sp, color = TextSecondary)
                } else {
                    error?.let { Text(it, fontSize = 12.sp, color = CoralRed) }
                    issue?.body?.takeIf { it.isNotBlank() }?.let {
                        Text(it.take(2000), fontSize = 12.sp, color = TextSecondary, maxLines = 12)
                    }
                    Text("COMMENTS (${comments.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(comments, key = { it.id }) { comment ->
                            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF0C1220), border = BorderStroke(1.dp, CardBorder)) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "${comment.user.login} • ${formatDate(comment.createdAt)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan,
                                    )
                                    Text(comment.body.take(4000), fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }
                        if (comments.isEmpty()) {
                            item { Text("No comments yet.", fontSize = 12.sp, color = TextMuted) }
                        }
                    }
                    OutlinedTextField(
                        value = reply,
                        onValueChange = { reply = it; postError = null },
                        label = { Text("Write a reply…") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = FeedbackFieldColors,
                    )
                    ImagePickerRow(
                        imageUri = replyImageUri,
                        onPick = { pickReplyImage.launch("image/*") },
                        onClear = { replyImageUri = null },
                    )
                    postError?.let { Text(it, fontSize = 12.sp, color = CoralRed) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Close", color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                val cleanReply = reply.trim()
                                if (cleanReply.isBlank()) {
                                    postError = "Reply cannot be empty."
                                    return@Button
                                }
                                posting = true
                                postError = null
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        var attachmentUrl: String? = null
                                        replyImageUri?.let { uri ->
                                            val base64 = uriToBase64(context, uri).getOrElse {
                                                throw IllegalArgumentException(it.message ?: "Image read failed.")
                                            }
                                            val ext = extensionForUri(context, uri)
                                            when (val up = api.uploadAsset(uniqueCommentFileName(report.number, ext), base64)) {
                                                is FeedbackWorkerApi.Result.Ok ->
                                                    attachmentUrl = up.value.downloadUrl
                                                        ?: throw IllegalStateException("Upload returned no URL.")
                                                is FeedbackWorkerApi.Result.Err ->
                                                    throw IllegalStateException("Image upload failed: ${up.message}. Comment was not posted.")
                                            }
                                        }
                                        when (val posted = api.postComment(report.number, buildCommentBody(cleanReply, attachmentUrl))) {
                                            is FeedbackWorkerApi.Result.Ok -> {
                                                withContext(Dispatchers.Main) {
                                                    reply = ""
                                                    replyImageUri = null
                                                    posting = false
                                                    refresh()
                                                }
                                            }
                                            is FeedbackWorkerApi.Result.Err ->
                                                throw IllegalStateException(posted.message)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            postError = e.message ?: "Failed to post reply."
                                            posting = false
                                        }
                                    }
                                }
                            },
                            enabled = !posting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold, contentColor = Color.Black),
                        ) {
                            if (posting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
                            } else {
                                Text("Submit reply", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                // Issue number chip for quick reference.
                Row {
                    AssistChip(
                        onClick = onDismiss,
                        label = { Text("#${report.number}", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = TextMuted),
                    )
                }
            }
        }
    }
}

private fun formatDate(iso: String): String {
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val output = SimpleDateFormat("MMM d, yyyy", Locale.US)
        val normalized = iso.replace(Regex("\\.\\d+Z$"), "Z").replace(Regex("[+-]\\d{2}:?\\d{2}$"), "Z")
        val date: Date? = try {
            input.parse(normalized)
        } catch (_: Exception) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.take(10))
        }
        if (date != null) output.format(date) else iso.take(10)
    } catch (_: Exception) {
        iso.take(10)
    }
}
