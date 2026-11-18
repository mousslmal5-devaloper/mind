package app.mind.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val SG = Color(0xFFE8C547)
private val SM = Color(0xFFA2A2AB)
private val SF = Color(0x0EFFFFFF)
private val SB = Color(0x17FFFFFF)
private val SBlue = Color(0xFF5AA9FF)
private val SGreen = Color(0xFF5BD68A)
private val SOrange = Color(0xFFFF7A59)
private val SPurple = Color(0xFFB78CFF)
private val STeal = Color(0xFF4DD0C8)
private fun sDate(t: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))
private fun sHour(t: Long) = Calendar.getInstance().apply { timeInMillis=t }.get(Calendar.HOUR_OF_DAY)
private fun sDays(count: Int = 7): List<String> {
    val c=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }
    return (count - 1 downTo 0).map { sDate((c.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR,-it) }.timeInMillis) }
}
private fun List<Float>.meanKnown(): Float { val v=filterNot { it.isNaN() }; return if(v.isEmpty()) 0f else v.average().toFloat() }

@Composable
fun StatisticsScreen(habits:List<String>,habitMask:Int,water:Int,waterGoal:Int,waterEntries:List<WaterEntry>,steps:Int,stepEntries:List<StepEntry>,habitHistory:List<HabitDayRecord>,studySessions:List<StudySession>,sleepSessions:List<SleepSession>) {
    var period by rememberSaveable { mutableStateOf("week") }
    val days=remember(period) { sDays(if (period == "month") 30 else 7) }; val today=days.last()
    val start=remember { Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0) }.timeInMillis }
    val hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val waterWeek=days.map { k -> (if(k==today) maxOf(water,waterEntries.count { sDate(it.recordedAt)==k }) else waterEntries.count { sDate(it.recordedAt)==k }).toFloat() }
    val studyWeek=days.map { k -> studySessions.filter { sDate(it.completedAt)==k }.sumOf { it.durationMinutes }.toFloat() }
    val sleepWeek=days.map { k -> sleepSessions.filter { sDate(it.endedAt)==k }.sumOf { it.durationMinutes }/60f }
    val habitPct=if(habits.isEmpty()) 0f else Integer.bitCount(habitMask).coerceAtMost(habits.size)*100f/habits.size
    val waterHour=(0..23).map { h -> waterEntries.count { it.recordedAt>=start && sHour(it.recordedAt)==h }.toFloat() }
    val studyHour=(0..23).map { h -> studySessions.filter { it.completedAt>=start && sHour(it.completedAt)==h }.sumOf { it.durationMinutes }.toFloat() }
    val sleepHour=(0..23).map { h -> val a=start+h*3600000L; val b=a+3600000L; sleepSessions.sumOf { (minOf(b,it.endedAt)-maxOf(a,it.startedAt)).coerceAtLeast(0L) }/3600000f }
    val stepsByDay=stepEntries.groupBy { sDate(it.recordedAt) }
    val stepsWeek=days.map { k -> (stepsByDay[k]?.sumOf { it.delta } ?: if(k==today) steps else 0).coerceAtLeast(0).toFloat() }
    val habitByDay=habitHistory.associateBy { it.dateKey }
    val habitsWeek=days.map { k ->
        val record=habitByDay[k]
        if(record!=null) if(record.habitCount==0) 0f else Integer.bitCount(record.completedMask).coerceAtMost(record.habitCount)*100f/record.habitCount
        else if(k==today) habitPct else Float.NaN
    }
    val stepsHour=(0..23).map { h -> stepEntries.filter { sDate(it.recordedAt)==today && sHour(it.recordedAt)==h }.sumOf { it.delta }.coerceAtLeast(0).toFloat().let { if(it==0f) Float.NaN else it } }
    val habitsHour=(0..23).map { Float.NaN }
    val caloriesWeek=stepsWeek.map { if(it.isNaN()) Float.NaN else it*.04f }
    val caloriesHour=stepsHour.map { if(it.isNaN()) Float.NaN else it*.04f }
    val labels=days.map { runCatching { SimpleDateFormat(if(period=="month") "d" else "EEE",Locale("ar")).format(SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(it)!!) }.getOrDefault("—") }
    val hLabels=(0..23).map { if(it%6==0) "%02d".format(it) else "" }
    val rings=if(period=="day") listOf(
        RMetric(steps.toFloat(),10000f,SG,steps.toString(),"الخطوات"),
        RMetric(water.toFloat(),waterGoal.coerceAtLeast(1).toFloat(),SBlue,water.toString()+"/"+waterGoal,"الماء"),
        RMetric(steps*.04f,400f,SOrange,(steps*.04f).roundToInt().toString(),"سعرات تقديرية"),
        RMetric(habitPct,100f,SGreen,habitPct.roundToInt().toString()+"%","العادات"),
        RMetric(studyHour.sum(),120f,SPurple,studyHour.sum().roundToInt().toString(),"دقائق التركيز"),
        RMetric(sleepHour.sum(),8f,STeal,String.format(Locale.US,"%.1f",sleepHour.sum()),"النوم")
    ) else listOf(
        RMetric(stepsWeek.sum(),10000f*days.size,SG,stepsWeek.sum().roundToInt().toString(),"الخطوات"),
        RMetric(waterWeek.sum(),(waterGoal*days.size).coerceAtLeast(1).toFloat(),SBlue,waterWeek.sum().roundToInt().toString(),"الماء"),
        RMetric(caloriesWeek.filterNot { it.isNaN() }.sum(),400f*days.size,SOrange,caloriesWeek.filterNot { it.isNaN() }.sum().roundToInt().toString(),"سعرات تقديرية"),
        RMetric(habitsWeek.meanKnown(),100f,SGreen,habitsWeek.meanKnown().roundToInt().toString()+"%","متوسط العادات"),
        RMetric(studyWeek.sum(),120f*days.size,SPurple,studyWeek.sum().roundToInt().toString(),"دقائق التركيز"),
        RMetric(sleepWeek.meanKnown(),8f,STeal,String.format(Locale.US,"%.1f",sleepWeek.meanKnown()),"متوسط النوم")
    )
    val charts=if(period=="day") listOf(
        CMetric("الخطوات","خطوة",SG,stepsHour,hLabels),CMetric("السعرات المحروقة","kcal تقديري",SOrange,caloriesHour,hLabels),
        CMetric("إنجاز العادات","%",SGreen,habitsHour,hLabels),CMetric("النوم","ساعة",STeal,sleepHour,hLabels),
        CMetric("دقائق التركيز","دقيقة",SPurple,studyHour,hLabels),CMetric("الماء","كوب",SBlue,waterHour,hLabels)
    ) else listOf(
        CMetric("الخطوات","خطوة",SG,stepsWeek,labels),CMetric("السعرات المحروقة","kcal تقديري",SOrange,caloriesWeek,labels),
        CMetric("إنجاز العادات","%",SGreen,habitsWeek,labels),CMetric("النوم","ساعة",STeal,sleepWeek,labels),
        CMetric("دقائق التركيز","دقيقة",SPurple,studyWeek,labels),CMetric("الماء","كوب",SBlue,waterWeek,labels)
    )
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,12.dp,18.dp,112.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("الإحصائيات",fontSize=26.sp,fontWeight=FontWeight.ExtraBold);Text("تابع تقدمك واتجاهاتك بمرور الوقت",color=SM,fontSize=12.sp) }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SF).border(1.dp,SB,RoundedCornerShape(16.dp)).padding(4.dp)) {
                listOf("day" to "اليوم","week" to "7 أيام","month" to "30 يوماً").forEach { p ->
                    val chosen=period==p.first
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(chosen) SG else Color.Transparent).clickable { period=p.first }.padding(11.dp),contentAlignment=Alignment.Center) {
                        Text(p.second,color=if(chosen) Color(0xFF15130A) else SM,fontWeight=FontWeight.Bold,fontSize=13.sp)
                    }
                }
            }
        }
        item {
            Text(when(period) { "day" -> "ملخص اليوم"; "month" -> "ملخص آخر 30 يوماً"; else -> "ملخص آخر 7 أيام" },fontSize=17.sp,fontWeight=FontWeight.Bold)
            BoxWithConstraints {
                val columns=when { maxWidth>=900.dp -> 6; maxWidth>=560.dp -> 3; else -> 2 }
                Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    rings.chunked(columns).forEach { group -> Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        group.forEach { RingTile(it,Modifier.weight(1f)) }; repeat(columns-group.size){Spacer(Modifier.weight(1f))}
                    } }
                }
            }
        }
        item { Text("المنحنيات",fontSize=17.sp,fontWeight=FontWeight.Bold) }
        item {
            BoxWithConstraints {
                val columns=if(maxWidth>=760.dp) 2 else 1
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    charts.chunked(columns).forEach { group ->
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            group.forEach { ChartTile(it,period,Modifier.weight(1f)) }
                            repeat(columns-group.size){Spacer(Modifier.weight(1f))}
                        }
                    }
                }
            }
        }
        if(period!="day") {
            item { HabitWeekGrid(habits,habitMask,days,labels,habitHistory) }
            item { SleepWeekGrid(sleepSessions,days,labels) }
        }
        item { Text("السعرات تقديرية من الخطوات. منحنى العادات يعرض الأيام المسجلة فقط، ولا تتوفر بيانات ساعة بساعة للعادات بعد.",color=SM,fontSize=10.sp) }
    }
}

