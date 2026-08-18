package wojtoteka.ovh.kajet.core.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp
import wojtoteka.ovh.kajet.core.model.CodeLanguage

object LanguageIcons {

    val Python by lazy {
        icon("Python") {
            moveTo(12f, 3.5f)
            lineTo(8f, 3.5f)
            curveTo(6.6f, 3.5f, 5.5f, 4.6f, 5.5f, 6f)
            lineTo(5.5f, 9.5f)
            lineTo(12f, 9.5f)
            lineTo(12f, 11f)
            lineTo(4.5f, 11f)
            curveTo(3.1f, 11f, 2f, 12.1f, 2f, 13.5f)
            lineTo(2f, 16f)
            dot(8f, 6.2f)
            moveTo(12f, 20.5f)
            lineTo(16f, 20.5f)
            curveTo(17.4f, 20.5f, 18.5f, 19.4f, 18.5f, 18f)
            lineTo(18.5f, 14.5f)
            lineTo(12f, 14.5f)
            lineTo(12f, 13f)
            lineTo(19.5f, 13f)
            curveTo(20.9f, 13f, 22f, 11.9f, 22f, 10.5f)
            lineTo(22f, 8f)
            dot(16f, 17.8f)
        }
    }

    val C by lazy {
        icon("C") {
            hexagon()
            moveTo(14.5f, 9f)
            curveTo(13.8f, 8.2f, 12.9f, 7.8f, 11.9f, 7.8f)
            curveTo(9.6f, 7.8f, 8.2f, 9.6f, 8.2f, 12f)
            curveTo(8.2f, 14.4f, 9.6f, 16.2f, 11.9f, 16.2f)
            curveTo(12.9f, 16.2f, 13.8f, 15.8f, 14.5f, 15f)
        }
    }

    val Cpp by lazy {
        icon("C++") {
            moveTo(12.5f, 8f)
            curveTo(11.8f, 7.2f, 10.9f, 6.8f, 9.9f, 6.8f)
            curveTo(7.4f, 6.8f, 5.8f, 8.9f, 5.8f, 12f)
            curveTo(5.8f, 15.1f, 7.4f, 17.2f, 9.9f, 17.2f)
            curveTo(10.9f, 17.2f, 11.8f, 16.8f, 12.5f, 16f)
            moveTo(16.4f, 9.5f); lineTo(16.4f, 14.5f)
            moveTo(13.9f, 12f); lineTo(18.9f, 12f)
            moveTo(21.2f, 9.5f); lineTo(21.2f, 14.5f)
            moveTo(18.7f, 12f); lineTo(23.7f, 12f)
        }
    }

    val Java by lazy {
        icon("Java") {
            moveTo(5f, 12f); lineTo(17f, 12f); lineTo(17f, 16.5f)
            curveTo(17f, 18.4f, 15.4f, 20f, 13.5f, 20f)
            lineTo(8.5f, 20f)
            curveTo(6.6f, 20f, 5f, 18.4f, 5f, 16.5f)
            close()
            moveTo(17f, 13.5f); lineTo(19f, 13.5f)
            curveTo(20.4f, 13.5f, 21.5f, 14.6f, 21.5f, 16f)
            curveTo(21.5f, 17.4f, 20.4f, 18.5f, 19f, 18.5f)
            lineTo(17.6f, 18.5f)
            moveTo(9f, 9f); curveTo(9f, 7.5f, 11f, 7f, 11f, 5.5f)
            moveTo(13f, 9f); curveTo(13f, 7.8f, 14.5f, 7.2f, 14.5f, 6f)
        }
    }

    val Kotlin by lazy {
        icon("Kotlin") {
            moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
            moveTo(4f, 20f); lineTo(20f, 4f)
            moveTo(4f, 12f); lineTo(12f, 4f)
        }
    }

    val JavaScript by lazy {
        icon("JavaScript") {
            braces()
            dot(12f, 12f)
        }
    }

    val TypeScript by lazy {
        icon("TypeScript") {
            braces()
            moveTo(9.5f, 9.5f); lineTo(14.5f, 9.5f)
            moveTo(12f, 9.5f); lineTo(12f, 15f)
        }
    }

    val CSharp by lazy {
        icon("C#") {
            moveTo(11.5f, 8f)
            curveTo(10.8f, 7.2f, 9.9f, 6.8f, 8.9f, 6.8f)
            curveTo(6.4f, 6.8f, 4.8f, 8.9f, 4.8f, 12f)
            curveTo(4.8f, 15.1f, 6.4f, 17.2f, 8.9f, 17.2f)
            curveTo(9.9f, 17.2f, 10.8f, 16.8f, 11.5f, 16f)
            moveTo(15.5f, 8f); lineTo(14.3f, 16f)
            moveTo(19f, 8f); lineTo(17.8f, 16f)
            moveTo(13.6f, 10.8f); lineTo(20.4f, 10.8f)
            moveTo(13.2f, 13.6f); lineTo(20f, 13.6f)
        }
    }

    val Go by lazy {
        icon("Go") {
            moveTo(3f, 9f); lineTo(9f, 9f)
            moveTo(2f, 12f); lineTo(8f, 12f)
            moveTo(3f, 15f); lineTo(9f, 15f)
            circle(15.5f, 12f, 5.5f)
            dot(14f, 10.5f)
            dot(17.5f, 10.5f)
        }
    }

