package com.medbot.app.presentation.knowledge

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.AdaptiveListDetail
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.model.RagDocument
import com.medbot.app.domain.model.SearchResult
import com.medbot.app.data.rag.BankBookCorpusManifest
import com.medbot.app.data.rag.BundledKnowledgeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KnowledgeBaseScreen(viewModel: KnowledgeViewModel, onNavigateBack: () -> Unit) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isIngesting by viewModel.isIngesting.collectAsStateWithLifecycle()
    val message by viewModel.ingestMessage.collectAsStateWithLifecycle()
    val bundledKnowledgeState by viewModel.bundledKnowledgeState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDocument = documents.firstOrNull { it.id == selectedDocumentId }
    val isSinglePane = LocalConfiguration.current.screenWidthDp < 840

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.onEvent(KnowledgeUiEvent.IngestDocument(uri.toString()))
    }
    val launchPicker = {
        picker.launch(
            arrayOf(
                "application/pdf",
                "text/plain",
                "text/markdown",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/json",
                "application/x-ndjson"
            )
        )
    }

    Column {
        MedBotTopAppBar(
            title = stringResource(R.string.knowledge_title),
            subtitle = stringResource(R.string.knowledge_subtitle),
            navigationIcon = {
                IconButton(onClick = { if (isSinglePane && selectedDocumentId != null) selectedDocumentId = null else onNavigateBack() }, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = launchPicker, enabled = !isIngesting, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = stringResource(R.string.knowledge_import))
                }
            }
        )
        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 1_040.dp) {
            AdaptiveListDetail(
                modifier = Modifier.fillMaxSize(),
                 selectedKey = selectedDocumentId,
                 onBackFromDetail = { selectedDocumentId = null },
                listPane = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(MedBotSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                                Text(stringResource(R.string.knowledge_import_heading), style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    stringResource(R.string.knowledge_import_support),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                BundledKnowledgeStatus(bundledKnowledgeState)
                                Button(
                                    onClick = launchPicker,
                                    enabled = !isIngesting,
                                    modifier = Modifier.fillMaxWidth().springBounceClick()
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                                    Spacer(Modifier.width(MedBotSpacing.small))
                                    Text(stringResource(R.string.knowledge_import))
                                }
                                if (isIngesting) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(MedBotSpacing.small))
                                        Text(stringResource(R.string.knowledge_processing), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (message != null && !isIngesting) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = messageText(message = message),
                                            modifier = Modifier.padding(MedBotSpacing.medium),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = query,
                                 onValueChange = { query = it; viewModel.onEvent(KnowledgeUiEvent.Search(it)) },
                                label = { Text(stringResource(R.string.knowledge_search_label)) },
                                supportingText = { Text(stringResource(R.string.knowledge_search_support)) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        if (searchResults.isNotEmpty()) {
                            item { Text(stringResource(R.string.knowledge_search_results, searchResults.size), style = MaterialTheme.typography.titleMedium) }
                            items(searchResults, key = { it.chunk.id }) { result ->
                                SearchResultRow(result) {
                                    selectedDocumentId = documents.firstOrNull { it.fileName == result.documentTitle }?.id
                                }
                            }
                        }
                        item { Text(stringResource(R.string.knowledge_documents_heading, documents.size), style = MaterialTheme.typography.titleLarge) }
                        if (documents.isEmpty()) {
                            item {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.knowledge_no_documents), modifier = Modifier.padding(MedBotSpacing.large), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        } else {
                            items(documents, key = { it.id }) { document ->
                                DocumentRow(
                                    document = document,
                                    selected = document.id == selectedDocumentId,
                                    onSelect = { selectedDocumentId = document.id },
                                    onDelete = {
                                        if (selectedDocumentId == document.id) selectedDocumentId = null
                                         viewModel.onEvent(KnowledgeUiEvent.DeleteDocument(document.id))
                                    },
                                    allowDelete = !document.fileUri.startsWith("asset://")
                                )
                            }
                        }
                    }
                },
                detailPane = {
                    if (selectedDocument == null) {
                        Surface(
                            modifier = Modifier.fillMaxSize().padding(MedBotSpacing.medium),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(MedBotSpacing.large), verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                                Text(stringResource(R.string.knowledge_detail_empty_title), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.knowledge_detail_empty_support), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        KnowledgeDocumentDetail(selectedDocument)
                    }
                }
            )
        }
    }
}

