package fyi.goodbye.fridgy.ui.itemDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.elements.ExpirationDateDialog
import fyi.goodbye.fridgy.ui.shared.components.LoadingState
import fyi.goodbye.fridgy.ui.shared.components.SimpleErrorState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    fridgeId: String,
    itemId: String,
    onBackClick: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val userNames by viewModel.userNames.collectAsState()
    val pendingItemForDate by viewModel.pendingItemForDate.collectAsState()
    val pendingItemForEdit by viewModel.pendingItemForEdit.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    // Navigate back when all items are deleted
    LaunchedEffect(uiState) {
        if (uiState is ItemDetailViewModel.ItemDetailUiState.Success) {
            val items = (uiState as ItemDetailViewModel.ItemDetailUiState.Success).items
            if (items.isEmpty()) {
                onBackClick()
            }
        }
    }

    // Show date picker dialog when needed
    pendingItemForDate?.let { upc ->
        LaunchedEffect(upc) {
            val product = viewModel.getProductForDisplay(upc)
            if (product != null) {
                // Dialog will be shown below
            }
        }

        val currentState = uiState
        if (currentState is ItemDetailViewModel.ItemDetailUiState.Success) {
            ExpirationDateDialog(
                productName = currentState.product.name,
                onDateSelected = { date ->
                    viewModel.addNewInstanceWithDate(date)
                },
                onDismiss = {
                    viewModel.cancelDatePicker()
                }
            )
        }
    }

    // Show date picker dialog for editing existing item
    pendingItemForEdit?.let { item ->
        val currentState = uiState
        if (currentState is ItemDetailViewModel.ItemDetailUiState.Success) {
            ExpirationDateDialog(
                productName = currentState.product.name,
                initialDate = item.expirationDate,
                onDateSelected = { date ->
                    viewModel.updateItemExpiration(date)
                },
                onDismiss = {
                    viewModel.cancelDatePicker()
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.item_details),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
            )
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            when (val state = uiState) {
                ItemDetailViewModel.ItemDetailUiState.Loading -> {
                    LoadingState()
                }
                is ItemDetailViewModel.ItemDetailUiState.Error -> {
                    SimpleErrorState(message = state.message)
                }
                is ItemDetailViewModel.ItemDetailUiState.Success -> {
                    val items = state.items
                    val product = state.product

                    // Show empty state while navigating back
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        return@Box
                    }

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Item Image
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (product.imageUrl != null) {
                                AsyncImage(
                                    model =
                                        coil.request.ImageRequest.Builder(context)
                                            .data(product.imageUrl)
                                            .size(800) // PERFORMANCE FIX: Limit to 800px (detail view is larger)
                                            .build(),
                                    contentDescription = product.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(text = stringResource(R.string.cd_placeholder_icon), fontSize = 80.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Item Name & Brand
                        Text(
                            text = product.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        if (product.brand.isNotBlank()) {
                            Text(
                                text = product.brand,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = stringResource(R.string.upc_label, items.first().upc),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Product size/unit
                        if (product.size != null && product.unit != null) {
                            val sizeUnitText =
                                fyi.goodbye.fridgy.models.SizeUnit.formatSizeUnit(
                                    product.size,
                                    product.unit
                                )
                            if (sizeUnitText != null) {
                                Text(
                                    text = sizeUnitText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // All Instances
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Instances (${items.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )

                                    Button(
                                        onClick = { viewModel.showAddInstanceDialog() },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add instance",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add")
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                items.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }

                                    ItemInstanceCard(
                                        item = item,
                                        product = product,
                                        userNames = userNames,
                                        dateFormatter = dateFormatter,
                                        onDelete = {
                                            viewModel.deleteItem(item.id)
                                        },
                                        onEditExpiration = {
                                            viewModel.showEditExpirationDialog(item)
                                        }
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
fun ItemInstanceCard(
    item: fyi.goodbye.fridgy.models.Item,
    product: fyi.goodbye.fridgy.models.Product,
    userNames: Map<String, String>,
    dateFormatter: SimpleDateFormat,
    onDelete: () -> Unit,
    onEditExpiration: () -> Unit
) {
    Column {
        // Expiration Date
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Expiration Date",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onEditExpiration,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit expiration",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (item.expirationDate != null) {
                    val expirationDateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
                    val isExpired = fyi.goodbye.fridgy.models.Item.isExpired(item.expirationDate)
                    val isExpiringSoon = fyi.goodbye.fridgy.models.Item.isExpiringSoon(item.expirationDate)

                    Text(
                        text = expirationDateFormatter.format(Date(item.expirationDate)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color =
                            when {
                                isExpired -> MaterialTheme.colorScheme.error
                                isExpiringSoon -> Color(0xFFFFA726) // Orange
                                else -> MaterialTheme.colorScheme.primary
                            }
                    )

                    if (isExpired) {
                        Text(
                            text = "Expired",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isExpiringSoon) {
                        Text(
                            text = "Expiring soon",
                            fontSize = 12.sp,
                            color = Color(0xFFFFA726)
                        )
                    }
                } else {
                    Text(
                        text = "No expiration",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delete button
            FilledIconButton(
                onClick = onDelete,
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFFF3B30)
                    ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Added info
        DetailRow(
            label = "Added by",
            value = userNames[item.addedBy] ?: "Loading..."
        )
        DetailRow(
            label = "Added on",
            value = item.addedAt?.let { dateFormatter.format(it) } ?: "Recently"
        )
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