    val Rust by lazy {
        icon("Rust") {
            circle(12f, 12f, 6.5f)
            circle(12f, 12f, 3f)
            moveTo(12f, 2.5f); lineTo(12f, 5.5f)
            moveTo(12f, 18.5f); lineTo(12f, 21.5f)
            moveTo(2.5f, 12f); lineTo(5.5f, 12f)
            moveTo(18.5f, 12f); lineTo(21.5f, 12f)
            moveTo(5.3f, 5.3f); lineTo(7.4f, 7.4f)
            moveTo(16.6f, 16.6f); lineTo(18.7f, 18.7f)
            moveTo(18.7f, 5.3f); lineTo(16.6f, 7.4f)
            moveTo(7.4f, 16.6f); lineTo(5.3f, 18.7f)
        }
    }

    val Php by lazy {
        icon("PHP") {
            moveTo(12f, 5.5f)
            curveTo(17.5f, 5.5f, 22f, 8.4f, 22f, 12f)
            curveTo(22f, 15.6f, 17.5f, 18.5f, 12f, 18.5f)
            curveTo(6.5f, 18.5f, 2f, 15.6f, 2f, 12f)
            curveTo(2f, 8.4f, 6.5f, 5.5f, 12f, 5.5f)
            close()
            moveTo(8f, 16f); lineTo(10f, 8.5f)
            moveTo(9.3f, 11.5f)
            curveTo(11.5f, 11.5f, 13f, 11.5f, 12.6f, 13f)
            curveTo(12.3f, 14.2f, 10.5f, 14.2f, 8.7f, 14.2f)
            moveTo(14.5f, 16f); lineTo(16.5f, 8.5f)
        }
    }

    val Ruby by lazy {
        icon("Ruby") {
            moveTo(7f, 5f); lineTo(17f, 5f); lineTo(21f, 10f); lineTo(12f, 20f)
            lineTo(3f, 10f); close()
            moveTo(3f, 10f); lineTo(21f, 10f)
            moveTo(7f, 5f); lineTo(9.5f, 10f); lineTo(12f, 20f)
            moveTo(17f, 5f); lineTo(14.5f, 10f); lineTo(12f, 20f)
        }
    }

    val Bash by lazy {
        icon("Bash") {
            moveTo(3f, 4.5f); lineTo(21f, 4.5f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
            moveTo(6.5f, 9.5f); lineTo(10f, 12.5f); lineTo(6.5f, 15.5f)
            moveTo(12f, 15.5f); lineTo(17.5f, 15.5f)
        }
    }

    val Sql by lazy {
        icon("SQL") {
            moveTo(4.5f, 6.5f)
            curveTo(4.5f, 5.1f, 7.9f, 4f, 12f, 4f)
            curveTo(16.1f, 4f, 19.5f, 5.1f, 19.5f, 6.5f)
            curveTo(19.5f, 7.9f, 16.1f, 9f, 12f, 9f)
            curveTo(7.9f, 9f, 4.5f, 7.9f, 4.5f, 6.5f)
            close()
            moveTo(4.5f, 6.5f); lineTo(4.5f, 17.5f)
            curveTo(4.5f, 18.9f, 7.9f, 20f, 12f, 20f)
            curveTo(16.1f, 20f, 19.5f, 18.9f, 19.5f, 17.5f)
            lineTo(19.5f, 6.5f)
            moveTo(4.5f, 12f)
            curveTo(4.5f, 13.4f, 7.9f, 14.5f, 12f, 14.5f)
            curveTo(16.1f, 14.5f, 19.5f, 13.4f, 19.5f, 12f)
        }
    }

    val PlainText by lazy {
        icon("Zwykły tekst") {
            moveTo(5.5f, 3.5f); lineTo(18.5f, 3.5f); lineTo(18.5f, 20.5f); lineTo(5.5f, 20.5f); close()
            moveTo(8.5f, 8.5f); lineTo(15.5f, 8.5f)
            moveTo(8.5f, 12f); lineTo(15.5f, 12f)
            moveTo(8.5f, 15.5f); lineTo(12.5f, 15.5f)
        }
    }

    fun forLanguage(language: CodeLanguage?): ImageVector = when (language) {
        CodeLanguage.PYTHON -> Python
        CodeLanguage.C -> C
        CodeLanguage.CPP -> Cpp
        CodeLanguage.JAVA -> Java
        CodeLanguage.KOTLIN -> Kotlin
        CodeLanguage.JAVASCRIPT -> JavaScript
        CodeLanguage.TYPESCRIPT -> TypeScript
        CodeLanguage.CSHARP -> CSharp
        CodeLanguage.GO -> Go
        CodeLanguage.RUST -> Rust
        CodeLanguage.PHP -> Php
        CodeLanguage.RUBY -> Ruby
        CodeLanguage.BASH -> Bash
        // MySQL dostaje tę samą ikonę co SQLite: to dwa języki, ale jedna
        // rzecz na ekranie — baza danych.
        CodeLanguage.SQL, CodeLanguage.MYSQL -> Sql
        else -> PlainText
    }
}

private fun PathBuilder.hexagon() {
    moveTo(12f, 2.6f); lineTo(20.1f, 7.3f); lineTo(20.1f, 16.7f)
    lineTo(12f, 21.4f); lineTo(3.9f, 16.7f); lineTo(3.9f, 7.3f); close()
}

private fun PathBuilder.braces() {
    moveTo(9f, 4.5f)
    curveTo(6.5f, 4.5f, 7.5f, 10f, 5f, 12f)
    curveTo(7.5f, 14f, 6.5f, 19.5f, 9f, 19.5f)
    moveTo(15f, 4.5f)
    curveTo(17.5f, 4.5f, 16.5f, 10f, 19f, 12f)
    curveTo(16.5f, 14f, 17.5f, 19.5f, 15f, 19.5f)
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}

private fun PathBuilder.dot(cx: Float, cy: Float) {
    moveTo(cx, cy)
    lineTo(cx + 0.01f, cy)
}

private fun icon(name: String, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathData(build),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
