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

private const val RIM = "M18.394,20.641A10.75,10.75 0 0 1 5.606,20.641L5.495,18.505L3.359,18.394A10.75,10.75 0 0 1 3.359,5.606L5.495,5.495L5.606,3.359A10.75,10.75 0 0 1 18.394,3.359L18.505,5.495L20.641,5.606A10.75,10.75 0 0 1 20.641,18.394L18.505,18.505L18.394,20.641ZM17.055,18.958A8.6,8.6 0 0 1 6.945,18.958L6.626,17.374L5.042,17.055A8.6,8.6 0 0 1 5.042,6.945L6.626,6.626L6.945,5.042A8.6,8.6 0 0 1 17.055,5.042L17.374,6.626L18.958,6.945A8.6,8.6 0 0 1 18.958,17.055L17.374,17.374L17.055,18.958Z"
private const val FACE = "M17.055,18.958A8.6,8.6 0 0 1 6.945,18.958L6.626,17.374L5.042,17.055A8.6,8.6 0 0 1 5.042,6.945L6.626,6.626L6.945,5.042A8.6,8.6 0 0 1 17.055,5.042L17.374,6.626L18.958,6.945A8.6,8.6 0 0 1 18.958,17.055L17.374,17.374L17.055,18.958ZM8.1,6.9H15.9V8.9H14.1V15.1H15.9V17.1H8.1V15.1H9.9V8.9H8.1Z"
