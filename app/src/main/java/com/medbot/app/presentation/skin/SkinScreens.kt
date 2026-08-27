@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.medbot.app.presentation.skin

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medbot.app.R
import com.medbot.app.core.designsystem.components.AdaptiveContent
import com.medbot.app.core.designsystem.components.AdaptiveFlowRow
import com.medbot.app.core.designsystem.components.AdaptiveListDetail
import com.medbot.app.core.designsystem.components.InteractiveSkinSplitComparator
import com.medbot.app.core.designsystem.components.MedBotSpacing
import com.medbot.app.core.designsystem.components.MedBotTopAppBar
import com.medbot.app.core.designsystem.components.UrgencyBadge
import com.medbot.app.core.designsystem.components.springBounceClick
import com.medbot.app.domain.model.SkinRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SkinBodyPartOption(val id: String, val labelRes: Int)

private val BODY_PARTS = listOf(
    SkinBodyPartOption("all", R.string.body_part_all),
    SkinBodyPartOption("face", R.string.body_part_face),
    SkinBodyPartOption("neck", R.string.body_part_neck),
    SkinBodyPartOption("chest", R.string.body_part_chest),
    SkinBodyPartOption("back", R.string.body_part_back),
    SkinBodyPartOption("left_arm", R.string.body_part_left_arm),
    SkinBodyPartOption("right_arm", R.string.body_part_right_arm),
    SkinBodyPartOption("hand", R.string.body_part_hand),
    SkinBodyPartOption("leg", R.string.body_part_leg),
    SkinBodyPartOption("foot", R.string.body_part_foot)
)

@Composable
fun SkinScanScreen(viewModel: SkinViewModel, onNavigateBack: () -> Unit, onNavigateToLineage: () -> Unit) {
    val context = LocalContext.current
    var selectedPart by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val imagePath by viewModel.selectedImagePath.collectAsStateWithLifecycle()
    val captureTarget by viewModel.captureTarget.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val currentAnalysis by viewModel.currentAnalysis.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onEvent(SkinUiEvent.CameraResult(success))
    }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onEvent(SkinUiEvent.PrepareCameraCapture)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.onEvent(SkinUiEvent.ImportImage(uri.toString()))
    }

    LaunchedEffect(captureTarget) {
        captureTarget?.let { takePicture.launch(Uri.parse(it.uri)) }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.onEvent(SkinUiEvent.PrepareCameraCapture)
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    Column {
        MedBotTopAppBar(
            title = stringResource(R.string.skin_title),
            subtitle = stringResource(R.string.skin_subtitle),
            navigationIcon = { IconButton(onClick = onNavigateBack, modifier = Modifier.springBounceClick()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            actions = { IconButton(onClick = onNavigateToLineage, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.Timeline, contentDescription = stringResource(R.string.skin_lineage)) } }
        )
        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 840.dp) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = MedBotSpacing.medium, vertical = MedBotSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(MedBotSpacing.large)
            ) {
                if (analysisState.status != SkinAnalysisUiStatus.IDLE) {
                    item {
                        Surface(color = if (analysisState.status == SkinAnalysisUiStatus.FAILED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(MedBotSpacing.medium)) {
                                Text(
                                    text = when (analysisState.status) {
                                        SkinAnalysisUiStatus.UNAVAILABLE -> stringResource(R.string.skin_unavailable)
                                        SkinAnalysisUiStatus.INSUFFICIENT_DATA -> stringResource(R.string.skin_insufficient_data)
                                        SkinAnalysisUiStatus.FAILED -> stringResource(R.string.skin_failed_generic)
                                        SkinAnalysisUiStatus.IDLE -> ""
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when (analysisState.reason) {
                                        SkinAnalysisReason.MODEL_UNAVAILABLE -> stringResource(R.string.skin_reason_model)
                                        SkinAnalysisReason.INPUT_REQUIRED -> stringResource(R.string.skin_reason_input)
                                        SkinAnalysisReason.FAILED -> stringResource(R.string.skin_reason_failed)
                                        null -> ""
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = MedBotSpacing.small)
                                )
                            }
                        }
                    }
                }
                item {
                    val bitmap = rememberBitmap(imagePath)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        if (bitmap != null) {
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = stringResource(R.string.skin_photo_description), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(240.dp).clip(MaterialTheme.shapes.large))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(MedBotSpacing.large)) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                                Text(stringResource(R.string.skin_photo_heading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = MedBotSpacing.small))
                                Text(stringResource(R.string.skin_photo_support), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = MedBotSpacing.xSmall))
                            }
                        }
                    }
                    AdaptiveFlowRow(modifier = Modifier.fillMaxWidth().padding(top = MedBotSpacing.small)) {
                        Button(onClick = ::launchCamera, enabled = !isImporting, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.PhotoCamera, contentDescription = null); Spacer(modifier = Modifier.width(MedBotSpacing.small)); Text(stringResource(R.string.skin_camera)) }
                        OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = !isImporting, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.PhotoLibrary, contentDescription = null); Spacer(modifier = Modifier.width(MedBotSpacing.small)); Text(stringResource(R.string.skin_gallery)) }
                        if (isImporting) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                item {
                    SkinBodyPartDropdownSelector(
                        selectedPart = selectedPart,
                        onSelectPart = { selectedPart = it }
                    )
                }
                item {
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.skin_notes_label)) }, supportingText = { Text(stringResource(R.string.skin_notes_support)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                item {
                    Button(
                         onClick = { viewModel.onEvent(SkinUiEvent.Analyze(selectedPart, notes)) },
                        enabled = imagePath != null && selectedPart.isNotBlank() && !isAnalyzing && !isImporting,
                        modifier = Modifier.fillMaxWidth().springBounceClick(),
                        contentPadding = PaddingValues(vertical = MedBotSpacing.medium)
                    ) {
                        if (isAnalyzing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) else Icon(Icons.Filled.Analytics, contentDescription = null)
                        Spacer(modifier = Modifier.width(MedBotSpacing.small))
                        Text(if (isAnalyzing) stringResource(R.string.skin_analyzing) else stringResource(R.string.skin_analyze))
                    }
                }
                currentAnalysis?.let { record -> item { SkinResultCard(record) } }
            }
        }
    }
}

