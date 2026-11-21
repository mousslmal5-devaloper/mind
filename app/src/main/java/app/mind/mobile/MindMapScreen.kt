package app.mind.mobile

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val android.content.Context.mindMapDataStore by preferencesDataStore(name = "mind_maps")
private val MindMapKey = stringPreferencesKey("documents")
private val MapGold = Color(0xFFE8C547)
private val MapPalette = listOf(0xFFE8C547, 0xFF68C7B8, 0xFF8F9BFF, 0xFFE58B72, 0xFFB78CFF, 0xFF72B8E8)
private val MapBackgrounds = listOf(0xFF111115, 0xFF171923, 0xFF1B1712, 0xFF101D1B)

private data class MindMapNode(val id: Long, val text: String, val x: Float, val y: Float, val color: Long, val parentId: Long?)
private data class MindMapDocument(val title: String, val background: Long, val nodes: List<MindMapNode>)

private fun encodeMindMaps(maps: List<MindMapDocument>): String = JSONArray().apply {
    maps.forEach { map ->
        put(JSONObject().put("title", map.title).put("background", map.background).put("nodes", JSONArray().apply {
            map.nodes.forEach { node -> put(JSONObject().put("id", node.id).put("text", node.text).put("x", node.x)
                .put("y", node.y).put("color", node.color).put("parentId", node.parentId ?: JSONObject.NULL)) }
        }))
    }
}.toString()

private fun decodeMindMaps(raw: String, english: Boolean): List<MindMapDocument> = runCatching {
    val a = JSONArray(raw)
    buildList {
        for (i in 0 until a.length()) {
            val m = a.optJSONObject(i) ?: continue
            val ns = m.optJSONArray("nodes") ?: JSONArray()
            val nodes = buildList {
                for (j in 0 until ns.length()) {
                    val n = ns.optJSONObject(j) ?: continue
                    val parent = n.opt("parentId")
                    add(MindMapNode(n.optLong("id"), n.optString("text"), n.optDouble("x").toFloat(),
                        n.optDouble("y").toFloat(), n.optLong("color", 0xFFE8C547L), if (parent == JSONObject.NULL) null else (parent as? Number)?.toLong()))
                }
            }
            add(MindMapDocument(m.optString("title", mindString(english, "خريطة ذهنية", "Mind map")), m.optLong("background", MapBackgrounds.first()), nodes))
        }
    }
}.getOrDefault(emptyList())

