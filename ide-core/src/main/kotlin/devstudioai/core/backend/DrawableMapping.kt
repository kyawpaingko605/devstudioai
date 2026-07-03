package devstudioai.core.backend

import devstudioai.android.support.preview.DrawablePreview
import devstudioai.android.support.preview.GradientSpec
import devstudioai.android.support.preview.Layer
import devstudioai.android.support.preview.StateLayer
import devstudioai.android.support.preview.VectorPath
import devstudioai.ui.backend.UiDrawable
import devstudioai.ui.backend.UiGradient
import devstudioai.ui.backend.UiLayer
import devstudioai.ui.backend.UiStateLayer
import devstudioai.ui.backend.UiVectorPath

/** Maps the engine's [DrawablePreview] model onto the neutral [UiDrawable] DTO the Compose canvas renders. */
internal object DrawableMapping {

    fun toUi(d: DrawablePreview): UiDrawable = when (d) {
        is DrawablePreview.SolidColor -> UiDrawable.SolidColor(d.color)
        is DrawablePreview.Shape -> d.spec.let { s ->
            UiDrawable.Shape(
                shape = s.shape.name.lowercase(),
                solidColor = s.solidColor,
                gradient = s.gradient?.let(::toUiGradient),
                strokeColor = s.strokeColor,
                strokeWidthDp = s.strokeWidthDp,
                dashWidthDp = s.dashWidthDp,
                dashGapDp = s.dashGapDp,
                cornerTopLeftDp = s.cornerTopLeftDp,
                cornerTopRightDp = s.cornerTopRightDp,
                cornerBottomRightDp = s.cornerBottomRightDp,
                cornerBottomLeftDp = s.cornerBottomLeftDp,
                intrinsicWidthDp = s.intrinsicWidthDp,
                intrinsicHeightDp = s.intrinsicHeightDp,
                innerRadiusFraction = s.innerRadiusFraction,
                thicknessFraction = s.thicknessFraction,
            )
        }

        is DrawablePreview.Vector -> d.spec.let { v ->
            UiDrawable.Vector(
                widthDp = v.widthDp, heightDp = v.heightDp,
                viewportWidth = v.viewportWidth, viewportHeight = v.viewportHeight,
                rootAlpha = v.rootAlpha,
                paths = v.paths.map(::toUiVectorPath),
            )
        }

        is DrawablePreview.Layers -> UiDrawable.Layers(d.layers.map(::toUiLayer))
        is DrawablePreview.States -> UiDrawable.States(
            states = d.states.map(::toUiStateLayer),
            defaultLayer = d.defaultLayer?.let(::toUi),
        )

        is DrawablePreview.BitmapRef -> UiDrawable.Bitmap(d.resType, d.resName, d.filePath)
        is DrawablePreview.Unsupported -> UiDrawable.Unsupported(d.rootTag, d.message)
    }

    private fun toUiGradient(g: GradientSpec) = UiGradient(
        kind = g.kind.name.lowercase(),
        startColor = g.startColor,
        centerColor = g.centerColor,
        endColor = g.endColor,
        angle = g.angle,
        centerX = g.centerX,
        centerY = g.centerY,
        radiusFraction = g.radiusFraction,
    )

    private fun toUiVectorPath(p: VectorPath) = UiVectorPath(
        pathData = p.pathData, fillColor = p.fillColor, strokeColor = p.strokeColor,
        strokeWidthVp = p.strokeWidthVp, fillAlpha = p.fillAlpha, strokeAlpha = p.strokeAlpha,
    )

    private fun toUiLayer(l: Layer) = UiLayer(
        drawable = toUi(l.drawable),
        insetLeftDp = l.insetLeftDp, insetTopDp = l.insetTopDp,
        insetRightDp = l.insetRightDp, insetBottomDp = l.insetBottomDp,
    )

    private fun toUiStateLayer(s: StateLayer) = UiStateLayer(s.states, toUi(s.drawable))
}
