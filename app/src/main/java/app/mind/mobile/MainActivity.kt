package app.mind.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Gold = Color(0xFFE8C547)
private val GoldDeep = Color(0xFFD4A942)
private val Ink = Color(0xFF0D0D0F)
private val Muted = Color(0xFFA2A2AB)
private val SurfaceGlass = Color(0x0EFFFFFF)
private val BorderGlass = Color(0x18FFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContent { MindRoot() }
    }
}

@Composable
private fun MindRoot() {
    var page by remember { mutableStateOf("home") }
    var previous by remember { mutableStateOf("home") }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Color(0xFF19191D), primary = Gold,
        onPrimary = Color(0xFF15130A), onBackground = Color(0xFFF4F4F6),
        onSurface = Color(0xFFF4F4F6)
    )) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            ReferenceAtmosphere()
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(targetState = page, label = "screen-transition") { target ->
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        if (target != "home") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { page = previous }) {
                                    Icon(Icons.Rounded.ArrowBack, "رجوع", tint = Gold)
                                }
                                Text(titleFor(target), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                        when (target) {
                            "home" -> HomeScreen(onOpen = { previous = "home"; page = it })
                            "habits" -> PlaceholderScreen("العادات", "إدارة العادات وتتبعها يومياً")
                            "water" -> PlaceholderScreen("الماء", "تسجيل استهلاك الماء حسب اليوم والساعة")
                            "settings" -> PlaceholderScreen("الإعدادات", "المظهر والصوت والحساب والبيانات")
                            else -> HomeScreen(onOpen = { previous = "home"; page = it })
                        }
                    }
                }
                NavigationBar(containerColor = Color(0xCC151518)) {
                    NavigationBarItem(selected = page == "home", onClick = { page = "home" },
                        icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("الرئيسية") })
                    NavigationBarItem(selected = page == "habits", onClick = { previous = page; page = "habits" },
                        icon = { Icon(Icons.Rounded.CheckCircle, null) }, label = { Text("العادات") })
                    NavigationBarItem(selected = page == "water", onClick = { previous = page; page = "water" },
                        icon = { Icon(Icons.Rounded.WaterDrop, null) }, label = { Text("الماء") })
                    NavigationBarItem(selected = page == "settings", onClick = { previous = page; page = "settings" },
                        icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("الإعدادات") })
                }
            }
        }
    }
}

@Composable
private fun ReferenceAtmosphere() {
    val transition = rememberInfiniteTransition(label = "ambient-orbs")
    val x by transition.animateFloat(-24f, 28f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orb-x")
    val y by transition.animateFloat(18f, -26f, infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "orb-y")
    Box(Modifier.fillMaxSize().background(
        Brush.radialGradient(listOf(Color(0x2EE8C547), Color.Transparent), radius = 900f,
            center = androidx.compose.ui.geometry.Offset(900f, 0f))
    ))
    Box(Modifier.offset(x.dp, y.dp).size(240.dp).align(Alignment.TopEnd).blur(65.dp)
        .background(Color(0x1FE8C547), RoundedCornerShape(120.dp)))
    Box(Modifier.offset((-30).dp, 260.dp).size(190.dp).align(Alignment.BottomStart).blur(70.dp)
        .background(Color(0x122F79A8), RoundedCornerShape(100.dp)))
}

@Composable
private fun HomeScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("MIND", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
        Text("مساحة ليوم أكثر توازناً", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("عقلك، وقتك، حياتك.", color = Muted)
        GlassCard(Modifier.fillMaxWidth()) {
            Text("تقدم اليوم", color = Muted)
            Text("ابدأ بخطوة صغيرة", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                color = Gold, trackColor = Color(0x22FFFFFF))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickCard("العادات", "تتبع يومك", Modifier.weight(1f)) { onOpen("habits") }
            QuickCard("الماء", "سجل أكوابك", Modifier.weight(1f)) { onOpen("water") }
        }
        QuickCard("الإعدادات", "الحساب والمظهر والصوت", Modifier.fillMaxWidth()) { onOpen("settings") }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted)
        Text("سيتم تنفيذ هذه الشاشة وفق تفاصيل المرجع الأصلي واختبار كل تفاعل قبل اعتمادها.",
            color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, color = SurfaceGlass,
        shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(SurfaceGlass)
        .border(1.dp, BorderGlass, RoundedCornerShape(22.dp)).padding(18.dp), content = content)
}

private fun titleFor(page: String) = when(page) {
    "habits" -> "العادات"; "water" -> "الماء"; "settings" -> "الإعدادات"; else -> "MIND"
}
