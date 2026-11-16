package com.libertelos.app.security

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 用正脸轮廓的相对几何比对本人。只在录入时保存模板，解锁时必须对上这个模板。
 */
data class FaceSample(
    val ratios: FloatArray,
    val contours: FloatArray
)

data class FaceTemplate(
    val ratios: FloatArray,
    val ratioLimit: Float,
    val contours: FloatArray,
    val contourLimit: Float
) {
    fun matches(sample: FaceSample): Boolean {
        if (sample.ratios.size != ratios.size || sample.contours.size != contours.size) return false
        val ratioDistance = FaceGeometry.l2(sample.ratios, ratios)
        if (ratioDistance > ratioLimit) return false
        val contourDistance = FaceGeometry.l2(sample.contours, contours)
        return contourDistance <= contourLimit || ratioDistance <= ratioLimit * 0.8f
    }

    fun serialize(): String = buildString {
        append("1;")
        append(ratioLimit.toBits())
        append(';')
        append(contourLimit.toBits())
        append(';')
        append(ratios.joinToString(",") { it.toBits().toString() })
        append(';')
        append(contours.joinToString(",") { it.toBits().toString() })
    }

    companion object {
        fun fromSamples(samples: List<FaceSample>): FaceTemplate? {
            if (samples.size < 2) return null
            val ratioMean = FaceGeometry.mean(samples.map { it.ratios })
            val contourMean = FaceGeometry.mean(samples.map { it.contours })
            val ratioSpread = samples.maxOf { FaceGeometry.l2(it.ratios, ratioMean) }
            val contourSpread = samples.maxOf { FaceGeometry.l2(it.contours, contourMean) }
            if (ratioSpread > 0.30f || contourSpread > 0.95f) return null
            return FaceTemplate(
                ratios = ratioMean,
                ratioLimit = (ratioSpread * 3.2f).coerceIn(0.34f, 0.48f),
                contours = contourMean,
                contourLimit = (contourSpread * 3.2f).coerceIn(0.9f, 1.5f)
            )
        }

        fun parse(raw: String): FaceTemplate? = runCatching {
            val parts = raw.split(';')
            if (parts.size != 5 || parts[0] != "1") return null
            val ratios = decode(parts[3])
            val contours = decode(parts[4])
            if (ratios.isEmpty() || contours.isEmpty()) return null
            FaceTemplate(
                ratios = ratios,
                ratioLimit = Float.fromBits(parts[1].toInt()),
                contours = contours,
                contourLimit = Float.fromBits(parts[2].toInt())
            )
        }.getOrNull()

        private fun decode(csv: String): FloatArray =
            csv.split(',').map { Float.fromBits(it.toInt()) }.toFloatArray()
    }
}

sealed class FaceRead {
    data class Ok(val sample: FaceSample) : FaceRead()
    data class Reject(val message: String) : FaceRead()
}

object FaceGeometry {
    private val contourSpec = listOf(
        FaceContour.LEFT_EYE to 6,
        FaceContour.RIGHT_EYE to 6,
        FaceContour.LEFT_EYEBROW_TOP to 4,
        FaceContour.RIGHT_EYEBROW_TOP to 4,
        FaceContour.NOSE_BRIDGE to 3,
        FaceContour.NOSE_BOTTOM to 3,
        FaceContour.UPPER_LIP_TOP to 5,
        FaceContour.LOWER_LIP_BOTTOM to 5,
        FaceContour.LEFT_CHEEK to 3,
        FaceContour.RIGHT_CHEEK to 3
    )

