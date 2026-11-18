package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Global NERV HUD (EVA-01 + EVA-02) Dark & Day/Light Tactical Palettes.
 * - Night HUD: Cybernetic obsidian background (#08060E), deep slate cards (#120E21),
 *   modal surfaces (#161129), EVA-01 green (#22C55E) + purple (#7C3AED),
 *   and EVA-02 crimson red (#EF4444) + alert orange (#F97316).
 * - Day/Light HUD: Tactical light background (#F4F3F8), pure white cards (#FFFFFF),
 *   modal surfaces (#FAF9FE), dark indigo-slate ink (#120E21), with high-contrast
 *   EVA-01 green/purple & EVA-02 red/orange accents.
 */
internal object NexoHudPalette {
  val BgPrimary = Color(0xFF08060E)
  val SurfaceCard = Color(0xFF120E21)
  val SurfaceModal = Color(0xFF161129)
  val SurfaceTactical = Color(0xFF1B1433)
  val BorderViolet = Color(0xFF7C3AED)
  val BorderSubtle = Color(0xFF332559)
  val EvaGreen = Color(0xFF22C55E)
  val EvaGreenDim = Color(0xFF16A34A)
  val EvaPurple = Color(0xFF7C3AED)
  val EvaPurpleBright = Color(0xFFA78BFA)
  val EvaRed = Color(0xFFEF4444)
  val EvaRedDim = Color(0xFFB91C1C)
  val EvaOrange = Color(0xFFF97316)
  val EvaAmber = Color(0xFFF59E0B)
  val TextPrimary = Color(0xFFF3EFFF)
  val TextSecondary = Color(0xFFAEA1CF)
  val OnNeonBlack = Color(0xFF08060E)
}

internal object NexoHudLightPalette {
  val BgPrimary = Color(0xFFF4F3F8)
  val SurfaceCard = Color(0xFFFFFFFF)
  val SurfaceModal = Color(0xFFFAF9FE)
  val SurfaceTactical = Color(0xFFEAE6F5)
  val BorderViolet = Color(0xFF7C3AED)
  val BorderSubtle = Color(0xFFCCC4E6)
  val EvaGreen = Color(0xFF16A34A)
  val EvaGreenSoft = Color(0xFF22C55E)
  val EvaPurple = Color(0xFF7C3AED)
  val EvaPurpleDeep = Color(0xFF5B21B6)
  val EvaRed = Color(0xFFDC2626)
  val EvaRedSoft = Color(0xFFFEE2E2)
  val EvaOrange = Color(0xFFEA580C)
  val EvaAmber = Color(0xFFD97706)
  val TextPrimary = Color(0xFF120E21)
  val TextSecondary = Color(0xFF4A3F6B)
  val OnPrimaryLight = Color(0xFFFFFFFF)
}

@Immutable
data class NexoSemanticColors(
  val evaGreen: Color = NexoHudPalette.EvaGreen,
  val evaPurple: Color = NexoHudPalette.EvaPurple,
  val evaRed: Color = NexoHudPalette.EvaRed,
  val evaOrange: Color = NexoHudPalette.EvaOrange,
  val warning: Color = NexoHudPalette.EvaOrange,
  val primarySoft: Color = NexoHudPalette.EvaGreenDim,
  val secondarySoft: Color = NexoHudPalette.EvaPurpleBright,
  val tacticalOrange: Color = NexoHudPalette.EvaOrange,
  val tacticalAmber: Color = NexoHudPalette.EvaAmber,
  val tacticalSurface: Color = NexoHudPalette.SurfaceTactical,
  val modalSurface: Color = NexoHudPalette.SurfaceModal,
  val hudBorder: Color = NexoHudPalette.BorderSubtle,
)