@Composable
fun SkinLineageScreen(viewModel: SkinViewModel, onNavigateBack: () -> Unit, onNavigateToScan: () -> Unit) {
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val selectedPart by viewModel.selectedBodyPart.collectAsStateWithLifecycle()
    val filtered = remember(records, selectedPart) { if (selectedPart == "all") records else records.filter { it.bodyPart == selectedPart } }
    var selectedRecordId by remember { mutableStateOf<String?>(null) }
    val selectedRecord = filtered.firstOrNull { it.id == selectedRecordId }
    val isSinglePane = LocalConfiguration.current.screenWidthDp < 840
    Column {
        MedBotTopAppBar(
            title = stringResource(R.string.skin_lineage_title),
            subtitle = stringResource(R.string.skin_lineage_subtitle),
             navigationIcon = { IconButton(onClick = { if (isSinglePane && selectedRecordId != null) selectedRecordId = null else onNavigateBack() }, modifier = Modifier.springBounceClick()) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
            actions = { IconButton(onClick = onNavigateToScan, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.skin_new_photo)) } }
        )
        AdaptiveContent(modifier = Modifier.weight(1f), maxContentWidth = 1_040.dp) {
            AdaptiveListDetail(
                modifier = Modifier.fillMaxWidth(),
                 selectedKey = selectedRecordId,
                 onBackFromDetail = { selectedRecordId = null },
                listPane = {
                    LazyColumn(contentPadding = PaddingValues(MedBotSpacing.medium), verticalArrangement = Arrangement.spacedBy(MedBotSpacing.medium)) {
                        item {
                            SkinBodyPartDropdownFilter(
                                selectedPart = selectedPart,
                                onSelectPart = { viewModel.onEvent(SkinUiEvent.SelectBodyPartFilter(it)) },
                                records = records
                            )
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(MedBotSpacing.large), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(R.string.skin_no_records), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.skin_no_records_support), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = MedBotSpacing.small))
                                        Button(onClick = onNavigateToScan, modifier = Modifier.padding(top = MedBotSpacing.medium).springBounceClick()) { Text(stringResource(R.string.skin_new_photo)) }
                                    }
                                }
                            }
                        } else {
                            if (filtered.size >= 2) {
                                item {
                                    val before = filtered.last()
                                    val after = filtered.first()
                                    InteractiveSkinSplitComparator(
                                        beforeBitmap = rememberBitmap(before.imagePath),
                                        afterBitmap = rememberBitmap(after.imagePath),
                                        beforeLabel = stringResource(R.string.skin_before),
                                        afterLabel = stringResource(R.string.skin_latest)
                                    )
                                }
                            }
                            items(filtered, key = { it.id }) { record ->
                                SkinLineageTimelineItem(
                                    record = record,
                                    onClick = { selectedRecordId = record.id },
                                    onDelete = {
                                        if (selectedRecordId == record.id) selectedRecordId = null
                                         viewModel.onEvent(SkinUiEvent.DeleteRecord(record.id))
                                    }
                                )
                            }
                        }
                    }
                },
                detailPane = {
                    if (selectedRecord == null) {
                        Surface(modifier = Modifier.fillMaxSize().padding(MedBotSpacing.medium), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                            Column(modifier = Modifier.padding(MedBotSpacing.large), verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
                                Text(stringResource(R.string.skin_detail_empty_title), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.skin_detail_empty_support), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(MedBotSpacing.medium)) { item { SkinResultCard(selectedRecord) } }
                    }
                }
            )
        }
    }
}