    fun read(face: Face): FaceRead {
        val yaw = kotlin.math.abs(face.headEulerAngleY)
        val pitch = kotlin.math.abs(face.headEulerAngleX)
        val roll = kotlin.math.abs(face.headEulerAngleZ)
        if (yaw > 32f || roll > 30f) return FaceRead.Reject("请正对镜头")
        if (pitch > 42f) return FaceRead.Reject("请把手机举到与脸同高")
        val smile = face.smilingProbability
        if (smile != null && smile > 0.6f) return FaceRead.Reject("请保持自然表情")
        val leftOpen = face.leftEyeOpenProbability
        val rightOpen = face.rightEyeOpenProbability
        if ((leftOpen != null && leftOpen < 0.35f) || (rightOpen != null && rightOpen < 0.35f)) {
            return FaceRead.Reject("请睁眼")
        }

        val leftEye = points(face, FaceContour.LEFT_EYE) ?: return FaceRead.Reject("请将脸放入人像框")
        val rightEye = points(face, FaceContour.RIGHT_EYE) ?: return FaceRead.Reject("请将脸放入人像框")
        val leftCenter = centroid(leftEye)
        val rightCenter = centroid(rightEye)
        val eyeDist = distance(leftCenter, rightCenter)
        if (eyeDist < 20f) return FaceRead.Reject("请靠近一些")

        val originX = (leftCenter.x + rightCenter.x) / 2f
        val originY = (leftCenter.y + rightCenter.y) / 2f
        val angle = atan2(rightCenter.y - leftCenter.y, rightCenter.x - leftCenter.x)
        fun norm(p: PointF): PointF {
            val dx = p.x - originX
            val dy = p.y - originY
            val cos = cos(-angle)
            val sin = sin(-angle)
            return PointF(
                (dx * cos - dy * sin) / eyeDist,
                (dx * sin + dy * cos) / eyeDist
            )
        }

        val faceOval = points(face, FaceContour.FACE)?.map(::norm)
        val noseBottom = points(face, FaceContour.NOSE_BOTTOM)?.map(::norm)
            ?: return FaceRead.Reject("请将脸放入人像框")
        val upperLip = points(face, FaceContour.UPPER_LIP_TOP)?.map(::norm)
            ?: return FaceRead.Reject("请将脸放入人像框")
        val lowerLip = points(face, FaceContour.LOWER_LIP_BOTTOM)?.map(::norm)
            ?: return FaceRead.Reject("请将脸放入人像框")
        val leftBrow = points(face, FaceContour.LEFT_EYEBROW_BOTTOM)?.map(::norm)
            ?: points(face, FaceContour.LEFT_EYEBROW_TOP)?.map(::norm)
        val rightBrow = points(face, FaceContour.RIGHT_EYEBROW_BOTTOM)?.map(::norm)
            ?: points(face, FaceContour.RIGHT_EYEBROW_TOP)?.map(::norm)
        val leftCheek = points(face, FaceContour.LEFT_CHEEK)?.map(::norm)
        val rightCheek = points(face, FaceContour.RIGHT_CHEEK)?.map(::norm)

        val normLeftEye = leftEye.map(::norm)
        val normRightEye = rightEye.map(::norm)
        val ratios = ratioVector(
            leftEye = normLeftEye,
            rightEye = normRightEye,
            leftBrow = leftBrow,
            rightBrow = rightBrow,
            noseBottom = noseBottom,
            upperLip = upperLip,
            lowerLip = lowerLip,
            leftCheek = leftCheek,
            rightCheek = rightCheek,
            faceOval = faceOval
        ) ?: return FaceRead.Reject("请将脸放入人像框")

        val contours = FloatArray(contourSpec.sumOf { it.second * 2 })
        var offset = 0
        for ((type, count) in contourSpec) {
            val raw = points(face, type)
            val normalized = when {
                raw != null -> raw.map(::norm)
                type == FaceContour.LEFT_CHEEK && leftCheek != null -> leftCheek
                type == FaceContour.RIGHT_CHEEK && rightCheek != null -> rightCheek
                type == FaceContour.NOSE_BRIDGE -> noseBottom
                type == FaceContour.LEFT_EYEBROW_TOP && leftBrow != null -> leftBrow
                type == FaceContour.RIGHT_EYEBROW_TOP && rightBrow != null -> rightBrow
                else -> return FaceRead.Reject("请将脸放入人像框")
            }
            for (p in resample(normalized, count)) {
                contours[offset++] = p.x
                contours[offset++] = p.y
            }
        }
        return FaceRead.Ok(FaceSample(ratios, contours))
    }

