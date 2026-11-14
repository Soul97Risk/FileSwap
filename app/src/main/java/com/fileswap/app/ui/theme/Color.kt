/*
 * Copyright 2026 kndnb666
 * SPDX-License-Identifier: Apache-2.0
 */

package com.fileswap.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Material 3 标准色板，种子色 #006C4C（绿）。
// 按 M3 tonal palette 生成全部角色值：旧版只填 15 个角色、缺 tertiary 与
// surface 层级，导致非动态取色设备上"不像标准 M3"。此处补满。
// ---------------------------------------------------------------------------

// Light
val Primary = Color(0xFF006C4C)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF89F8C7)
val OnPrimaryContainer = Color(0xFF002114)
val InversePrimaryLight = Color(0xFF57DCA8)

val Secondary = Color(0xFF4C6358)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFCEE9D9)
val OnSecondaryContainer = Color(0xFF092017)

val Tertiary = Color(0xFF3D6373)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFC1E8FB)
val OnTertiaryContainer = Color(0xFF001E2B)

val Background = Color(0xFFFBFDF9)
val OnBackground = Color(0xFF191C1A)
val Surface = Color(0xFFFBFDF9)
val OnSurface = Color(0xFF191C1A)
val SurfaceVariant = Color(0xFFDBE5DD)
val OnSurfaceVariant = Color(0xFF404943)
val InverseSurfaceLight = Color(0xFF2E312F)
val InverseOnSurfaceLight = Color(0xFFEFF1EE)

val ErrorColor = Color(0xFFBA1A1A)
val OnErrorColor = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val OutlineLight = Color(0xFF6F7973)
val OutlineVariantLight = Color(0xFFBEC9C2)
val Scrim = Color(0xFF000000)
val SurfaceBrightLight = Color(0xFFFBFDF9)
val SurfaceDimLight = Color(0xFFDADFD9)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF5FAF5)
val SurfaceContainerLight = Color(0xFFEFF4EF)
val SurfaceContainerHighLight = Color(0xFFE9EFE9)
val SurfaceContainerHighestLight = Color(0xFFE4EAE4)

// Dark
val PrimaryDark = Color(0xFF57DCA8)
val OnPrimaryDark = Color(0xFF003822)
val PrimaryContainerDark = Color(0xFF005234)
val OnPrimaryContainerDark = Color(0xFF74F8B7)

val SecondaryDark = Color(0xFFB2CCBF)
val OnSecondaryDark = Color(0xFF18352A)
val SecondaryContainerDark = Color(0xFF314B3F)
val OnSecondaryContainerDark = Color(0xFFCEE9D9)

val TertiaryDark = Color(0xFFA4CDDF)
val OnTertiaryDark = Color(0xFF053544)
val TertiaryContainerDark = Color(0xFF234B5B)
val OnTertiaryContainerDark = Color(0xFFC1E8FB)

val BackgroundDark = Color(0xFF101412)
val OnBackgroundDark = Color(0xFFDDE4DE)
val SurfaceDark = Color(0xFF101412)
val OnSurfaceDark = Color(0xFFDDE4DE)
val SurfaceVariantDark = Color(0xFF404943)
val OnSurfaceVariantDark = Color(0xFFBFC9C1)
val InverseSurfaceDark = Color(0xFFDDE4DE)
val InverseOnSurfaceDark = Color(0xFF2E312F)

val ErrorColorDark = Color(0xFFFFB4AB)
val OnErrorColorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val OutlineDark = Color(0xFF89938C)
val OutlineVariantDark = Color(0xFF404943)
val SurfaceBrightDark = Color(0xFF363B38)
val SurfaceDimDark = Color(0xFF101412)
val SurfaceContainerLowestDark = Color(0xFF0B0F0D)
val SurfaceContainerLowDark = Color(0xFF191C1A)
val SurfaceContainerDark = Color(0xFF1D211F)
val SurfaceContainerHighDark = Color(0xFF272B29)
val SurfaceContainerHighestDark = Color(0xFF323634)

val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimaryLight,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Primary,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    error = ErrorColor,
    onError = OnErrorColor,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = Scrim,
    surfaceBright = SurfaceBrightLight,
    surfaceDim = SurfaceDimLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight
)

val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    inversePrimary = Primary,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceTint = PrimaryDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    error = ErrorColorDark,
    onError = OnErrorColorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = Scrim,
    surfaceBright = SurfaceBrightDark,
    surfaceDim = SurfaceDimDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark
)
