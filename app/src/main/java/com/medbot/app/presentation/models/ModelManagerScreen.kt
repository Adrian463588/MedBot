package com.medbot.app.presentation.models

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(navController: NavController) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Online Download", "Local SAF Folder")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model AI Manager") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            Box(modifier = Modifier.fillMaxSize()) {
                if (selectedTabIndex == 0) {
                    OnlineDownloadTab()
                } else {
                    LocalSafTab()
                }
            }
        }
    }
}

@Composable
fun OnlineDownloadTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Gemma 4 E2B", style = MaterialTheme.typography.titleMedium)
                Text("Size: 2.5 GB • Format: LiteRT-LM", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { /* TODO: Start WorkManager Download */ }) {
                    Text("Download Bundle")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Gemma 2 2B", style = MaterialTheme.typography.titleMedium)
                Text("Size: 1.5 GB • Format: GGUF", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { /* TODO: Start WorkManager Download */ }) {
                    Text("Download Bundle")
                }
            }
        }
    }
}

@Composable
fun LocalSafTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("No local folder selected yet.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { /* TODO: Launch ACTION_OPEN_DOCUMENT_TREE */ }) {
            Text("Pilih Folder Model Lokal")
        }
    }
}
