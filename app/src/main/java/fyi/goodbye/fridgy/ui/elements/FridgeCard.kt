package fyi.goodbye.fridgy.ui.elements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.goodbye.fridgy.R
import fyi.goodbye.fridgy.models.DisplayFridge
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTextBlue
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A card component that displays summary information for a single fridge.
 *
 * @param fridge The [DisplayFridge] data to show.
 * @param onClick Callback triggered when the card is clicked.
 */
@Composable
fun FridgeCard(fridge: DisplayFridge, onClick: (DisplayFridge) -> Unit) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable { onClick(fridge) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridgyWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fridge.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FridgyDarkBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.created_by_label, fridge.creatorDisplayName),
                    fontSize = 14.sp,
                    color = FridgyTextBlue.copy(alpha = 0.7f)
                )
                Text(
                    text = stringResource(R.string.members_count, fridge.memberUsers.size),
                    fontSize = 14.sp,
                    color = FridgyTextBlue.copy(alpha = 0.7f)
                )
                Text(
                    text = stringResource(R.string.added_date, dateFormatter.format(Date(fridge.createdAt))),
                    fontSize = 12.sp,
                    color = FridgyTextBlue.copy(alpha = 0.5f)
                )
            }
        }
    }
}