private data class RMetric(val value:Float,val goal:Float,val color:Color,val display:String,val title:String)
private data class CMetric(val title:String,val unit:String,val color:Color,val values:List<Float>,val labels:List<String>)

@Composable private fun RingTile(m:RMetric,modifier:Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(19.dp)).background(SF).border(1.dp,SB,RoundedCornerShape(19.dp)).padding(vertical=12.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(72.dp),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { val sw=7.dp.toPx();drawArc(Color.White.copy(alpha=.08f),-90f,360f,false,style=Stroke(sw));drawArc(m.color,-90f,360f*(m.value/m.goal.coerceAtLeast(1f)).coerceIn(0f,1f),false,style=Stroke(sw,cap=StrokeCap.Round)) }
            Text(m.display,fontSize=11.sp,fontWeight=FontWeight.Bold)
        }
        Text(m.title,fontSize=10.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
        Text("هدف "+m.goal.roundToInt(),color=SM,fontSize=9.sp)
    }
}
@Composable private fun ChartTile(c:CMetric,period:String,modifier:Modifier=Modifier) {
    val known=c.values.filterNot { it.isNaN() };val avg=known.filter { it>0 }.let { if(it.isEmpty()) 0f else it.average().toFloat() };val best=known.maxOrNull()?:0f
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(SF).border(1.dp,SB,RoundedCornerShape(21.dp)).padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text(c.title,Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=14.sp);Text((if(period!="day") known.sum() else best).roundToInt().toString()+" "+c.unit,color=c.color,fontSize=12.sp,fontWeight=FontWeight.Bold) }
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val l=8.dp.toPx();val r=size.width-8.dp.toPx();val t=8.dp.toPx();val b=size.height-18.dp.toPx()
            repeat(3){i->val y=t+(b-t)*i/2f;drawLine(Color.White.copy(alpha=.07f),Offset(l,y),Offset(r,y),1.dp.toPx())}
            val vals=c.values.filterNot { it.isNaN() };val max=(vals.maxOrNull()?:1f).coerceAtLeast(1f)*1.15f
            val path=Path();var started=false
            c.values.forEachIndexed { i,v -> if(v.isNaN()) started=false else {val x=l+(r-l)*i/(c.values.size-1).coerceAtLeast(1);val y=b-(b-t)*v/max;if(!started){path.moveTo(x,y);started=true}else path.lineTo(x,y);drawCircle(c.color,3.dp.toPx(),Offset(x,y))} }
            drawPath(path,c.color,style=Stroke(2.5.dp.toPx(),cap=StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){c.labels.forEachIndexed{i,s->if(s.isNotBlank()&&(period=="day"||(period=="month" && (i%5==0||i==c.labels.lastIndex))||(period=="week" && (i%2==0||i==c.labels.lastIndex))))Text(s,color=SM,fontSize=9.sp)}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){FootStat("المتوسط",String.format(Locale.US,"%.1f",avg));FootStat("الأعلى",String.format(Locale.US,"%.1f",best));FootStat("نقاط البيانات",known.size.toString()+"/"+c.values.size)}
    }
}
@Composable private fun FootStat(a:String,b:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(a,color=SM,fontSize=9.sp);Text(b,fontSize=11.sp,fontWeight=FontWeight.Bold)}}

