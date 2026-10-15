package com.pulse.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max

/** A simplified circuit outline from OpenF1 x/y location samples for one recorded lap.
 * The glowing marker is approximate lap progress, NOT live GPS / precise car position.
 */
class TrackMapView(context: Context) : View(context) {
    private var samples = listOf<TrackPoint>()
    private var progress=0f
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.FILL }
    fun setTrace(points: List<TrackPoint>, lapFraction: Float) {
        samples=points
        progress=lapFraction.coerceIn(0f,1f)
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)
        if (samples.size < 15) {
            fill.color=Color.rgb(108,113,131); fill.textSize=resources.displayMetrics.scaledDensity*12f
            canvas.drawText("Track trace unavailable",22f,height/2f,fill)
            return
        }
        val minX=samples.minOf { it.x };val maxX=samples.maxOf { it.x }
        val minY=samples.minOf { it.y };val maxY=samples.maxOf { it.y }
        val w=max(1f,maxX-minX);val h=max(1f,maxY-minY)
        val scale=( (width-54f)/w ).coerceAtMost((height-54f)/h)
        val ox=(width-w*scale)/2f;val oy=(height-h*scale)/2f
        fun at(t: TrackPoint): Pair<Float,Float> = Pair(ox+(t.x-minX)*scale,oy+(t.y-minY)*scale)
        val path=Path()
        samples.forEachIndexed { index, point -> val (x,y)=at(point); if(index==0) path.moveTo(x,y) else path.lineTo(x,y) }
        stroke.color=Color.rgb(55,59,75);stroke.strokeWidth=18f*resources.displayMetrics.density/3f
        canvas.drawPath(path,stroke)
        stroke.color=Color.rgb(164,148,255);stroke.strokeWidth=5f*resources.displayMetrics.density/3f
        canvas.drawPath(path,stroke)
        val idx=((samples.size-1)*progress).toInt().coerceIn(0,samples.size-1)
        val (px,py)=at(samples[idx])
        fill.color=Color.argb(90,164,148,255)
        canvas.drawCircle(px,py,11f*resources.displayMetrics.density/2f,fill)
        fill.color=Color.WHITE
        canvas.drawCircle(px,py,4f*resources.displayMetrics.density/2f,fill)
    }
}
