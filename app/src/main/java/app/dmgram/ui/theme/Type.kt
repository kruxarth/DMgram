package app.dmgram.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.dmgram.R

val DMGramTypography = Typography()

// DMGram's wordmark: DM Sans (SIL OFL 1.1, license in assets/licenses/DMSans-OFL.txt),
// bundled so it never needs the network. The variable font is set to its display
// optical size, which tightens spacing and sharpens the letters at header sizes.
@OptIn(ExperimentalTextApi::class)
private val WordmarkFamily = FontFamily(
    Font(
        resId = R.font.dm_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("opsz", 40f),
        ),
    ),
)

fun wordmarkStyle(size: TextUnit): TextStyle = TextStyle(
    fontFamily = WordmarkFamily,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    letterSpacing = (-0.02 * size.value).sp,
)
