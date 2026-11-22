package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Product
import com.example.ui.AppStrings

@Composable
fun CatalogScreen(
    strings: AppStrings,
    products: List<Product>,
    onSaveProduct: (
        editingId: String?,
        name: String,
        price: Double,
        currency: String,
        category: String,
        description: String,
        inStock: Boolean,
        onDone: () -> Unit
    ) -> Unit,
    onToggleStock: (Product) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onAddSampleProducts: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }

    val filteredProducts = remember(products, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) products
        else products.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingProduct = null
                    showEditorDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(strings.addProduct) },
                modifier = Modifier.testTag("add_product_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = strings.catalogTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.catalogSearchHint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("catalog_search_input")
                )

                if (filteredProducts.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = strings.emptyCatalogTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = strings.emptyCatalogDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (products.isEmpty()) {
                                OutlinedButton(
                                    onClick = onAddSampleProducts,
                                    modifier = Modifier.testTag("add_sample_products_button")
                                ) {
                                    Text(strings.addSampleProducts)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductCard(
                                strings = strings,
                                product = product,
                                onToggleStock = { onToggleStock(product) },
                                onEdit = {
                                    editingProduct = product
                                    showEditorDialog = true
                                },
                                onDelete = { onDeleteProduct(product.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        ProductEditorDialog(
            strings = strings,
            initial = editingProduct,
            onDismiss = { showEditorDialog = false },
            onSave = { name, price, currency, category, description, inStock ->
                onSaveProduct(
                    editingProduct?.id,
                    name,
                    price,
                    currency,
                    category,
                    description,
                    inStock
                ) {
                    showEditorDialog = false
                }
            }
        )
    }
}

@Composable
private fun ProductCard(
    strings: AppStrings,
    product: Product,
    onToggleStock: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priceDisplay = if (product.price % 1.0 == 0.0) {
        product.price.toLong().toString()
    } else {
        product.price.toString()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (product.category.isNotBlank()) {
                        Text(
                            text = product.category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${product.currency}$priceDisplay",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (product.description.isNotBlank()) {
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = product.inStock,
                    onClick = onToggleStock,
                    label = {
                        Text(if (product.inStock) strings.inStockLabel else strings.outOfStockLabel)
                    }
                )
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_product_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = strings.editProduct
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_product_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = strings.deleteLabel,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductEditorDialog(
    strings: AppStrings,
    initial: Product?,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var priceText by remember {
        mutableStateOf(
            initial?.let {
                if (it.price % 1.0 == 0.0) it.price.toLong().toString() else it.price.toString()
            }.orEmpty()
        )
    }
    var currency by remember { mutableStateOf(initial?.currency ?: "৳") }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var inStock by remember { mutableStateOf(initial?.inStock ?: true) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (initial == null) strings.addProduct else strings.editProduct)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(120)
                        nameError = false
                    },
                    label = { Text(strings.productNameLabel) },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(strings.productPriceLabel) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("product_price_input")
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.take(10) },
                        label = { Text(strings.productCurrencyLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(60) },
                    label = { Text(strings.productCategoryLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(1000) },
                    label = { Text(strings.productDescriptionLabel) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_description_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.inStockLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = inStock,
                        onCheckedChange = { inStock = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        nameError = true
                        return@Button
                    }
                    val parsedPrice = priceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    onSave(trimmed, parsedPrice, currency, category, description, inStock)
                },
                modifier = Modifier.testTag("save_product_button")
            ) {
                Text(strings.saveLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelLabel)
            }
        }
    )
}
