package com.example.killquestion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.killquestion.ui.theme.*

@Composable
fun AnimatedBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(ZenBackgroundStart, ZenBackgroundEnd)
            )
        )
        drawCircle(
            color = ZenGreenAccent.copy(alpha = 0.05f),
            radius = size.width * 0.8f,
            center = Offset(size.width * 0.9f, size.height * 0.1f)
        )
        drawCircle(
            color = ZenGreenPrimary.copy(alpha = 0.03f),
            radius = size.width * 0.6f,
            center = Offset(size.width * 0.1f, size.height * 0.9f)
        )
    }
}

@Composable
fun BouncyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scaleDown: Float = 0.95f,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    backgroundColor: Color = ZenSurface,
    elevation: Dp = 4.dp,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        shadowElevation = elevation,
        border = border
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
fun SmallIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = TextSecondary,
    containerColor: Color = Color.White
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        border = if (containerColor == Color.White) BorderStroke(1.dp, Color.Black.copy(0.05f)) else null,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}