package com.example.kaptus.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat

val CinemaBlack = Color(0xFF000000)
val CaptionWhite = Color(0xFFF8F8F4)
val MutedGray = Color(0xFFA9A9A2)
val SyncAmber = Color(0xFFFFC857)
val PanelBlack = Color(0xFF11110F)

internal val CinemaColorScheme = darkColorScheme(
    primary = SyncAmber,
    onPrimary = Color(0xFF211A08),
    primaryContainer = Color(0xFF463619),
    onPrimaryContainer = Color(0xFFFFDEA0),
    inversePrimary = Color(0xFF755A1C),
    secondary = Color(0xFFD3C4AA),
    onSecondary = Color(0xFF372F21),
    secondaryContainer = Color(0xFF302B23),
    onSecondaryContainer = Color(0xFFECE0CA),
    tertiary = Color(0xFFBCCEB5),
    onTertiary = Color(0xFF283522),
    tertiaryContainer = Color(0xFF34452F),
    onTertiaryContainer = Color(0xFFD8EACE),
    background = CinemaBlack,
    onBackground = CaptionWhite,
    surface = PanelBlack,
    onSurface = CaptionWhite,
    surfaceVariant = Color(0xFF242420),
    onSurfaceVariant = MutedGray,
    surfaceDim = Color(0xFF11110F),
    surfaceBright = Color(0xFF383834),
    surfaceContainerLowest = CinemaBlack,
    surfaceContainerLow = Color(0xFF151513),
    surfaceContainer = Color(0xFF1C1C19),
    surfaceContainerHigh = Color(0xFF272723),
    surfaceContainerHighest = Color(0xFF32322D),
    surfaceTint = SyncAmber,
    inverseSurface = Color(0xFFE5E2DB),
    inverseOnSurface = Color(0xFF30302C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF51201E),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF93938A),
    outlineVariant = Color(0xFF45453E),
    scrim = CinemaBlack
)

private val KaptusShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Previewable directions. The shipping default remains cinema until a direction is selected. */
enum class KaptusVisualStyle { CinemaAmber, MaterialSage, WarmPaper, MidnightBlue }

private val SageColorScheme = lightColorScheme(
    primary = Color(0xFF356844), onPrimary = Color.White,
    primaryContainer = Color(0xFFCEEBCF), onPrimaryContainer = Color(0xFF183E24),
    secondary = Color(0xFF526350), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEE8D8), onSecondaryContainer = Color(0xFF283B2A),
    tertiary = Color(0xFF3C6570), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCEEAF0), onTertiaryContainer = Color(0xFF1F4954),
    background = Color(0xFFF5F8F0), onBackground = Color(0xFF20281F),
    surface = Color(0xFFF5F8F0), onSurface = Color(0xFF20281F),
    surfaceVariant = Color(0xFFDFE5D9), onSurfaceVariant = Color(0xFF4A5749),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4EA),
    surfaceContainer = Color(0xFFE9EFE2), surfaceContainerHigh = Color(0xFFE2E9DA),
    surfaceContainerHighest = Color(0xFFDBE3D3),
    outline = Color(0xFF727D6D), outlineVariant = Color(0xFFC2CCBA),
    surfaceTint = Color(0xFF356844)
)

private val PaperColorScheme = lightColorScheme(
    primary = Color(0xFF36392F), onPrimary = Color(0xFFFFFCF5),
    primaryContainer = Color(0xFFEAE3D4), onPrimaryContainer = Color(0xFF36392F),
    secondary = Color(0xFF71543C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E3D2), onSecondaryContainer = Color(0xFF4B3D2C),
    tertiary = Color(0xFF50664B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE8D4), onTertiaryContainer = Color(0xFF354B2F),
    background = Color(0xFFFAF6EE), onBackground = Color(0xFF282A26),
    surface = Color(0xFFFAF6EE), onSurface = Color(0xFF282A26),
    surfaceVariant = Color(0xFFE9E2D5), onSurfaceVariant = Color(0xFF626052),
    surfaceContainerLowest = Color(0xFFFFFCF6), surfaceContainerLow = Color(0xFFF4EFE5),
    surfaceContainer = Color(0xFFEFE9DD), surfaceContainerHigh = Color(0xFFE9E2D5),
    surfaceContainerHighest = Color(0xFFE3DBCC),
    outline = Color(0xFF817B6C), outlineVariant = Color(0xFFCFC7B7),
    surfaceTint = Color(0xFF71543C)
)

private val BlueColorScheme = CinemaColorScheme.copy(
    primary = Color(0xFF9DCBFF), onPrimary = Color(0xFF103351),
    primaryContainer = Color(0xFF23466C), onPrimaryContainer = Color(0xFFD3E7FF),
    secondary = Color(0xFFB5C7DD), onSecondary = Color(0xFF203447),
    secondaryContainer = Color(0xFF20364D), onSecondaryContainer = Color(0xFFD7E5F7),
    tertiary = Color(0xFFCCC2EC), onTertiary = Color(0xFF342D4C),
    tertiaryContainer = Color(0xFF46405E), onTertiaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFF071422), onBackground = Color(0xFFE6EDF6),
    surface = Color(0xFF0B1929), onSurface = Color(0xFFE6EDF6),
    surfaceVariant = Color(0xFF25364A), onSurfaceVariant = Color(0xFFB1C1D4),
    surfaceContainerLowest = Color(0xFF04101D), surfaceContainerLow = Color(0xFF0F2033),
    surfaceContainer = Color(0xFF14283E), surfaceContainerHigh = Color(0xFF1D334B),
    surfaceContainerHighest = Color(0xFF293F58),
    outline = Color(0xFF8498AF), outlineVariant = Color(0xFF344D66),
    surfaceTint = Color(0xFF9DCBFF)
)

@Composable
fun KaptusTheme(
    style: KaptusVisualStyle = KaptusVisualStyle.CinemaAmber,
    content: @Composable () -> Unit
) {
    val colors = when (style) {
        KaptusVisualStyle.CinemaAmber -> CinemaColorScheme
        KaptusVisualStyle.MaterialSage -> SageColorScheme
        KaptusVisualStyle.WarmPaper -> PaperColorScheme
        KaptusVisualStyle.MidnightBlue -> BlueColorScheme
    }
    val typography = when (style) {
        KaptusVisualStyle.WarmPaper -> KaptusTypography.copy(
            headlineLarge = KaptusTypography.headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = KaptusTypography.headlineMedium.copy(fontFamily = FontFamily.Serif),
            headlineSmall = KaptusTypography.headlineSmall.copy(fontFamily = FontFamily.Serif),
            displaySmall = KaptusTypography.displaySmall.copy(fontFamily = FontFamily.Serif)
        )
        KaptusVisualStyle.MidnightBlue -> KaptusTypography.copy(
            headlineLarge = KaptusTypography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineMedium = KaptusTypography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        else -> KaptusTypography
    }
    val shapes = when (style) {
        KaptusVisualStyle.MaterialSage -> KaptusShapes.copy(
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        )
        KaptusVisualStyle.WarmPaper -> KaptusShapes.copy(
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp)
        )
        else -> KaptusShapes
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                val light = style == KaptusVisualStyle.MaterialSage || style == KaptusVisualStyle.WarmPaper
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = shapes,
        content = content
    )
}
