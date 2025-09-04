package fyi.goodbye.fridgy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.goodbye.fridgy.models.Fridge
import fyi.goodbye.fridgy.models.Item

@Composable
fun FridgeApp() {
    val viewModel: FridgeViewModel = viewModel()
    val fridges by viewModel.fridges.collectAsState()
    val selectedFridge by viewModel.selectedFridge.collectAsState()
    val currentItems by viewModel.currentFridgeItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (selectedFridge == null) {
                FridgeListScreen(
                    fridges = fridges,
                    isLoading = isLoading,
                    onFridgeClick = { fridge ->
                        viewModel.selectFridge(fridge)
                    }
                )
            } else {
                FridgeItemsScreen(
                    fridge = selectedFridge!!,
                    items = currentItems,
                    isLoading = isLoading,
                    onBackClick = { viewModel.selectFridge(null) },
                    onAddItem = { name, brand, quantity ->
                        viewModel.addItem(quantity)
                    },
                    onUpdateQuantity = { itemId, quantity ->
                        viewModel.updateQuantity(itemId, quantity)
                    },
                    onDeleteItem = { itemId ->
                        viewModel.deleteItem(itemId)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeListScreen(
    fridges: List<Fridge>,
    isLoading: Boolean,
    onFridgeClick: (Fridge) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "My Fridges",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(fridges) { fridge ->
                    FridgeCard(
                        fridge = fridge,
                        onClick = { onFridgeClick(fridge) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeCard(
    fridge: Fridge,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = fridge.name,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "${fridge.members.size} member(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeItemsScreen(
    fridge: Fridge,
    items: List<Item>,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onAddItem: (String, String, Int) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackClick) {
                Text("← Back")
            }
            Text(
                text = fridge.name,
                style = MaterialTheme.typography.headlineSmall
            )
            Button(onClick = { showAddDialog = true }) {
                Text("Add Item")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(items) { item ->
                    GroceryItemCard(
                        item = item,
                        onUpdateQuantity = onUpdateQuantity,
                        onDelete = onDeleteItem
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemDialog(
            onDismiss = { showAddDialog = false },
            onAddItem = { name, brand, quantity ->
                onAddItem(name, brand, quantity)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun GroceryItemCard(
    item: Item,
    onUpdateQuantity: (String, Int) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.upc,
                    style = MaterialTheme.typography.titleMedium
                )
                if (item.upc.isNotEmpty()) {
                    Text(
                        text = "Got Milk",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (item.quantity > 1) {
                            if (item.id != null) {
                                onUpdateQuantity(item.id, item.quantity - 1)
                            }
                        } else {
                            onDelete(item.id ?: "123")
                        }
                    }
                ) {
                    Text("-")
                }

                Text(
                    text = item.quantity.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = { if (item.id!=null) onUpdateQuantity(item.id, item.quantity + 1) }
                ) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
fun AddItemDialog(
    onDismiss: () -> Unit,
    onAddItem: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Item") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAddItem(name, brand, quantity.toIntOrNull() ?: 1)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}