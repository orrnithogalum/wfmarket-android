package com.wfmarket.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The platinum coin, drawn as a two-tone Material icon: a notched rim around a face with an "I" cut
 * out. Being a vector, it's sharp at any size. Tint it like any other icon. The source is
 * `app/src/main/art/platinum.svg`.
 */
val PlatinumIcon: ImageVector by lazy {
    ImageVector.Builder("Platinum", 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(RIM).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        .addPath(PathParser().parsePathString(FACE).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black), fillAlpha = 0.62f)
        .build()
}

