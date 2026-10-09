package cn.inkledger.app

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.graphics.PathParser

val White = Color.White
val Light = Color(0xfff0f0f0)
val LightMid = Color(0xffdedede)
val Mid = Color(0xff888888)
val MidDark = Color(0xff555555)
val Dark = Color(0xff333333)
val Expense = Color(0xffb34c4c)
val Income = Color(0xff37745c)
val LocalBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }
val LocalEffect = staticCompositionLocalOf { "balanced" }

@Composable
fun InkIcon(
    name: String,
    modifier: Modifier = Modifier.size(22.dp),
    color: Color = Dark,
    weight: Float = 1.65f,
) {
    val path =
        remember(name) {
            PathParser.createPathFromPathData(iconPaths[name] ?: iconPaths.getValue("other"))
        }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
    Canvas(modifier) {
        paint.color = color.toArgb()
        paint.strokeWidth = weight
        drawIntoCanvas { c ->
            c.save()
            c.scale(size.width / 24f, size.height / 24f)
            c.nativeCanvas.drawPath(path, paint)
            c.restore()
        }
    }
}

@Composable
fun IconButton(
    name: String,
    description: String,
    onClick: () -> Unit,
    color: Color = White,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkIcon(name, color = color)
    }
}

@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .shadow(8.dp, shape, ambientColor = White, spotColor = White)
            .clip(shape)
            .background(White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        content = content,
    )
}

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    sample: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = LocalBackdrop.current
    val effect = LocalEffect.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val blur =
        when (effect) {
            "strong" -> 30f
            "weak" -> 12f
            else -> 22f
        }
    val alpha =
        when (effect) {
            "strong" -> .42f
            "weak" -> .83f
            else -> .64f
        }
    Box(
        modifier
            .shadow(5.dp, shape, ambientColor = White, spotColor = White)
            .clip(shape)
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .border(1.dp, White.copy(.65f), shape)
    ) {
        if (sample && layer != null && Build.VERSION.SDK_INT >= 31)
            Canvas(
                Modifier.matchParentSize().graphicsLayer {
                    renderEffect = BlurEffect(blur.dp.toPx(), blur.dp.toPx(), TileMode.Clamp)
                }
            ) {
                translate(-origin.x, -origin.y) { drawLayer(layer) }
            }
        Box(
            Modifier.matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(White.copy(.90f), White.copy(alpha), White.copy(.27f))
                    )
                )
        )
        Canvas(Modifier.matchParentSize()) {
            // The narrow rim is brightest at lower left and upper right.
            val radius = 6.dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    listOf(White.copy(.9f), Color.Transparent),
                    Offset(size.width - 5.dp.toPx(), 5.dp.toPx()),
                    radius,
                ),
                radius,
                Offset(size.width - 5.dp.toPx(), 5.dp.toPx()),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(White.copy(.9f), Color.Transparent),
                    Offset(5.dp.toPx(), size.height - 5.dp.toPx()),
                    radius,
                ),
                radius,
                Offset(5.dp.toPx(), size.height - 5.dp.toPx()),
            )
        }
        content()
    }
}

@Composable
fun CatIcon(id: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Mid else Light)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        InkIcon(if (selected) "check" else id, color = if (selected) White else Dark)
    }
}

@Composable
fun Progress(used: Long, total: Long, modifier: Modifier = Modifier) {
    Box(modifier.height(7.dp).clip(CircleShape).background(Light)) {
        Box(
            Modifier.fillMaxWidth(
                    if (total > 0) (used.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
                )
                .fillMaxHeight()
                .background(Dark)
        )
    }
}

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    size: Int = 14,
    bold: Boolean = false,
) {
    Text(
        text,
        modifier,
        color,
        fontSize = size.sp,
        lineHeight = (size * 1.25f).sp,
        letterSpacing = 0.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

val LocalDeletingIds = staticCompositionLocalOf<Set<String>> { emptySet() }
val LocalDeleteProgress = staticCompositionLocalOf<() -> Float> { { 0f } }
