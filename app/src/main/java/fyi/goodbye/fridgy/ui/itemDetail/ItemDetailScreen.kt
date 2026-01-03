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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    fridgeId: String,
    itemId: String,
    onBackClick: () -> Unit,
    viewModel: ItemDetailViewModel = viewModel(factory = ItemDetailViewModel.provideFactory(fridgeId, itemId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val userNames by viewModel.userNames.collectAsState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.item_details),
                        color = FridgyWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = FridgyWhite
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ItemDetailViewModel.ItemDetailUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is ItemDetailViewModel.ItemDetailUiState.Success -> {
                    val item = state.item
                    val product = state.product

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
                                    model = product.imageUrl,
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
                            text = "UPC: ${item.upc}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Reduced and equalized gaps around Quantity Control
                        Spacer(modifier = Modifier.height(16.dp))

                        // Quantity Control (Compact)
                        Card(
                            modifier = Modifier.widthIn(max = 240.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Qty",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${item.quantity}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isLastItem = item.quantity == 1
                                    FilledIconButton(
                                        onClick = {
                                            if (isLastItem) {
                                                viewModel.updateQuantity(0)
                                            } else {
                                                viewModel.updateQuantity(item.quantity - 1)
                                            }
                                        },
                                        colors =
                                            IconButtonDefaults.filledIconButtonColors(
                                                containerColor =
                                                    if (isLastItem) {
                                                        Color(
                                                            0xFFFF3B30
                                                        )
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    }
                                            ),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLastItem) Icons.Default.Delete else Icons.Default.Remove,
                                            contentDescription =
                                                if (isLastItem) {
                                                    stringResource(
                                                        R.string.cd_delete
                                                    )
                                                } else {
                                                    stringResource(R.string.cd_decrease)
                                                },
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    FilledIconButton(
                                        onClick = { viewModel.updateQuantity(item.quantity + 1) },
                                        colors =
                                            IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = stringResource(R.string.cd_increase),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Additional Details
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.history),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                DetailRow(
                                    label = stringResource(R.string.added_by),
                                    value = userNames[item.addedBy] ?: stringResource(R.string.loading)
                                )
                                DetailRow(
                                    label = stringResource(R.string.added_on),
                                    value = dateFormatter.format(Date(item.addedAt))
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                DetailRow(
                                    label = stringResource(R.string.last_updated_by),
                                    value = userNames[item.lastUpdatedBy] ?: stringResource(R.string.loading)
                                )
                                DetailRow(
                                    label = stringResource(R.string.last_updated_on),
                                    value = dateFormatter.format(Date(item.lastUpdatedAt))
                                )
                            }
                        }
                    }
                }
            }
        }
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
