package com.example.financehome.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary palette – deep teal ──────────────────────────────────────────────
val Teal80          = Color(0xFF80CBC4)   // light variant (dark theme)
val Teal60          = Color(0xFF4DB6AC)
val Teal40          = Color(0xFF00897B)   // primary (light theme)
val Teal20          = Color(0xFF00695C)
val TealContainer80 = Color(0xFFB2DFDB)
val TealContainer30 = Color(0xFF004D40)

// ── Secondary palette – light blue-teal ──────────────────────────────────────
val Cyan80          = Color(0xFF80DEEA)
val Cyan40          = Color(0xFF26A69A)
val Cyan20          = Color(0xFF00796B)
val CyanContainer80 = Color(0xFFB2EBF2)
val CyanContainer30 = Color(0xFF006064)

// ── Tertiary palette – indigo accent ─────────────────────────────────────────
val Indigo80        = Color(0xFF9FA8DA)
val Indigo40        = Color(0xFF3F51B5)
val Indigo20        = Color(0xFF283593)
val IndigoContainer = Color(0xFFE8EAF6)

// ── Error / Debt ──────────────────────────────────────────────────────────────
val Red80           = Color(0xFFEF9A9A)
val Red40           = Color(0xFFE53935)   // error / debit / debt
val Red20           = Color(0xFFB71C1C)
val RedContainer80  = Color(0xFFFFCDD2)
val RedContainer30  = Color(0xFF7F0000)

// ── Semantic: Success / Income / Gain ─────────────────────────────────────────
val Green80         = Color(0xFFA5D6A7)
val Green40         = Color(0xFF43A047)   // income / credit / positive net worth
val Green20         = Color(0xFF1B5E20)
val GreenContainer  = Color(0xFFE8F5E9)

// ── Semantic: Warning / Approaching Budget Limit ──────────────────────────────
val Amber80         = Color(0xFFFFCC80)
val Amber40         = Color(0xFFFFA000)   // warning / near-limit
val Amber20         = Color(0xFFE65100)
val AmberContainer  = Color(0xFFFFF8E1)

// ── Neutrals ──────────────────────────────────────────────────────────────────
val Neutral99       = Color(0xFFF5FAF9)   // background (light)
val Neutral95       = Color(0xFFECF4F3)
val Neutral90       = Color(0xFFDAE5E3)
val Neutral30       = Color(0xFF3A4A49)
val Neutral20       = Color(0xFF263231)
val Neutral10       = Color(0xFF131F1E)   // background (dark)
val Neutral06       = Color(0xFF0D1514)

// ── Neutral Variant ───────────────────────────────────────────────────────────
val NeutralVariant90 = Color(0xFFCED8D7)
val NeutralVariant80 = Color(0xFFB1BBBA)
val NeutralVariant50 = Color(0xFF5F7170)
val NeutralVariant30 = Color(0xFF3A4947)
val NeutralVariant20 = Color(0xFF2A3836)

// ── Light colour scheme raw values ───────────────────────────────────────────
val md_theme_light_primary            = Teal40
val md_theme_light_onPrimary          = Color.White
val md_theme_light_primaryContainer   = TealContainer80
val md_theme_light_onPrimaryContainer = Teal20

val md_theme_light_secondary            = Cyan40
val md_theme_light_onSecondary          = Color.White
val md_theme_light_secondaryContainer   = CyanContainer80
val md_theme_light_onSecondaryContainer = Cyan20

val md_theme_light_tertiary            = Indigo40
val md_theme_light_onTertiary          = Color.White
val md_theme_light_tertiaryContainer   = IndigoContainer
val md_theme_light_onTertiaryContainer = Indigo20

val md_theme_light_error            = Red40
val md_theme_light_onError          = Color.White
val md_theme_light_errorContainer   = RedContainer80
val md_theme_light_onErrorContainer = Red20

val md_theme_light_background   = Neutral99
val md_theme_light_onBackground = Neutral10
val md_theme_light_surface      = Neutral99
val md_theme_light_onSurface    = Neutral10

val md_theme_light_surfaceVariant   = NeutralVariant90
val md_theme_light_onSurfaceVariant = NeutralVariant30
val md_theme_light_outline          = NeutralVariant50
val md_theme_light_outlineVariant   = NeutralVariant90

val md_theme_light_inverseSurface   = Neutral20
val md_theme_light_inverseOnSurface = Neutral95
val md_theme_light_inversePrimary   = Teal80
val md_theme_light_surfaceTint      = Teal40
val md_theme_light_scrim            = Color.Black

// ── Dark colour scheme raw values ────────────────────────────────────────────
val md_theme_dark_primary            = Teal80
val md_theme_dark_onPrimary          = Teal20
val md_theme_dark_primaryContainer   = TealContainer30
val md_theme_dark_onPrimaryContainer = TealContainer80

val md_theme_dark_secondary            = Cyan80
val md_theme_dark_onSecondary          = Cyan20
val md_theme_dark_secondaryContainer   = CyanContainer30
val md_theme_dark_onSecondaryContainer = CyanContainer80

val md_theme_dark_tertiary            = Indigo80
val md_theme_dark_onTertiary          = Indigo20
val md_theme_dark_tertiaryContainer   = Indigo40
val md_theme_dark_onTertiaryContainer = IndigoContainer

val md_theme_dark_error            = Red80
val md_theme_dark_onError          = Red20
val md_theme_dark_errorContainer   = RedContainer30
val md_theme_dark_onErrorContainer = RedContainer80

val md_theme_dark_background   = Neutral10
val md_theme_dark_onBackground = Neutral90
val md_theme_dark_surface      = Neutral10
val md_theme_dark_onSurface    = Neutral90

val md_theme_dark_surfaceVariant   = NeutralVariant30
val md_theme_dark_onSurfaceVariant = NeutralVariant80
val md_theme_dark_outline          = NeutralVariant50
val md_theme_dark_outlineVariant   = NeutralVariant30

val md_theme_dark_inverseSurface   = Neutral90
val md_theme_dark_inverseOnSurface = Neutral20
val md_theme_dark_inversePrimary   = Teal40
val md_theme_dark_surfaceTint      = Teal80
val md_theme_dark_scrim            = Color.Black

// ── Convenience semantic aliases (use in UI code) ─────────────────────────────
/** Use for credit / income / positive amounts */
val ColorIncome      = Green40
val ColorIncomeLight = Green80

/** Use for debit / expense / negative amounts */
val ColorExpense      = Red40
val ColorExpenseLight = Red80

/** Use for approaching-budget-limit warnings */
val ColorWarning      = Amber40
val ColorWarningLight = Amber80

/** Fully-opaque scrim tint over hero cards */
val ScrimDark = Color(0x99000000)