@Composable private fun HabitWeekGrid(habits:List<String>,mask:Int,days:List<String>,labels:List<String>,history:List<HabitDayRecord>) {
    val records=history.associateBy { it.dateKey }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(SF).border(1.dp,SB,RoundedCornerShape(21.dp)).padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("شبكة العادات",fontSize=15.sp,fontWeight=FontWeight.Bold)
        if(habits.isEmpty()) Text("أضف عاداتك لعرض الشبكة",color=SM,fontSize=12.sp)
        habits.forEachIndexed { i,name -> Row(verticalAlignment=Alignment.CenterVertically) {
            Text(name,Modifier.weight(1.2f),fontSize=10.sp,maxLines=1)
            days.forEachIndexed { d,key ->
                val record=records[key]
                val done=if(d==days.lastIndex) (mask and (1 shl i))!=0 else record!=null && i<record.habitCount && (record.completedMask and (1 shl i))!=0
                val known=d==days.lastIndex || record!=null
                Box(Modifier.weight(.35f).padding(horizontal=2.dp).height(19.dp).clip(RoundedCornerShape(5.dp)).background(if(done) SGreen.copy(alpha=.8f) else if(known) Color.White.copy(alpha=.07f) else Color.White.copy(alpha=.025f)))
            }
        } }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){labels.forEach{Text(it.take(2),color=SM,fontSize=9.sp)}}
        Text("الأيام غير المسجلة تبقى فارغة ولا تُحسب كإخفاق.",color=SM,fontSize=10.sp)
    }
}
@Composable private fun SleepWeekGrid(sessions:List<SleepSession>,days:List<String>,labels:List<String>) {
    val values=days.map{k->sessions.filter{sDate(it.endedAt)==k}.sumOf{it.durationMinutes}/60f}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(SF).border(1.dp,SB,RoundedCornerShape(21.dp)).padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("شبكة النوم الأسبوعية",fontSize=15.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.Bottom) {
            values.forEachIndexed{i,v->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(if(v>0)String.format(Locale.US,"%.1f",v) else "—",fontSize=9.sp,color=STeal)
                Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha=.04f)),contentAlignment=Alignment.BottomCenter){
                    Box(Modifier.fillMaxWidth().fillMaxHeight((v/10f).coerceIn(0f,1f)).background(STeal.copy(alpha=.75f),RoundedCornerShape(6.dp)))
                }
                Text(labels[i].take(2),fontSize=9.sp,color=SM)
            }}
        }
    }
}

