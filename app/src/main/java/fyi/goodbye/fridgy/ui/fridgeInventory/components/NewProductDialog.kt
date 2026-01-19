package fyi.goodbye.fridgy.ui.fridgeInventory.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.SizeUnit
import fyi.goodbye.fridgy.ui.shared.CategoryViewModel
import java.io.File

/**
 * Dialog for entering details for a new, unknown product.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewProductDialog(
    upc: String,
    onConfirm: (String, String, String, Uri?, Double?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var productBrand by remember { mutableStateOf("") }
    var sizeText by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf<SizeUnit?>(null) }
    var showUnitDropdown by remember { mutableStateOf(false) }
    var customUnit by remember { mutableStateOf("") }
    var showCustomUnitInput by remember { mutableStateOf(false) }

    // Load categories from database
    val categoryViewModel: CategoryViewModel = viewModel(factory = CategoryViewModel.provideFactory())
    val categoryState by categoryViewModel.uiState.collectAsState()

    // Get the fallback category from string resources
    val fallbackCategory = stringResource(R.string.category_other)

    val categories =
        when (val state = categoryState) {
            is CategoryViewModel.CategoryUiState.Success -> state.categories.map { it.name }
            else -> listOf(fallbackCategory) // Fallback if categories haven't loaded yet
        }

    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: fallbackCategory) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current

    // OPTIMIZATION: Memoize file paths to avoid file system calls on every recomposition
    val (tempFile, tempUri) =
        remember {
            val file = File(context.cacheDir, "temp_product_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "fyi.goodbye.fridgy.fileprovider", file)
            file to uri
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) capturedImageUri = tempUri
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.new_product_detected),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.product_not_recognized, upc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onClick = { cameraLauncher.launch(tempUri) }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedImageUri != null) {
                            AsyncImage(
                                model = capturedImageUri,
                                contentDescription = stringResource(R.string.cd_captured_product),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    stringResource(R.string.take_product_photo),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = productBrand,
                    onValueChange = { productBrand = it },
                    label = { Text(stringResource(R.string.brand_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Size/Unit Section
                Text(
                    "Size/Unit (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Size input
                    OutlinedTextField(
                        value = sizeText,
                        onValueChange = { sizeText = it },
                        label = { Text("Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )
                    
                    // Unit dropdown
                    ExposedDropdownMenuBox(
                        expanded = showUnitDropdown,
                        onExpandedChange = { showUnitDropdown = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = when {
                                showCustomUnitInput -> customUnit
                                selectedUnit != null -> selectedUnit!!.displayName
                                else -> ""
                            },
                            onValueChange = {
                                if (showCustomUnitInput) {
                                    customUnit = it
                                }
                            },
                            readOnly = !showCustomUnitInput,
                            label = { Text("Unit") },
                            trailingIcon = {
                                if (!showCustomUnitInput) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitDropdown)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )
                        
                        ExposedDropdownMenu(
                            expanded = showUnitDropdown,
                            onDismissRequest = { showUnitDropdown = false }
                        ) {
                            SizeUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.displayName) },
                                    onClick = {
                                        selectedUnit = unit
                                        showUnitDropdown = false
                                        showCustomUnitInput = (unit == SizeUnit.OTHER)
                                        if (unit != SizeUnit.OTHER) {
                                            customUnit = ""
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.category),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = cat,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { 
                    val size = sizeText.toDoubleOrNull()
                    val unit = when {
                        showCustomUnitInput && customUnit.isNotBlank() -> customUnit
                        selectedUnit != null && selectedUnit != SizeUnit.OTHER -> selectedUnit!!.name
                        else -> null
                    }
                    onConfirm(productName, productBrand, selectedCategory, capturedImageUri, size, unit) 
                },
                enabled = productName.isNotBlank()
            ) {
                Text(stringResource(R.string.save_and_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
