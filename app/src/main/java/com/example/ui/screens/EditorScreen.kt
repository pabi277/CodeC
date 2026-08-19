package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.settings.SettingsManager
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.ui.components.SymbolBar
import com.example.ui.components.TerminalOutput
import com.example.ui.theme.ThemeManager
import com.example.ui.theme.EditorThemeType
import com.example.ui.theme.getEditorTheme
import com.example.ui.utils.CSyntaxVisualTransformation
import com.example.ui.viewmodels.EditorViewModel

import androidx.compose.runtime.LaunchedEffect

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    fileName: String? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    val settingsManager = remember { SettingsManager(context) }
    val currentEditorTheme by themeManager.editorThemeFlow.collectAsState(initial = EditorThemeType.DRACULA)
    val editorColors = getEditorTheme(currentEditorTheme)
    
    val fontSize by settingsManager.fontSizeFlow.collectAsState(initial = 14f)
    val showLineNumbers by settingsManager.lineNumbersFlow.collectAsState(initial = true)
    val wordWrap by settingsManager.wordWrapFlow.collectAsState(initial = false)
    
    LaunchedEffect(fileName) {
        if (fileName != null) {
            viewModel.loadFile(context, fileName)
            // Add file to recent files
            settingsManager.addRecentFile(fileName)
        }
    }

    val codeText by viewModel.codeText.collectAsState()
    val currentFileName by viewModel.fileName.collectAsState()
    val terminalSegments by viewModel.terminalSegments.collectAsState()
    val isTerminalExpanded by viewModel.isTerminalExpanded.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isDirty) {
        showUnsavedDialog = true
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(currentFileName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("File Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        val name = if (newName.endsWith(".c")) newName else "$newName.c"
                        viewModel.updateFileName(name)
                    }
                    showRenameDialog = false
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes. Do you want to save before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveFile(context)
                    showUnsavedDialog = false
                    onNavigateBack()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onNavigateBack()
                }) {
                    Text("Discard")
                }
            }
        )
    }
    
    // A dark theme color specifically for the code editor area
    val editorBackgroundColor = editorColors.background
    val lineNumberColor = Color(0xFF858585)
    val codeTextColor = editorColors.text

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = currentFileName + if (isDirty) " *" else "",
                        modifier = Modifier.clickable { showRenameDialog = true },
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isDirty) showUnsavedDialog = true else onNavigateBack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.runCode() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.padding(end = 4.dp))
                        Text("RUN")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* TODO */ }) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo") }
                IconButton(onClick = { /* TODO */ }) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo") }
                IconButton(onClick = { /* TODO */ }) { Icon(Icons.Default.AutoFixHigh, contentDescription = "Format") }
                IconButton(onClick = { /* TODO */ }) { Icon(Icons.Default.Search, contentDescription = "Find") }
                IconButton(onClick = { 
                    if (viewModel.saveFile(context)) {
                        Toast.makeText(context, "File saved", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Default.Save, contentDescription = "Save") }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { /* TODO */ }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
            }
            
            HorizontalDivider()

            // Main Editor Area
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(editorBackgroundColor)
            ) {
                Row(modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .then(if (!wordWrap) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                    .padding(vertical = 8.dp)
                ) {
                    // Line numbers
                    if (showLineNumbers) {
                        val lineCount = codeText.text.count { it == '\n' } + 1
                        val lineNumbers = (1..lineCount).joinToString("\n")
                        Text(
                            text = lineNumbers,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                color = lineNumberColor,
                                textAlign = TextAlign.End
                            ),
                            modifier = Modifier
                                .width(40.dp)
                                .padding(end = 8.dp)
                        )
                    }
                    
                    // Code Text Field
                    BasicTextField(
                        value = codeText,
                        onValueChange = { viewModel.updateCode(it) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            color = codeTextColor
                        ),
                        visualTransformation = CSyntaxVisualTransformation(currentEditorTheme),
                        cursorBrush = SolidColor(codeTextColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Symbol Bar
            SymbolBar(
                textFieldValue = codeText,
                onValueChange = { viewModel.updateCode(it) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )

            // Terminal Panel
            AnimatedVisibility(visible = isTerminalExpanded) {
                TerminalOutput(
                    segments = terminalSegments,
                    onClear = { viewModel.clearTerminal() },
                    onToggleExpand = { viewModel.toggleTerminal() },
                    isExpanded = isTerminalExpanded,
                    modifier = Modifier.height(200.dp)
                )
            }
            if (!isTerminalExpanded) {
                TerminalOutput(
                    segments = terminalSegments.takeLast(1),
                    onClear = { viewModel.clearTerminal() },
                    onToggleExpand = { viewModel.toggleTerminal() },
                    isExpanded = isTerminalExpanded,
                    modifier = Modifier.height(64.dp)
                )
            }
        }
    }
}