@Composable
fun MindMapScreen() {
    val context = LocalContext.current
    val english = LocalMindLanguage.current == "en"
    val scope = rememberCoroutineScope()
    var maps by remember { mutableStateOf<List<MindMapDocument>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var activeMap by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editText by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val raw = runCatching { context.mindMapDataStore.data.first()[MindMapKey] ?: "[]" }.getOrDefault("[]")
        maps = decodeMindMaps(raw, english).ifEmpty { listOf(MindMapDocument(mindString(english, "خريطة جديدة", "New map"), MapBackgrounds.first(),
            listOf(MindMapNode(System.currentTimeMillis(), mindString(english, "الفكرة الرئيسية", "Main idea"), 115f, 110f, 0xFFE8C547L, null)))) }
        loaded = true
    }
    LaunchedEffect(maps, loaded) {
        if (loaded) runCatching { context.mindMapDataStore.edit { it[MindMapKey] = encodeMindMaps(maps) } }
            .onFailure { Toast.makeText(context, mindString(english, "تعذر حفظ الخريطة", "Could not save the map"), Toast.LENGTH_SHORT).show() }
    }

    val map = maps.getOrNull(activeMap)
    val selected = map?.nodes?.firstOrNull { it.id == selectedId }
    fun updateMap(transform: (MindMapDocument) -> MindMapDocument) {
        maps = maps.mapIndexed { index, item -> if (index == activeMap) transform(item) else item }
    }
    fun addNode(text: String) {
        if (text.isBlank() || map == null) return
        val id = System.currentTimeMillis()
        val parent = selectedId ?: map.nodes.firstOrNull()?.id
        updateMap { it.copy(nodes = it.nodes + MindMapNode(id, text.trim(), 110f + (it.nodes.size % 3) * 55f,
            210f + (it.nodes.size % 5) * 48f, MapPalette[it.nodes.size % MapPalette.size], parent)) }
        selectedId = id
        editText = text.trim()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(mindString(english, "الخرائط الذهنية", "Mind maps"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            maps.forEachIndexed { index, item ->
                FilterChip(selected = activeMap == index, onClick = { activeMap = index; selectedId = null },
                    label = { Text(item.title.ifBlank { mindString(english, "خريطة ${index + 1}", "Map ${index + 1}") }, maxLines = 1) })
            }
            OutlinedButton(onClick = {
                val rootId = System.currentTimeMillis()
                maps = maps + MindMapDocument(mindString(english, "خريطة جديدة", "New map"), MapBackgrounds.first(),
                    listOf(MindMapNode(rootId, mindString(english, "الفكرة الرئيسية", "Main idea"), 115f, 110f, 0xFFE8C547L, null)))
                activeMap = maps.lastIndex
                selectedId = rootId
            }) { Text("+") }
        }
        if (map != null) {
            OutlinedTextField(value = map.title, onValueChange = { value -> updateMap { it.copy(title = value) } },
                label = { Text(mindString(english, "عنوان الخريطة", "Map title")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = MapGold, contentColor = Color(0xFF15130A))) { Text(mindString(english, "إضافة فكرة", "Add idea")) }
                OutlinedButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }) { Text(mindString(english, "إعادة العرض", "Reset view")) }
                OutlinedButton(onClick = {
                    val body = buildString {
                        appendLine(map.title)
                        map.nodes.forEach { node -> appendLine("• " + node.text) }
                    }
                    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
                    context.startActivity(Intent.createChooser(intent, mindString(english, "مشاركة الخريطة", "Share map")))
                }) { Text(mindString(english, "تصدير / مشاركة", "Export / Share")) }
            }
            Text(mindString(english, "المساحة: اسحب العقد لتحريكها، واسحب الخلفية للتنقل، واستخدم إصبعين للتكبير والتصغير.", "Canvas: drag nodes to move them, drag the background to pan, and use two fingers to zoom."), color = Color(0xFFA2A2AB), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(22.dp))
                .background(Color(map.background)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(.55f, 2.4f)
                        panX += pan.x
                        panY += pan.y
                    }
                }) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val byId = map.nodes.associateBy { it.id }
                    map.nodes.forEach { node ->
                        node.parentId?.let { parentId -> byId[parentId]?.let { parent ->
                            drawLine(Color(node.color).copy(alpha = .65f),
                                androidx.compose.ui.geometry.Offset((parent.x + 62f) * zoom + panX, (parent.y + 24f) * zoom + panY),
                                androidx.compose.ui.geometry.Offset((node.x + 62f) * zoom + panX, (node.y + 24f) * zoom + panY),
                                strokeWidth = 2.dp.toPx())
                        } }
                    }
                }
                map.nodes.forEach { node ->
                    Surface(
                        modifier = Modifier.offset { IntOffset((node.x * zoom + panX).roundToInt(), (node.y * zoom + panY).roundToInt()) }
                            .widthIn(min = 105.dp, max = 170.dp)
                            .pointerInput(node.id, zoom) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    updateMap { doc -> doc.copy(nodes = doc.nodes.map {
                                        if (it.id == node.id) it.copy(x = it.x + drag.x / zoom, y = it.y + drag.y / zoom) else it
                                    }) }
                                }
                            },
                        onClick = { selectedId = node.id; editText = node.text },
                        color = Color(node.color).copy(alpha = .18f), shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(if (selectedId == node.id) 2.dp else 1.dp,
                            Color(node.color))
                    ) {
                        Text(node.text, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = Color.White,
                            fontWeight = FontWeight.SemiBold, maxLines = 3)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mindString(english, "خلفية الخريطة", "Map background"), color = Color(0xFFA2A2AB), modifier = Modifier.align(Alignment.CenterVertically))
                MapBackgrounds.forEach { color ->
                    Surface(onClick = { updateMap { it.copy(background = color) } }, modifier = Modifier.size(28.dp),
                        color = Color(color), shape = RoundedCornerShape(9.dp),
                        border = androidx.compose.foundation.BorderStroke(if (map.background == color) 2.dp else 1.dp, MapGold)) {}
                }
            }
            if (selected != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B20))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(mindString(english, "تعديل الفكرة المحددة", "Edit selected idea"), color = MapGold, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = editText, onValueChange = { editText = it }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MapPalette.forEach { color ->
                                Surface(onClick = { updateMap { doc -> doc.copy(nodes = doc.nodes.map { if (it.id == selected.id) it.copy(color = color) else it }) } },
                                    modifier = Modifier.size(28.dp), color = Color(color), shape = RoundedCornerShape(50)) {}
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { updateMap { doc -> doc.copy(nodes = doc.nodes.map { if (it.id == selected.id) it.copy(text = editText.trim().ifBlank { it.text }) else it }) } },
                                colors = ButtonDefaults.buttonColors(containerColor = MapGold, contentColor = Color(0xFF15130A))) { Text(mindString(english, "حفظ التعديل", "Save changes")) }
                            OutlinedButton(onClick = {
                                updateMap { doc -> doc.copy(nodes = doc.nodes.filterNot { it.id == selected.id }.map {
                                    if (it.parentId == selected.id) it.copy(parentId = selected.parentId) else it
                                }) }
                                selectedId = null
                            }) { Text(mindString(english, "حذف الفكرة", "Delete idea")) }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false },
        title = { Text(mindString(english, "فكرة جديدة", "New idea")) },
        text = { OutlinedTextField(value = newText, onValueChange = { newText = it }, label = { Text(mindString(english, "اكتب الفكرة", "Write an idea")) }) },
        confirmButton = { TextButton(onClick = { addNode(newText); newText = ""; showAdd = false }) { Text(mindString(english, "إضافة", "Add")) } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text(mindString(english, "إلغاء", "Cancel")) } })
}
