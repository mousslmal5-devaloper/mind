package app.mind.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FGold = Color(0xFFE8C547)
private val FMuted = Color(0xFFA2A2AB)
private val FGlass = Color(0x0EFFFFFF)
private val FBorder = Color(0x17FFFFFF)

private fun featureDateKey(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))

private fun featureDateLabel(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("ar")).format(Date(timestamp))

private fun lastSevenDayKeys(): List<String> {
    val today = Calendar.getInstance()
    return (6 downTo 0).map { offset ->
        (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }.timeInMillis
            .let(::featureDateKey)
    }
}

@Composable
fun FoodScreen(
    entries: List<FoodEntry>,
    onAdd: (Double) -> Unit,
    onDelete: (FoodEntry) -> Unit
) {
    var caloriesText by rememberSaveable { mutableStateOf("") }
    val today = remember { featureDateKey(System.currentTimeMillis()) }
    val todayEntries = entries.filter { featureDateKey(it.recordedAt) == today }
    val totalToday = todayEntries.sumOf { it.calories }
    val weekKeys = remember { lastSevenDayKeys() }
    val weekValues = weekKeys.map { key ->
        entries.filter { featureDateKey(it.recordedAt) == key }.sumOf { it.calories }.toFloat()
    }
    val weekMax = (weekValues.maxOrNull() ?: 0f).coerceAtLeast(1f)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("التغذية", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(FGold, Color(0xFFD4A942))),
                    RoundedCornerShape(22.dp)
                ).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("سعرات اليوم", color = Color(0xFF15130A), fontSize = 13.sp)
                Text(
                    String.format(Locale.getDefault(), "%.1f", totalToday),
                    color = Color(0xFF15130A), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold
                )
                Text("سعرة حرارية", color = Color(0xFF15130A), fontSize = 12.sp)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(FGlass, RoundedCornerShape(22.dp))
                    .border(1.dp, FBorder, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("آخر 7 أيام", Modifier.weight(1f), color = FMuted, fontSize = 13.sp)
                    Text(
                        String.format(Locale.getDefault(), "%.0f سعرة", weekValues.sum()),
                        color = FGold, fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                }
                Canvas(Modifier.fillMaxWidth().height(126.dp)) {
                    val gap = 9.dp.toPx()
                    val width = (size.width - gap * 6) / 7f
                    val baseline = size.height - 20.dp.toPx()
                    weekValues.forEachIndexed { index, value ->
                        val height = (baseline - 8.dp.toPx()) * (value / weekMax)
                        val left = index * (width + gap)
                        drawRoundRect(
                            color = FGold.copy(alpha = if (index == 6) 1f else .62f),
                            topLeft = Offset(left, baseline - height),
                            size = androidx.compose.ui.geometry.Size(width, height.coerceAtLeast(2.dp.toPx())),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    weekKeys.forEach { key ->
                        Text(key.takeLast(2), color = FMuted, fontSize = 9.sp)
                    }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().background(FGlass, RoundedCornerShape(22.dp))
                    .border(1.dp, FBorder, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Restaurant, null, tint = FGold)
                    Text("إضافة سعرات", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = caloriesText,
                    onValueChange = { caloriesText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(',', '.') },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("السعرات الحرارية") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Button(
                    onClick = {
                        val value = caloriesText.toDoubleOrNull()
                        if (value != null && value.isFinite() && value > 0.0) {
                            onAdd(value)
                            caloriesText = ""
                        }
                    },
                    enabled = caloriesText.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FGold, contentColor = Color(0xFF15130A))
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة")
                }
            }
        }
        item { Text("سجل التغذية", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (entries.isEmpty()) {
            item {
                Text("لا توجد سجلات بعد", color = FMuted, fontSize = 12.sp)
            }
        } else {
            items(entries.sortedByDescending { it.recordedAt }.take(30), key = { it.recordedAt }) { entry ->
                Row(
                    Modifier.fillMaxWidth().background(FGlass, RoundedCornerShape(17.dp))
                        .border(1.dp, FBorder, RoundedCornerShape(17.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(String.format(Locale.getDefault(), "%.1f سعرة", entry.calories), color = FGold, fontWeight = FontWeight.Bold)
                        Text(featureDateLabel(entry.recordedAt), color = FMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Rounded.DeleteOutline, "حذف", tint = FMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun JournalScreen(
    entries: List<JournalEntry>,
    onAdd: (String) -> Unit,
    onDelete: (JournalEntry) -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item { Text("اليوميات", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold) }
        item {
            Column(
                Modifier.fillMaxWidth().background(FGlass, RoundedCornerShape(22.dp))
                    .border(1.dp, FBorder, RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Book, null, tint = FGold)
                    Text("إضافة يومية", fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("اكتب هنا…") }
                )
                Button(
                    onClick = {
                        val value = text.trim()
                        if (value.isNotEmpty()) {
                            onAdd(value)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FGold, contentColor = Color(0xFF15130A))
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة")
                }
            }
        }
        item { Text("سجل اليوميات", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (entries.isEmpty()) {
            item { Text("لا توجد يوميات بعد", color = FMuted, fontSize = 12.sp) }
        } else {
            items(entries.sortedByDescending { it.recordedAt }.take(30), key = { it.recordedAt }) { entry ->
                Row(
                    Modifier.fillMaxWidth().background(FGlass, RoundedCornerShape(17.dp))
                        .border(1.dp, FBorder, RoundedCornerShape(17.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(entry.text, fontSize = 14.sp)
                        Text(featureDateLabel(entry.recordedAt), color = FMuted, fontSize = 10.sp)
                    }
                    IconButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Rounded.DeleteOutline, "حذف", tint = FMuted)
                    }
                }
            }
        }
    }
}
