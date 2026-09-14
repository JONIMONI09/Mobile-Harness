package com.jarves.mh.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WorkflowRunItem(
    val id: Long,
    val name: String,
    val headBranch: String,
    val headSha: String,
    val event: String,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String,
    val createdAt: String,
    val runNumber: Int,
)

data class PullRequestReviewItem(
    val id: Long,
    val pullNumber: Int,
    val pullTitle: String,
    val author: String,
    val state: String,
    val body: String,
    val submittedAt: String,
    val htmlUrl: String,
    val isCodeRabbit: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubDevDashboardScreen(
    token: String? = null,
    defaultRepo: String = "techjarves/Mobile-Harness",
    onTokenChanged: ((String?) -> Unit)? = null,
    getOAuthCredentials: (() -> Pair<String, String>)? = null,
    onSaveOAuthCredentials: ((String, String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repoOwnerAndName by rememberSaveable { mutableStateOf(defaultRepo) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedRuns by remember { mutableStateOf<List<WorkflowRunItem>>(emptyList()) }
    var prReviews by remember { mutableStateOf<List<PullRequestReviewItem>>(emptyList()) }

    var showAuthDialog by remember { mutableStateOf(false) }
    var authTab by remember { mutableIntStateOf(0) }
    var clientIdInput by remember { mutableStateOf(getOAuthCredentials?.invoke()?.first.orEmpty()) }
    var clientSecretInput by remember { mutableStateOf(getOAuthCredentials?.invoke()?.second.orEmpty()) }
    var patTokenInput by remember { mutableStateOf("") }

    fun fetchData() {
        val repo = repoOwnerAndName.trim().replace(" ", "").trim('/')
        val parts = repo.split('/')
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            errorMessage = "Please enter repository in owner/repo format."
            return
        }
        scope.launch {
            isLoading = true
            errorMessage = null
            val runsResult = fetchFailedWorkflowRuns(repo, token)
            val reviewsResult = fetchPullRequestReviews(repo, token)

            if (runsResult.isSuccess) {
                failedRuns = runsResult.getOrDefault(emptyList())
            }
            if (reviewsResult.isSuccess) {
                prReviews = reviewsResult.getOrDefault(emptyList())
            }

            if (runsResult.isFailure && reviewsResult.isFailure) {
                errorMessage = runsResult.exceptionOrNull()?.message
                    ?: reviewsResult.exceptionOrNull()?.message
                    ?: "Failed to load GitHub dashboard data."
            }
            isLoading = false
        }
    }

    LaunchedEffect(defaultRepo) {
        if (repoOwnerAndName.isNotBlank() && repoOwnerAndName.contains("/")) {
            fetchData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GitHub Dev Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Actions Runs & CodeRabbit Reviews",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { fetchData() }, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = repoOwnerAndName,
                onValueChange = { repoOwnerAndName = it },
                label = { Text("Repository (owner/repo)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { fetchData() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { fetchData() },
                enabled = !isLoading && repoOwnerAndName.isNotBlank(),
            ) {
                Text("Fetch")
            }
        }

        if (token.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unauthenticated: 60 req/hr rate limit.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { showAuthDialog = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    ) {
                        Text("Connect", fontSize = 12.sp)
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Authenticated with GitHub (5,000 req/hr)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onTokenChanged?.invoke(null)
                            fetchData()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Disconnect", fontSize = 12.sp)
                    }
                }
            }
        }

        if (showAuthDialog) {
            AlertDialog(
                onDismissRequest = { showAuthDialog = false },
                title = { Text("GitHub Authentication") },
                text = {
                    Column {
                        TabRow(selectedTabIndex = authTab) {
                            Tab(
                                selected = authTab == 0,
                                onClick = { authTab = 0 },
                                text = { Text("OAuth Login") },
                            )
                            Tab(
                                selected = authTab == 1,
                                onClick = { authTab = 1 },
                                text = { Text("Personal Token") },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (authTab == 0) {
                            Text(
                                "Log in via GitHub OAuth to view private actions and reviews.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = clientIdInput,
                                onValueChange = { clientIdInput = it },
                                label = { Text("Client ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = clientSecretInput,
                                onValueChange = { clientSecretInput = it },
                                label = { Text("Client Secret") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (clientIdInput.isNotBlank()) {
                                        onSaveOAuthCredentials?.invoke(clientIdInput.trim(), clientSecretInput.trim())
                                        val authUrl = "https://github.com/login/oauth/authorize?client_id=${clientIdInput.trim()}&redirect_uri=mobileharness://github-callback&scope=repo,workflow,read:org"
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            })
                                        }
                                        showAuthDialog = false
                                    }
                                },
                                enabled = clientIdInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open Browser Login")
                            }
                        } else {
                            Text(
                                "Paste a GitHub Personal Access Token with repo and workflow scopes.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = patTokenInput,
                                onValueChange = { patTokenInput = it },
                                label = { Text("Personal Access Token") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (patTokenInput.isNotBlank()) {
                                        onTokenChanged?.invoke(patTokenInput.trim())
                                        showAuthDialog = false
                                        fetchData()
                                    }
                                },
                                enabled = patTokenInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Save Token")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAuthDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Failed Actions (${failedRuns.size})") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("PR Reviews (${prReviews.size})") },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && failedRuns.isEmpty() && prReviews.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            when (selectedTab) {
                0 -> {
                    if (failedRuns.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF22C55E),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No failed workflow runs found!", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "All recent actions runs completed successfully.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(failedRuns, key = { it.id }) { run ->
                                WorkflowRunCard(run = run, onOpenUrl = { url ->
                                    if (url.isNotBlank()) {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
                1 -> {
                    if (prReviews.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No PR reviews found", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Open PRs or reviews will appear here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(prReviews, key = { it.id }) { review ->
                                PullRequestReviewCard(review = review, onOpenUrl = { url ->
                                    if (url.isNotBlank()) {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkflowRunCard(
    run: WorkflowRunItem,
    onOpenUrl: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(run.htmlUrl) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${run.name} #${run.runNumber}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = run.conclusion?.uppercase() ?: "FAILURE",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Branch: ${run.headBranch} (${run.headSha.take(7)})",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Created: ${run.createdAt.take(10)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View on GitHub",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PullRequestReviewCard(
    review: PullRequestReviewItem,
    onOpenUrl: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(review.htmlUrl) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "PR #${review.pullNumber}: ${review.pullTitle}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (review.isCodeRabbit) {
                    Surface(
                        color = Color(0xFFF97316).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = Color(0xFFF97316),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CodeRabbit AI",
                                color = Color(0xFFF97316),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Reviewer: ${review.author}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = review.state,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            if (review.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = review.body.trim(),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = review.submittedAt.take(10),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View Review",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

private fun formatGitHubErrorMessage(code: Int, body: String): String {
    return try {
        val json = JSONObject(body)
        val msg = json.optString("message", "")
        if (code == 403 && msg.contains("rate limit", ignoreCase = true)) {
            "GitHub API rate limit exceeded (60 req/hr). Sign in with OAuth or a Personal Access Token to get 5,000 req/hr."
        } else if (msg.isNotBlank()) {
            "GitHub API ($code): $msg"
        } else {
            "GitHub API HTTP $code"
        }
    } catch (_: Exception) {
        "GitHub API HTTP $code: ${body.take(120)}"
    }
}

suspend fun fetchFailedWorkflowRuns(
    repo: String,
    token: String?,
): Result<List<WorkflowRunItem>> = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.github.com/repos/$repo/actions/runs?status=failure&per_page=20"
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "MobileHarness-App")

        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val (code, body, isSuccessful) = okHttpClient.newCall(requestBuilder.build()).execute().use {
            Triple(it.code, it.body?.string().orEmpty(), it.isSuccessful)
        }

        if (!isSuccessful) {
            return@withContext Result.failure(IOException(formatGitHubErrorMessage(code, body)))
        }

        val json = JSONObject(body)
        val runsArray = json.optJSONArray("workflow_runs") ?: JSONArray()
        val runs = mutableListOf<WorkflowRunItem>()

        for (i in 0 until runsArray.length()) {
            val obj = runsArray.optJSONObject(i) ?: continue
            val conclusion = obj.optString("conclusion")
            if (conclusion.equals("failure", ignoreCase = true) || conclusion.equals("cancelled", ignoreCase = true)) {
                runs.add(
                    WorkflowRunItem(
                        id = obj.optLong("id"),
                        name = obj.optString("name", "Workflow"),
                        headBranch = obj.optString("head_branch", "main"),
                        headSha = obj.optString("head_sha", ""),
                        event = obj.optString("event", "push"),
                        status = obj.optString("status", "completed"),
                        conclusion = conclusion,
                        htmlUrl = obj.optString("html_url", ""),
                        createdAt = obj.optString("created_at", ""),
                        runNumber = obj.optInt("run_number", 0),
                    )
                )
            }
        }

        Result.success(runs)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun fetchPullRequestReviews(
    repo: String,
    token: String?,
): Result<List<PullRequestReviewItem>> = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.github.com/repos/$repo/pulls?state=all&per_page=5&sort=updated&direction=desc"
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "MobileHarness-App")

        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val (code, body, isSuccessful) = okHttpClient.newCall(requestBuilder.build()).execute().use {
            Triple(it.code, it.body?.string().orEmpty(), it.isSuccessful)
        }

        if (!isSuccessful) {
            return@withContext Result.failure(IOException(formatGitHubErrorMessage(code, body)))
        }

        val pullsArray = JSONArray(body)
        val count = minOf(pullsArray.length(), 5)
        val reviewsList = coroutineScope {
            val deferreds = (0 until count).map { i ->
                async {
                    val prObj = pullsArray.optJSONObject(i) ?: return@async emptyList<PullRequestReviewItem>()
                    val prNumber = prObj.optInt("number")
                    val prTitle = prObj.optString("title")
                    val prUrl = prObj.optString("html_url")
                    val items = mutableListOf<PullRequestReviewItem>()

                    val reviewsUrl = "https://api.github.com/repos/$repo/pulls/$prNumber/reviews?per_page=10"
                    val revReq = Request.Builder()
                        .url(reviewsUrl)
                        .addHeader("Accept", "application/vnd.github+json")
                        .addHeader("User-Agent", "MobileHarness-App")
                        .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
                        .build()

                    runCatching {
                        okHttpClient.newCall(revReq).execute().use { revResp ->
                            if (revResp.isSuccessful) {
                                val revBody = revResp.body?.string().orEmpty()
                                if (revBody.isNotBlank()) {
                                    val revArray = JSONArray(revBody)
                                    for (j in 0 until revArray.length()) {
                                        val revObj = revArray.optJSONObject(j) ?: continue
                                        val userObj = revObj.optJSONObject("user")
                                        val authorName = userObj?.optString("login", "reviewer") ?: "reviewer"
                                        val isCodeRabbit = authorName.contains("coderabbit", ignoreCase = true) ||
                                            revObj.optString("body").contains("coderabbit", ignoreCase = true)

                                        items.add(
                                            PullRequestReviewItem(
                                                id = revObj.optLong("id"),
                                                pullNumber = prNumber,
                                                pullTitle = prTitle,
                                                author = authorName,
                                                state = revObj.optString("state", "COMMENTED"),
                                                body = revObj.optString("body", ""),
                                                submittedAt = revObj.optString("submitted_at", ""),
                                                htmlUrl = revObj.optString("html_url", prUrl),
                                                isCodeRabbit = isCodeRabbit,
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val commentsUrl = "https://api.github.com/repos/$repo/issues/$prNumber/comments?per_page=10"
                    val commReq = Request.Builder()
                        .url(commentsUrl)
                        .addHeader("Accept", "application/vnd.github+json")
                        .addHeader("User-Agent", "MobileHarness-App")
                        .apply { if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token") }
                        .build()

                    runCatching {
                        okHttpClient.newCall(commReq).execute().use { commResp ->
                            if (commResp.isSuccessful) {
                                val commBody = commResp.body?.string().orEmpty()
                                if (commBody.isNotBlank()) {
                                    val commArray = JSONArray(commBody)
                                    for (k in 0 until commArray.length()) {
                                        val commObj = commArray.optJSONObject(k) ?: continue
                                        val userObj = commObj.optJSONObject("user")
                                        val authorName = userObj?.optString("login", "commenter") ?: "commenter"
                                        val isCodeRabbit = authorName.contains("coderabbit", ignoreCase = true) ||
                                            commObj.optString("body").contains("coderabbit", ignoreCase = true)

                                        if (isCodeRabbit) {
                                            items.add(
                                                PullRequestReviewItem(
                                                    id = commObj.optLong("id"),
                                                    pullNumber = prNumber,
                                                    pullTitle = prTitle,
                                                    author = authorName,
                                                    state = "COMMENT",
                                                    body = commObj.optString("body", ""),
                                                    submittedAt = commObj.optString("created_at", ""),
                                                    htmlUrl = commObj.optString("html_url", prUrl),
                                                    isCodeRabbit = true,
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items
                }
            }
            deferreds.awaitAll().flatten()
        }

        Result.success(reviewsList)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