private data class AwardGroup(val key:String,val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val levels:List<Int>)
private data class AwardItem(val label:String,val icon:androidx.compose.ui.graphics.vector.ImageVector,val level:Int,val current:Int)

@Composable
fun AwardsScreen(habits:List<String>,habitMask:Int,waterEntries:List<WaterEntry>,sleepSessions:List<SleepSession>,studyMinutes:Int,studySessions:List<StudySession>,steps:Int,noteText:String) {
    val groups=listOf(
        AwardGroup("hab","عادات مضافة",Icons.Rounded.CheckCircle,listOf(1,3,5,10,20,50)),
        AwardGroup("str","يوماً متتالياً",Icons.Rounded.EmojiEvents,listOf(3,7,14,30,60,100,200,365)),
        AwardGroup("chk","تسجيل عادة",Icons.Rounded.CheckCircle,listOf(1,10,50,100,250,500,1000,5000)),
        AwardGroup("prf","أيام مثالية",Icons.Rounded.EmojiEvents,listOf(1,3,7,14,30,100)),
        AwardGroup("wat","كوب ماء",Icons.Rounded.WaterDrop,listOf(8,50,100,250,500,1000,5000)),
        AwardGroup("slp","ليلة نوم مسجلة",Icons.Rounded.Bedtime,listOf(1,7,14,30,100,365)),
        AwardGroup("slh","ساعة نوم",Icons.Rounded.Bedtime,listOf(10,50,100,500,1000)),
        AwardGroup("stm","دقيقة دراسة",Icons.Rounded.MenuBook,listOf(30,60,300,600,3000,6000,30000)),
        AwardGroup("sts","جلسة دراسة",Icons.Rounded.MenuBook,listOf(1,10,25,50,100,500)),
        AwardGroup("stp","خطوة",Icons.Rounded.DirectionsRun,listOf(1000,5000,10000,50000,100000,500000,1000000)),
        AwardGroup("fit","تمرين مسجل",Icons.Rounded.DirectionsRun,listOf(1,10,25,50,100)),
        AwardGroup("fod","وجبة مسجلة",Icons.Rounded.EmojiEvents,listOf(1,10,50,100,500,1000)),
        AwardGroup("jr","يومية مكتوبة",Icons.Rounded.Star,listOf(1,7,30,100,365)),
        AwardGroup("nt","مذكرة",Icons.Rounded.Star,listOf(1,10,25,50,100)),
        AwardGroup("day","يوم استخدام",Icons.Rounded.Star,listOf(1,7,30,100,365,730))
    )
    val done=Integer.bitCount(habitMask).coerceAtMost(habits.size)
    val active=buildSet {
        waterEntries.forEach{add(sDate(it.recordedAt))}
        studySessions.forEach{add(sDate(it.completedAt))}
        sleepSessions.forEach{add(sDate(it.endedAt))}
        if(done>0||steps>0)add(sDate(System.currentTimeMillis()))
    }.size
    val values=mapOf(
        "hab" to habits.size,"str" to 0,"chk" to done,
        "prf" to if(habits.isNotEmpty()&&done==habits.size)1 else 0,
        "wat" to waterEntries.size,"slp" to sleepSessions.size,
        "slh" to sleepSessions.sumOf{it.durationMinutes}/60,
        "stm" to maxOf(studyMinutes,studySessions.sumOf{it.durationMinutes}),
        "sts" to studySessions.size,"stp" to steps,"fit" to 0,"fod" to 0,
        "jr" to 0,"nt" to if(noteText.isBlank())0 else 1,"day" to active
    )
    val baseOpened=groups.sumOf{g->g.levels.count{(values[g.key]?:0)>=it}}
    val allGroups=groups+AwardGroup("ach","إنجازاً مفتوحاً",Icons.Rounded.Star,listOf(5,10,25,50,75,90,93))
    val items=allGroups.flatMap { g ->
        val current=if(g.key=="ach")baseOpened else values[g.key]?:0
        g.levels.map { AwardItem(g.label,g.icon,it,current) }
    }
    val unlocked=items.count{it.current>=it.level}
    val total=items.size

    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(18.dp,12.dp,18.dp,112.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
        item {
            Text("الإنجازات",fontSize=26.sp,fontWeight=FontWeight.ExtraBold)
            Text("كل خطوة تُحسب. واصل بناء تقدمك.",color=SM,fontSize=12.sp)
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SF)
                .border(1.dp,SB,RoundedCornerShape(22.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents,null,tint=SG,modifier=Modifier.size(24.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("الإنجازات المفتوحة",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                    Text(unlocked.toString()+" / "+total,color=SG,fontWeight=FontWeight.ExtraBold,fontSize=18.sp)
                }
                LinearProgressIndicator(progress={unlocked.toFloat()/total},modifier=Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color=SG,trackColor=Color.White.copy(alpha=.08f))
            }
        }
        item {
            BoxWithConstraints {
                val columns=if(maxWidth>=700.dp)4 else 2
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    items.chunked(columns).forEach { group ->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            group.forEach { AwardTile(it,Modifier.weight(1f)) }
                            repeat(columns-group.size){Spacer(Modifier.weight(1f))}
                        }
                    }
                }
            }
        }
        item {
            Text("الوجبات والتمارين واليوميات غير مفعّلة بعد؛ لذلك لا تُمنح إنجازات لها دون بيانات فعلية.",color=SM,fontSize=10.sp)
        }
    }
}

@Composable
private fun AwardTile(item:AwardItem,modifier:Modifier=Modifier) {
    val achieved=item.current>=item.level
    val progress=(item.current.toFloat()/item.level).coerceIn(0f,1f)
    Column(modifier.clip(RoundedCornerShape(18.dp))
        .background(if(achieved)SG.copy(alpha=.09f) else SF)
        .border(1.dp,if(achieved)SG.copy(alpha=.42f) else SB,RoundedCornerShape(18.dp))
        .padding(vertical=13.dp,horizontal=8.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Icon(item.icon,null,tint=if(achieved)SG else SM.copy(alpha=.55f),modifier=Modifier.size(29.dp))
        Text(item.level.toString(),fontSize=16.sp,fontWeight=FontWeight.ExtraBold,color=if(achieved)SG else Color.White)
        Text(item.label,color=if(achieved)Color.White else SM,fontSize=10.sp,maxLines=2)
        LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            color=if(achieved)SG else SM.copy(alpha=.45f),trackColor=Color.White.copy(alpha=.07f))
    }
}
