package fyi.goodbye.fridgy.ui.elements

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog for selecting an optional expiration date for an item.
 *
 * @param productName The name of the product being added
 * @param onDateSelected Called when user confirms with a date (null for no expiration)
 * @param onDismiss Called when user cancels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpirationDateDialog(
    productName: String,
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    // DatePicker works in UTC - get current date in UTC for proper display
    val currentDateUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDateUtc.timeInMillis
    )

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    // Use Material3 DatePickerDialog for proper sizing
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        // Convert from UTC date to local timezone end of day
                        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMillis
                        }
                        val localCalendar = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        onDateSelected(localCalendar.timeInMillis)
                    } ?: onDateSelected(null)
                }
            ) {
                Text("Set Date")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(onClick = { onDateSelected(null) }) {
                    Text("Skip")
                }
            }
        }
    ) {
        Column {
            Text(
                text = productName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
            )
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        }
    }
}