@Composable
fun SkinResultCard(record: SkinRecord) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(MedBotSpacing.medium), verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.skin_result_heading, skinBodyPartLabel(record.bodyPart)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); UrgencyBadge(record.urgencyLevel) }
            Text(stringResource(R.string.skin_risk_score, record.abcdEvaluation.totalRiskScore, record.abcdEvaluation.riskClassification), style = MaterialTheme.typography.bodyMedium)
            Text(record.clinicalSummary, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.skin_differential), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            record.differentialDiagnoses.forEach { diagnosis -> Text("• $diagnosis", style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.skin_advice), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            record.homeCareAdvice.forEach { advice -> Text("• $advice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun SkinLineageTimelineItem(record: SkinRecord, onClick: () -> Unit = {}, onDelete: () -> Unit) {
    val date = remember(record.createdAt) { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(record.createdAt)) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).springBounceClick()) {
        Row(modifier = Modifier.fillMaxWidth().padding(MedBotSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MedBotSpacing.xSmall)) {
                Text(skinBodyPartLabel(record.bodyPart), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.skin_risk_score, record.abcdEvaluation.totalRiskScore, record.abcdEvaluation.riskClassification), style = MaterialTheme.typography.bodySmall)
                if (record.userNotes.isNotBlank()) Text(record.userNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            UrgencyBadge(record.urgencyLevel)
            IconButton(onClick = onDelete, modifier = Modifier.springBounceClick()) { Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun rememberBitmap(path: String?): Bitmap? {
    val state by produceState<Bitmap?>(initialValue = null, key1 = path) {
        value = path?.let { filePath ->
            withContext(Dispatchers.IO) {
                File(filePath).takeIf { it.exists() }?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }
            }
        }
    }
    return state
}

@Composable
private fun skinBodyPartLabel(id: String): String {
    val option = BODY_PARTS.firstOrNull { it.id == id }
    return option?.let { stringResource(it.labelRes) } ?: id
}

@Composable
private fun SkinBodyPartDropdownFilter(
    selectedPart: String,
    onSelectPart: (String) -> Unit,
    records: List<SkinRecord>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val currentOption = remember(selectedPart) {
        BODY_PARTS.firstOrNull { it.id == selectedPart } ?: BODY_PARTS.first()
    }
    val currentLabel = stringResource(currentOption.labelRes)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Filter Area Tubuh",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(12.dp),
            color = if (selectedPart != "all") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (selectedPart != "all") MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MedBotSpacing.medium, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (selectedPart != "all") Icons.Filled.FilterList else Icons.Filled.Category,
                        contentDescription = null,
                        tint = if (selectedPart != "all") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (selectedPart == "all") "Semua Area Tubuh (${records.size} Foto)"
                        else "$currentLabel (${records.count { it.bodyPart == selectedPart }} Foto)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selectedPart != "all") FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedPart != "all") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedPart != "all") {
                        IconButton(
                            onClick = { onSelectPart("all") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Reset Filter",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    BODY_PARTS.forEach { part ->
                        val isSelected = selectedPart == part.id
                        val count = if (part.id == "all") records.size else records.count { it.bodyPart == part.id }
                        val label = stringResource(part.labelRes)

                        Surface(
                            onClick = {
                                onSelectPart(part.id)
                                isExpanded = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (count > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "$count",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinBodyPartDropdownSelector(
    selectedPart: String,
    onSelectPart: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val currentOption = remember(selectedPart) {
        BODY_PARTS.firstOrNull { it.id == selectedPart }
    }
    val currentLabel = currentOption?.let { stringResource(it.labelRes) } ?: ""

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MedBotSpacing.small)
    ) {
        Text(
            text = stringResource(R.string.skin_body_part),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.skin_body_part_required),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            onClick = { isExpanded = !isExpanded },
            shape = RoundedCornerShape(12.dp),
            color = if (selectedPart.isNotBlank()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                1.dp,
                if (selectedPart.isNotBlank()) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MedBotSpacing.medium, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessibilityNew,
                        contentDescription = null,
                        tint = if (selectedPart.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (selectedPart.isNotBlank()) currentLabel else "Pilih lokasi tubuh foto...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selectedPart.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedPart.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    BODY_PARTS.drop(1).forEach { part ->
                        val isSelected = selectedPart == part.id
                        val label = stringResource(part.labelRes)

                        Surface(
                            onClick = {
                                onSelectPart(part.id)
                                isExpanded = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
