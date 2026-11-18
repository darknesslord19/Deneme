package com.pulse.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.player.ui.theme.Card
import com.pulse.player.ui.theme.TextDim
import com.pulse.player.ui.theme.ThemeState
import com.pulse.player.ui.theme.Themes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Card) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Temalar", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("${Themes.size} tema • seçimin kaydedilir", color = TextDim, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kapak rengini kullan", fontWeight = FontWeight.SemiBold)
                    Text("Şimdi Çalıyor ekranı albüm kapağının rengini alır", color = TextDim, fontSize = 12.sp)
                }
                Switch(
                    checked = ThemeState.dynamicColor,
                    onCheckedChange = { ThemeState.setDynamic(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = ThemeState.current.primary)
                )
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Themes.size) { i ->
                val t = Themes[i]
                val sel = i == ThemeState.index
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(t.bg)
                        .border(2.dp, if (sel) t.primary else Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .clickable { ThemeState.set(ctx, i) }
                        .padding(12.dp)
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.8f).clip(RoundedCornerShape(12.dp)).background(t.card)) {
                        Box(Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(34.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(t.primary, t.tertiary))))
                        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.width(54.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.85f)))
                            Box(Modifier.width(38.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(t.dim))
                            Box(Modifier.width(54.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(t.primary, t.secondary))))
                        }
                        if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = t.primary, modifier = Modifier.align(Alignment.TopEnd).offset((-6).dp, 6.dp).size(20.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                        listOf(t.primary, t.secondary, t.tertiary).forEach {
                            Box(Modifier.padding(start = 3.dp).size(10.dp).clip(CircleShape).background(it))
                        }
                    }
                }
            }
        }
    }
}