@Composable
private fun BundledKnowledgeStatus(state: BundledKnowledgeState) {
    val (title, body, color) = when (state) {
        BundledKnowledgeState.NotStarted -> Triple(
            stringResource(R.string.knowledge_bankbook_title),
            stringResource(R.string.knowledge_bankbook_waiting),
            MaterialTheme.colorScheme.surfaceVariant
        )
        BundledKnowledgeState.Indexing -> Triple(
            stringResource(R.string.knowledge_bankbook_title),
            stringResource(R.string.knowledge_bankbook_indexing),
            MaterialTheme.colorScheme.secondaryContainer
        )
        is BundledKnowledgeState.Ready -> Triple(
            stringResource(R.string.knowledge_bankbook_ready_title),
            stringResource(
                R.string.knowledge_bankbook_ready,
                state.document.chunkCount,
                BankBookCorpusManifest.RECORD_COUNT,
                BankBookCorpusManifest.SHA256
            ),
            MaterialTheme.colorScheme.primaryContainer
        )
        is BundledKnowledgeState.Failed -> Triple(
            stringResource(R.string.knowledge_bankbook_failed_title),
            stringResource(R.string.knowledge_bankbook_failed, state.reason),
            MaterialTheme.colorScheme.errorContainer
        )
    }
    Surface(
        color = color,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(MedBotSpacing.medium), verticalArrangement = Arrangement.spacedBy(MedBotSpacing.xSmall)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall)
            Text(
                text = stringResource(R.string.knowledge_bankbook_source, BankBookCorpusManifest.SOURCE, BankBookCorpusManifest.VERSION),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun messageText(message: KnowledgeMessage?): String {
    return when (message?.kind) {
        KnowledgeMessageKind.PROCESSING -> stringResource(
            R.string.knowledge_processing_file,
            message.fileName.orEmpty()
        )
        KnowledgeMessageKind.INDEXED -> stringResource(
            R.string.knowledge_indexed,
            message.chunkCount ?: 0
        )
        KnowledgeMessageKind.EMBEDDER_UNAVAILABLE -> stringResource(R.string.knowledge_embedder_unavailable)
        KnowledgeMessageKind.DOCUMENT_UNAVAILABLE -> stringResource(R.string.knowledge_document_unavailable)
        KnowledgeMessageKind.FAILED -> stringResource(R.string.knowledge_error)
        null -> ""
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
         modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).springBounceClick()
    ) {
        Column(modifier = Modifier.padding(MedBotSpacing.medium)) {
            Text(result.documentTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(result.chunk.textContent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = MedBotSpacing.small))
            Text(stringResource(R.string.knowledge_score, result.similarityScore), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = MedBotSpacing.small))
        }
    }
}

@Composable
private fun DocumentRow(
    document: RagDocument,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    allowDelete: Boolean
) {
    val date = rememberDocumentDate(document.indexedAt)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
         modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSelect).springBounceClick()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(MedBotSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(MedBotSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(document.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val metadata = if (document.pageCount > 0) {
                    stringResource(R.string.knowledge_document_meta, document.chunkCount, document.pageCount, date)
                } else {
                    stringResource(R.string.knowledge_document_meta_unknown_pages, document.chunkCount, date)
                }
                Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (allowDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.springBounceClick()) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeDocumentDetail(document: RagDocument) {
    Surface(
        modifier = Modifier.fillMaxSize().padding(MedBotSpacing.medium),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(MedBotSpacing.large), verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
            Text(document.fileName, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.knowledge_detail_meta, document.mimeType, document.fileSize), style = MaterialTheme.typography.bodyMedium)
            DetailValue(stringResource(R.string.knowledge_detail_checksum), document.sha256)
            DetailValue(stringResource(R.string.knowledge_detail_source), document.fileUri)
            Text(stringResource(R.string.knowledge_detail_provenance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MedBotSpacing.xSmall)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun rememberDocumentDate(timestamp: Long): String {
    return remember(timestamp) { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp)) }
}