    fun l2(a: FloatArray, b: FloatArray): Float {
        var sum = 0.0
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i] - b[i]).toDouble()
            sum += d * d
        }
        return sqrt(sum).toFloat()
    }

    fun mean(vectors: List<FloatArray>): FloatArray {
        val dim = vectors.first().size
        val out = FloatArray(dim)
        for (vector in vectors) {
            for (i in 0 until dim) out[i] += vector[i]
        }
        val n = vectors.size.toFloat()
        for (i in out.indices) out[i] /= n
        return out
    }

    private fun ratioVector(
        leftEye: List<PointF>,
        rightEye: List<PointF>,
        leftBrow: List<PointF>?,
        rightBrow: List<PointF>?,
        noseBottom: List<PointF>,
        upperLip: List<PointF>,
        lowerLip: List<PointF>,
        leftCheek: List<PointF>?,
        rightCheek: List<PointF>?,
        faceOval: List<PointF>?
    ): FloatArray? {
        val faceWidth = when {
            leftCheek != null && rightCheek != null ->
                kotlin.math.abs(centroid(rightCheek).x - centroid(leftCheek).x)
            faceOval != null -> spanX(faceOval)
            else -> return null
        }
        if (faceWidth < 0.4f) return null
        val faceHeight = faceOval?.let { spanY(it) } ?: 0f
        val jawWidth = faceOval?.let { lowerWidth(it) } ?: faceWidth * 0.75f
        val nose = centroid(noseBottom)
        val mouth = centroid(upperLip)
        val lipGap = kotlin.math.abs(centroid(lowerLip).y - mouth.y)
        val browLeft = leftBrow?.let { kotlin.math.abs(centroid(it).y - centroid(leftEye).y) } ?: 0.35f
        val browRight = rightBrow?.let { kotlin.math.abs(centroid(it).y - centroid(rightEye).y) } ?: 0.35f
        return floatArrayOf(
            faceWidth,
            if (faceHeight > 0.2f) faceHeight else faceWidth * 1.35f,
            jawWidth,
            spanX(noseBottom),
            kotlin.math.abs(nose.y),
            spanX(upperLip),
            lipGap,
            kotlin.math.abs(mouth.y),
            spanX(leftEye),
            spanX(rightEye),
            browLeft,
            browRight,
            nose.x,
            mouth.x,
            jawWidth / faceWidth,
            kotlin.math.abs(mouth.y - nose.y)
        )
    }

    private fun points(face: Face, type: Int): List<PointF>? =
        face.getContour(type)?.points?.takeIf { it.size >= 2 }

    private fun centroid(points: List<PointF>): PointF {
        var x = 0f
        var y = 0f
        for (p in points) {
            x += p.x
            y += p.y
        }
        val n = points.size.toFloat()
        return PointF(x / n, y / n)
    }

    private fun spanX(points: List<PointF>): Float =
        points.maxOf { it.x } - points.minOf { it.x }

    private fun spanY(points: List<PointF>): Float =
        points.maxOf { it.y } - points.minOf { it.y }

    private fun lowerWidth(faceOval: List<PointF>): Float {
        val minY = faceOval.minOf { it.y }
        val maxY = faceOval.maxOf { it.y }
        val cut = minY + (maxY - minY) * 0.72f
        val lower = faceOval.filter { it.y >= cut }
        return if (lower.size >= 2) spanX(lower) else spanX(faceOval) * 0.75f
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx * dx + dy * dy).toFloat()
    }

    private fun resample(points: List<PointF>, count: Int): List<PointF> {
        if (count <= 1 || points.isEmpty()) return List(count.coerceAtLeast(1)) { points.firstOrNull() ?: PointF() }
        if (points.size == 1) return List(count) { points[0] }
        val lengths = FloatArray(points.size)
        var total = 0f
        for (i in 1 until points.size) {
            total += distance(points[i - 1], points[i])
            lengths[i] = total
        }
        if (total < 1e-4f) return List(count) { points[0] }
        return List(count) { index ->
            val target = total * index / (count - 1)
            var idx = lengths.indexOfFirst { it >= target }
            if (idx < 0) idx = points.lastIndex
            if (idx == 0) return@List points[0]
            val prev = lengths[idx - 1]
            val seg = (lengths[idx] - prev).coerceAtLeast(1e-4f)
            val t = ((target - prev) / seg).coerceIn(0f, 1f)
            val a = points[idx - 1]
            val b = points[idx]
            PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
    }
}
