
class GraphsCanvas(val image: java.awt.image.BufferedImage, val g: java.awt.Graphics2D, val unit: Double)
object GraphsHost {
    val objects = java.util.ArrayList<Any>()
    fun keep(o: Any): Int { objects.add(o); return objects.size - 1 }
    fun sb(h: Int): StringBuilder = objects[h] as StringBuilder
    fun gr(h: Int): y.J.gA = objects[h] as y.J.gA
    fun mo(h: Int): y.B.j = objects[h] as y.B.j
    fun cv(h: Int): GraphsCanvas = objects[h] as GraphsCanvas
    fun open(text: String): y.J.gA {
        val graph = y.J.gA()
        Class.forName("y.J.A.A").getConstructor(y.A.E::class.java).newInstance(graph)
        y.H.Q().ā(graph, text.byteInputStream(Charsets.UTF_8))
        return graph
    }
    fun module(name: String): y.B.j = Class.forName(name).getDeclaredConstructor().newInstance() as y.B.j
    fun set(module: y.B.j, key: String, value: String) {
        val handler = module.ă()
        val current = handler.đ(key)
        val typed: Any = when (current) {
            is Boolean -> value.toBoolean()
            is Int -> value.toInt()
            is Double -> value.toDouble()
            else -> value.intern()
        }
        handler.ă(key, typed)
    }
    fun run(module: y.B.j, graph: y.J.gA): Int { module.ă(graph); return module.Ĉ().toInt() }
    fun writeGraphml(graph: y.J.gA, path: String) { java.io.FileOutputStream(path).use { y.H.Q().ā(graph, it) } }
    fun writeSvg(graph: y.J.gA, path: String) { java.io.BufferedOutputStream(java.io.FileOutputStream(path)).use { B.A.D.C().ā(graph, it) } }
    fun writePng(graph: y.J.gA, path: String, maxPixels: Long) {
        val handler = y.H.A(javax.imageio.ImageIO.getImageWritersByFormatName("png").next())
        handler.ă(20.0)
        val bounds = graph.ĥ()
        val view = handler.ć(graph)
        val w = bounds.width + 40.0
        val h = bounds.height + 40.0
        val scale = Math.min(1.0, Math.sqrt(maxPixels / (w * h)))
        val size = java.awt.Dimension(Math.ceil(w * scale).toInt(), Math.ceil(h * scale).toInt())
        view.setSize(size)
        view.setPreferredSize(size)
        view.Ć(bounds.x - 20.0, bounds.y - 20.0, w, h)
        java.io.BufferedOutputStream(java.io.FileOutputStream(path)).use { handler.ā(view, it) }
        graph.ă(view as y.J.MB)
    }
    fun canvas(rawPng: String, header: Int): GraphsCanvas {
        val body = javax.imageio.ImageIO.read(java.io.File(rawPng))
        val unit = Math.max(1.0, body.width / 4000.0)
        val top = (header * unit).toInt()
        val image = java.awt.image.BufferedImage(body.width, body.height + top, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, image.width, image.height)
        g.drawImage(body, 0, top, null)
        return GraphsCanvas(image, g, unit)
    }
    fun px(c: GraphsCanvas, v: Int): Int = (v * c.unit).toInt()
    fun rect(c: GraphsCanvas, x: Int, y: Int, w: Int, h: Int, color: String) {
        c.g.color = java.awt.Color.decode(color)
        c.g.fillRect(px(c, x), px(c, y), if (w < 0) c.image.width else px(c, w), px(c, h))
    }
    fun text(c: GraphsCanvas, text: String, x: Int, y: Int, size: Int, bold: Boolean, color: String): Int {
        c.g.color = java.awt.Color.decode(color)
        c.g.font = java.awt.Font("SansSerif", if (bold) java.awt.Font.BOLD else java.awt.Font.PLAIN, px(c, size))
        c.g.drawString(text, px(c, x), px(c, y))
        return (c.g.fontMetrics.stringWidth(text) / c.unit).toInt()
    }
    fun centered(c: GraphsCanvas, text: String, x: Int, y: Int, w: Int, h: Int, size: Int) {
        c.g.color = java.awt.Color.BLACK
        c.g.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, px(c, size))
        val m = c.g.fontMetrics
        c.g.drawString(text, px(c, x) + (px(c, w) - m.stringWidth(text)) / 2, px(c, y) + (px(c, h) + m.ascent - m.descent) / 2)
    }
    fun stroke(c: GraphsCanvas, tenths: Int, pattern: String): java.awt.BasicStroke {
        val w = (tenths / 10.0 * c.unit).toFloat()
        val u = c.unit.toFloat()
        return when (pattern) {
            "dashed" -> java.awt.BasicStroke(w, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10f, floatArrayOf(8f * u, 5f * u), 0f)
            "dotted" -> java.awt.BasicStroke(w, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10f, floatArrayOf(2f * u, 4f * u), 0f)
            "dashed_dotted" -> java.awt.BasicStroke(w, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f * u, 4f * u, 2f * u, 4f * u), 0f)
            else -> java.awt.BasicStroke(w)
        }
    }
    fun shape(c: GraphsCanvas, kind: String, x0: Int, y0: Int, w0: Int, h0: Int, fill: String, border: String, tenths: Int, pattern: String) {
        val x = px(c, x0); val y = px(c, y0); val w = px(c, w0); val h = px(c, h0)
        val s: java.awt.Shape = when (kind) {
            "hexagon", "octagon" -> java.awt.Polygon(intArrayOf(x, x + h / 2, x + w - h / 2, x + w, x + w - h / 2, x + h / 2), intArrayOf(y + h / 2, y, y, y + h / 2, y + h, y + h), 6)
            "roundrectangle" -> java.awt.geom.RoundRectangle2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble(), h / 2.0, h / 2.0)
            "parallelogram" -> java.awt.Polygon(intArrayOf(x + h / 2, x + w, x + w - h / 2, x), intArrayOf(y, y, y + h, y + h), 4)
            "ellipse" -> java.awt.geom.Ellipse2D.Double(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble())
            else -> java.awt.Rectangle(x, y, w, h)
        }
        c.g.color = java.awt.Color.decode(fill)
        c.g.fill(s)
        c.g.color = java.awt.Color.decode(border)
        c.g.stroke = stroke(c, tenths, pattern)
        c.g.draw(s)
    }
    fun line(c: GraphsCanvas, x1: Int, y1: Int, x2: Int, y2: Int, color: String, tenths: Int, pattern: String) {
        c.g.color = java.awt.Color.decode(color)
        c.g.stroke = stroke(c, tenths, pattern)
        c.g.drawLine(px(c, x1), px(c, y1), px(c, x2), px(c, y2))
    }
    fun save(c: GraphsCanvas, path: String) {
        c.g.dispose()
        javax.imageio.ImageIO.write(c.image, "png", java.io.File(path))
    }
}

class Mut0(var l0: Int, var l1: UH5)
sealed interface US1 { val tag: Int }
object US1_Epic : US1 { override val tag: Int = 0 }
object US1_Feature : US1 { override val tag: Int = 1 }
object US1_Story : US1 { override val tag: Int = 2 }
object US1_Chore : US1 { override val tag: Int = 3 }
sealed interface US2 { val tag: Int }
object US2_P0 : US2 { override val tag: Int = 0 }
object US2_P1 : US2 { override val tag: Int = 1 }
object US2_P2 : US2 { override val tag: Int = 2 }
object US2_Later : US2 { override val tag: Int = 3 }
sealed interface US3 { val tag: Int }
object US3_Planned : US3 { override val tag: Int = 0 }
object US3_Active : US3 { override val tag: Int = 1 }
object US3_Blocked : US3 { override val tag: Int = 2 }
object US3_Paused : US3 { override val tag: Int = 3 }
object US3_Done : US3 { override val tag: Int = 4 }
sealed interface US4 { val tag: Int }
object US4_Main : US4 { override val tag: Int = 0 }
object US4_Kit : US4 { override val tag: Int = 1 }
object US4_Xlane : US4 { override val tag: Int = 2 }
object US4_Fiscal : US4 { override val tag: Int = 3 }
object US4_Compiler : US4 { override val tag: Int = 4 }
object US4_Tibia : US4 { override val tag: Int = 5 }
object US4_Resilience : US4 { override val tag: Int = 6 }
object US4_Cells : US4 { override val tag: Int = 7 }
object US4_Ci : US4 { override val tag: Int = 8 }
object US4_Other : US4 { override val tag: Int = 9 }
sealed interface UH5 { val tag: Int }
object UH5_Nil : UH5 { override val tag: Int = 0 }
data class UH5_Cons(val f0: String, val f1: UH5) : UH5 { override val tag: Int = 1 }
sealed interface UH0 { val tag: Int }
object UH0_Nil : UH0 { override val tag: Int = 0 }
data class UH0_Cons(val f0: String, val f1: String, val f2: US1, val f3: US2, val f4: US3, val f5: US4, val f6: UH5, val f7: UH0) : UH0 { override val tag: Int = 1 }
sealed interface US6 { val tag: Int }
data class US6_Some(val f0: String, val f1: String, val f2: US1, val f3: US2, val f4: US3, val f5: US4, val f6: UH5) : US6 { override val tag: Int = 0 }
object US6_None : US6 { override val tag: Int = 1 }
sealed interface US7 { val tag: Int }
data class US7_Some(val f0: String) : US7 { override val tag: Int = 0 }
object US7_None : US7 { override val tag: Int = 1 }
sealed interface US9 { val tag: Int }
object US9_TopToBottom : US9 { override val tag: Int = 0 }
object US9_LeftToRight : US9 { override val tag: Int = 1 }
sealed interface US8 { val tag: Int }
data class US8_Hierarchic(val f0: US9) : US8 { override val tag: Int = 0 }
data class US8_CompactOrthogonal(val f0: Int, val f1: Int) : US8 { override val tag: Int = 1 }
sealed interface US12 { val tag: Int }
object US12_Rectangle : US12 { override val tag: Int = 0 }
object US12_RoundRectangle : US12 { override val tag: Int = 1 }
object US12_Hexagon : US12 { override val tag: Int = 2 }
object US12_Octagon : US12 { override val tag: Int = 3 }
object US12_Parallelogram : US12 { override val tag: Int = 4 }
object US12_Ellipse : US12 { override val tag: Int = 5 }
sealed interface US13 { val tag: Int }
object US13_Solid : US13 { override val tag: Int = 0 }
object US13_Dashed : US13 { override val tag: Int = 1 }
object US13_Dotted : US13 { override val tag: Int = 2 }
object US13_DashDotted : US13 { override val tag: Int = 3 }
sealed interface US14 { val tag: Int }
object US14_Standard : US14 { override val tag: Int = 0 }
object US14_WhiteDelta : US14 { override val tag: Int = 1 }
object US14_WhiteDiamond : US14 { override val tag: Int = 2 }
object US14_Bare : US14 { override val tag: Int = 3 }
sealed interface US11 { val tag: Int }
data class US11_Swatch(val f0: String, val f1: US12, val f2: String, val f3: String, val f4: US13, val f5: Int, val f6: String) : US11 { override val tag: Int = 0 }
data class US11_Stroke(val f0: String, val f1: String, val f2: US13, val f3: Int, val f4: US14) : US11 { override val tag: Int = 1 }
sealed interface UH10 { val tag: Int }
object UH10_Nil : UH10 { override val tag: Int = 0 }
data class UH10_Cons(val f0: US11, val f1: UH10) : UH10 { override val tag: Int = 1 }
sealed interface UH16 { val tag: Int }
data class UH16_Leaf(val f0: String, val f1: String, val f2: UH5, val f3: US12, val f4: String, val f5: String, val f6: US13, val f7: Int, val f8: String, val f9: Int, val f10: Int, val f11: Int, val f12: Boolean) : UH16 { override val tag: Int = 0 }
data class UH16_Nest(val f0: String, val f1: String, val f2: String, val f3: String, val f4: Boolean, val f5: Int, val f6: UH15) : UH16 { override val tag: Int = 1 }
sealed interface UH15 { val tag: Int }
object UH15_Nil : UH15 { override val tag: Int = 0 }
data class UH15_Cons(val f0: UH16, val f1: UH15) : UH15 { override val tag: Int = 1 }
sealed interface UH17 { val tag: Int }
object UH17_Nil : UH17 { override val tag: Int = 0 }
data class UH17_Cons(val f0: String, val f1: String, val f2: String, val f3: String, val f4: US13, val f5: Int, val f6: US14, val f7: UH17) : UH17 { override val tag: Int = 1 }
sealed interface US18 { val tag: Int }
data class US18_Modeled(val f0: String, val f1: String, val f2: UH10, val f3: UH15, val f4: UH17) : US18 { override val tag: Int = 0 }
sealed interface US20 { val tag: Int }
data class US20_Handle(val f0: Int) : US20 { override val tag: Int = 0 }
sealed interface US19 { val tag: Int }
data class US19_LaidOut(val f0: String, val f1: String, val f2: UH10, val f3: UH15, val f4: UH17, val f5: US20) : US19 { override val tag: Int = 1 }
sealed interface UH21 { val tag: Int }
object UH21_Nil : UH21 { override val tag: Int = 0 }
data class UH21_Cons(val f0: String, val f1: String, val f2: UH21) : UH21 { override val tag: Int = 1 }
sealed interface UH22 { val tag: Int }
object UH22_Nil : UH22 { override val tag: Int = 0 }
data class UH22_Cons(val f0: US12, val f1: String, val f2: String, val f3: US13, val f4: Int, val f5: String, val f6: UH22) : UH22 { override val tag: Int = 1 }
sealed interface US25 { val tag: Int }
object US25_NHashcons : US25 { override val tag: Int = 0 }
object US25_NConsed : US25 { override val tag: Int = 1 }
object US25_NStriped : US25 { override val tag: Int = 2 }
object US25_NMemoize : US25 { override val tag: Int = 3 }
object US25_NHopacext : US25 { override val tag: Int = 4 }
object US25_NPvec : US25 { override val tag: Int = 5 }
object US25_NParsec : US25 { override val tag: Int = 6 }
object US25_NIdadapter : US25 { override val tag: Int = 7 }
object US25_NHopaclib : US25 { override val tag: Int = 8 }
sealed interface US26 { val tag: Int }
object US26_NStartup : US26 { override val tag: Int = 0 }
object US26_NTokenize : US26 { override val tag: Int = 1 }
object US26_NBlocksplit : US26 { override val tag: Int = 2 }
object US26_NBlockparse : US26 { override val tag: Int = 3 }
object US26_NBundle : US26 { override val tag: Int = 4 }
object US26_NFormatter : US26 { override val tag: Int = 5 }
sealed interface US27 { val tag: Int }
object US27_NSpiproj : US27 { override val tag: Int = 0 }
object US27_NWdiff : US27 { override val tag: Int = 1 }
object US27_NWdiffprep : US27 { override val tag: Int = 2 }
object US27_NGraph : US27 { override val tag: Int = 3 }
object US27_NLoader : US27 { override val tag: Int = 4 }
sealed interface US28 { val tag: Int }
object US28_NInfer : US28 { override val tag: Int = 0 }
object US28_NInferadapter : US28 { override val tag: Int = 1 }
object US28_NInlinedepth : US28 { override val tag: Int = 2 }
object US28_NTypeerr : US28 { override val tag: Int = 3 }
sealed interface US29 { val tag: Int }
object US29_NPrepass : US29 { override val tag: Int = 0 }
object US29_NPrepassadapter : US29 { override val tag: Int = 1 }
object US29_NPrepenv : US29 { override val tag: Int = 2 }
sealed interface US30 { val tag: Int }
object US30_NPeval : US30 { override val tag: Int = 0 }
object US30_NTopenv : US30 { override val tag: Int = 1 }
object US30_NLangenv : US30 { override val tag: Int = 2 }
object US30_NResult : US30 { override val tag: Int = 3 }
object US30_NTermscope : US30 { override val tag: Int = 4 }
object US30_NCse : US30 { override val tag: Int = 5 }
sealed interface US31 { val tag: Int }
object US31_NJpm : US31 { override val tag: Int = 0 }
object US31_NJpc : US31 { override val tag: Int = 1 }
object US31_NJpt : US31 { override val tag: Int = 2 }
object US31_NBsv : US31 { override val tag: Int = 3 }
object US31_NGlobals : US31 { override val tag: Int = 4 }
sealed interface US32 { val tag: Int }
object US32_NClosure : US32 { override val tag: Int = 0 }
object US32_NTjp : US32 { override val tag: Int = 1 }
object US32_NMjp : US32 { override val tag: Int = 2 }
object US32_NBswitch : US32 { override val tag: Int = 3 }
object US32_NUninh : US32 { override val tag: Int = 4 }
object US32_NPrintst : US32 { override val tag: Int = 5 }
object US32_NMainroot : US32 { override val tag: Int = 6 }
sealed interface US33 { val tag: Int }
object US33_NRunaway : US33 { override val tag: Int = 0 }
object US33_NDynif : US33 { override val tag: Int = 1 }
object US33_NUnionnest : US33 { override val tag: Int = 2 }
object US33_NGindex : US33 { override val tag: Int = 3 }
object US33_NRustgap : US33 { override val tag: Int = 4 }
sealed interface US34 { val tag: Int }
object US34_NAdapter : US34 { override val tag: Int = 0 }
object US34_NSSession : US34 { override val tag: Int = 1 }
object US34_NSClaimty : US34 { override val tag: Int = 2 }
object US34_NSStart : US34 { override val tag: Int = 3 }
object US34_NSRestart : US34 { override val tag: Int = 4 }
object US34_NSRoot : US34 { override val tag: Int = 5 }
object US34_NSDefer : US34 { override val tag: Int = 6 }
object US34_NSClaim : US34 { override val tag: Int = 7 }
object US34_NSAnnounce : US34 { override val tag: Int = 8 }
object US34_NSFill : US34 { override val tag: Int = 9 }
object US34_NSFamily : US34 { override val tag: Int = 10 }
object US34_NSKeyhit : US34 { override val tag: Int = 11 }
object US34_NSKeybody : US34 { override val tag: Int = 12 }
object US34_NSPrint : US34 { override val tag: Int = 13 }
object US34_NSNesting : US34 { override val tag: Int = 14 }
object US34_NSValidated : US34 { override val tag: Int = 15 }
object US34_NSRead : US34 { override val tag: Int = 16 }
object US34_NSActive : US34 { override val tag: Int = 17 }
sealed interface US35 { val tag: Int }
object US35_NSfSession : US35 { override val tag: Int = 0 }
object US35_NSfStart : US35 { override val tag: Int = 1 }
object US35_NSfRestart : US35 { override val tag: Int = 2 }
object US35_NSfRoot : US35 { override val tag: Int = 3 }
object US35_NSfDefer : US35 { override val tag: Int = 4 }
object US35_NSfClaim : US35 { override val tag: Int = 5 }
object US35_NSfAnnounce : US35 { override val tag: Int = 6 }
object US35_NSfFill : US35 { override val tag: Int = 7 }
object US35_NSfFamily : US35 { override val tag: Int = 8 }
object US35_NSfKeyhit : US35 { override val tag: Int = 9 }
object US35_NSfKeybody : US35 { override val tag: Int = 10 }
object US35_NSfPrint : US35 { override val tag: Int = 11 }
object US35_NSfNesting : US35 { override val tag: Int = 12 }
object US35_NSfValidated : US35 { override val tag: Int = 13 }
object US35_NSfRead : US35 { override val tag: Int = 14 }
object US35_NSfActive : US35 { override val tag: Int = 15 }
sealed interface US36 { val tag: Int }
object US36_NHsPolicy : US36 { override val tag: Int = 0 }
object US36_NHsSession : US36 { override val tag: Int = 1 }
object US36_NHsStart : US36 { override val tag: Int = 2 }
object US36_NHsRestart : US36 { override val tag: Int = 3 }
object US36_NHsRoot : US36 { override val tag: Int = 4 }
object US36_NHsDefer : US36 { override val tag: Int = 5 }
object US36_NHsBody : US36 { override val tag: Int = 6 }
object US36_NHsDrain : US36 { override val tag: Int = 7 }
object US36_NHsKeyhit : US36 { override val tag: Int = 8 }
object US36_NHsKeybody : US36 { override val tag: Int = 9 }
object US36_NHsPrint : US36 { override val tag: Int = 10 }
object US36_NHsFinish : US36 { override val tag: Int = 11 }
object US36_NHsCensus : US36 { override val tag: Int = 12 }
object US36_NHsFallback : US36 { override val tag: Int = 13 }
object US36_NHsMarks : US36 { override val tag: Int = 14 }
sealed interface US37 { val tag: Int }
object US37_NEEngine : US37 { override val tag: Int = 0 }
object US37_NEControl : US37 { override val tag: Int = 1 }
object US37_NEBridge : US37 { override val tag: Int = 2 }
object US37_NEAsk : US37 { override val tag: Int = 3 }
object US37_NESettle : US37 { override val tag: Int = 4 }
object US37_NEAwait : US37 { override val tag: Int = 5 }
object US37_NEAbandon : US37 { override val tag: Int = 6 }
object US37_NEStack : US37 { override val tag: Int = 7 }
object US37_NEThreads : US37 { override val tag: Int = 8 }
object US37_NELoop : US37 { override val tag: Int = 9 }
object US37_NEClaim : US37 { override val tag: Int = 10 }
object US37_NEValidated : US37 { override val tag: Int = 11 }
object US37_NERead : US37 { override val tag: Int = 12 }
object US37_NEFill : US37 { override val tag: Int = 13 }
object US37_NEAnnounce : US37 { override val tag: Int = 14 }
object US37_NEFamily : US37 { override val tag: Int = 15 }
object US37_NESpawn : US37 { override val tag: Int = 16 }
object US37_NEDrain : US37 { override val tag: Int = 17 }
object US37_NECheck : US37 { override val tag: Int = 18 }
object US37_NEClose : US37 { override val tag: Int = 19 }
object US37_NEConflict : US37 { override val tag: Int = 20 }
sealed interface US38 { val tag: Int }
object US38_NTTable : US38 { override val tag: Int = 0 }
object US38_NTDecide : US38 { override val tag: Int = 1 }
object US38_NTEntry : US38 { override val tag: Int = 2 }
object US38_NTState : US38 { override val tag: Int = 3 }
object US38_NTOps : US38 { override val tag: Int = 4 }
object US38_NTReplies : US38 { override val tag: Int = 5 }
object US38_NTPublished : US38 { override val tag: Int = 6 }
object US38_NTGuard : US38 { override val tag: Int = 7 }
sealed interface US39 { val tag: Int }
object US39_NGGraph : US39 { override val tag: Int = 0 }
object US39_NGReq : US39 { override val tag: Int = 1 }
object US39_NGCh : US39 { override val tag: Int = 2 }
sealed interface US40 { val tag: Int }
object US40_NWList : US40 { override val tag: Int = 0 }
object US40_NWReq : US40 { override val tag: Int = 1 }
object US40_NWSettle : US40 { override val tag: Int = 2 }
object US40_NWCh : US40 { override val tag: Int = 3 }
sealed interface US41 { val tag: Int }
object US41_NCCtx : US41 { override val tag: Int = 0 }
object US41_NCTl : US41 { override val tag: Int = 1 }
sealed interface US42 { val tag: Int }
object US42_NDOrder : US42 { override val tag: Int = 0 }
object US42_NDErr : US42 { override val tag: Int = 1 }
object US42_NDPrint : US42 { override val tag: Int = 2 }
object US42_NDRec : US42 { override val tag: Int = 3 }
object US42_NDConf : US42 { override val tag: Int = 4 }
object US42_NDNest : US42 { override val tag: Int = 5 }
object US42_NDGlobals : US42 { override val tag: Int = 6 }
object US42_NDSame : US42 { override val tag: Int = 7 }
sealed interface US43 { val tag: Int }
object US43_NCgAdapter : US43 { override val tag: Int = 0 }
object US43_NCgUtils : US43 { override val tag: Int = 1 }
object US43_NCgDead : US43 { override val tag: Int = 2 }
object US43_NCgUnused : US43 { override val tag: Int = 3 }
object US43_NCgRefc : US43 { override val tag: Int = 4 }
sealed interface US44 { val tag: Int }
object US44_NBFsharp : US44 { override val tag: Int = 0 }
object US44_NBRust : US44 { override val tag: Int = 1 }
object US44_NBDelphi : US44 { override val tag: Int = 2 }
object US44_NBZig : US44 { override val tag: Int = 3 }
object US44_NBLean : US44 { override val tag: Int = 4 }
object US44_NBBend : US44 { override val tag: Int = 5 }
object US44_NBGleam : US44 { override val tag: Int = 6 }
object US44_NBLua : US44 { override val tag: Int = 7 }
object US44_NBWasm : US44 { override val tag: Int = 8 }
object US44_NBWgsl : US44 { override val tag: Int = 9 }
object US44_NBCalyx : US44 { override val tag: Int = 10 }
object US44_NBKotlin : US44 { override val tag: Int = 11 }
object US44_NBSwift : US44 { override val tag: Int = 12 }
object US44_NBQasm : US44 { override val tag: Int = 13 }
object US44_NBEbpf : US44 { override val tag: Int = 14 }
object US44_NBTs : US44 { override val tag: Int = 15 }
object US44_NBC : US44 { override val tag: Int = 16 }
object US44_NBCpp : US44 { override val tag: Int = 17 }
object US44_NBPy : US44 { override val tag: Int = 18 }
sealed interface US45 { val tag: Int }
object US45_NSvMain : US45 { override val tag: Int = 0 }
object US45_NSvHub : US45 { override val tag: Int = 1 }
object US45_NSvNew : US45 { override val tag: Int = 2 }
object US45_NSvSuper : US45 { override val tag: Int = 3 }
object US45_NSvBuild : US45 { override val tag: Int = 4 }
object US45_NSvMany : US45 { override val tag: Int = 5 }
object US45_NSvOut : US45 { override val tag: Int = 6 }
object US45_NSvXcompat : US45 { override val tag: Int = 7 }
sealed interface US46 { val tag: Int }
object US46_NEnvWorkers : US46 { override val tag: Int = 0 }
object US46_NEnvPolicy : US46 { override val tag: Int = 1 }
object US46_NEnvStats : US46 { override val tag: Int = 2 }
object US46_NEnvCensus : US46 { override val tag: Int = 3 }
object US46_NEnvForce : US46 { override val tag: Int = 4 }
object US46_NEnvGap : US46 { override val tag: Int = 5 }
object US46_NEnvIf : US46 { override val tag: Int = 6 }
object US46_NEnvUnion : US46 { override val tag: Int = 7 }
object US46_NEnvDefine : US46 { override val tag: Int = 8 }
sealed interface US24 { val tag: Int }
data class US24_PInfra(val f0: US25) : US24 { override val tag: Int = 0 }
data class US24_PFront(val f0: US26) : US24 { override val tag: Int = 1 }
data class US24_PProj(val f0: US27) : US24 { override val tag: Int = 2 }
data class US24_PTyping(val f0: US28) : US24 { override val tag: Int = 3 }
data class US24_PPrep(val f0: US29) : US24 { override val tag: Int = 4 }
data class US24_PPe(val f0: US30) : US24 { override val tag: Int = 5 }
data class US24_PPeTables(val f0: US31) : US24 { override val tag: Int = 6 }
data class US24_PPeOps(val f0: US32) : US24 { override val tag: Int = 7 }
data class US24_PPeGuards(val f0: US33) : US24 { override val tag: Int = 8 }
data class US24_PSeams(val f0: US34) : US24 { override val tag: Int = 9 }
data class US24_PSf(val f0: US35) : US24 { override val tag: Int = 10 }
data class US24_PHsSeams(val f0: US36) : US24 { override val tag: Int = 11 }
data class US24_PHsEngine(val f0: US37) : US24 { override val tag: Int = 12 }
data class US24_PHsTable(val f0: US38) : US24 { override val tag: Int = 13 }
data class US24_PHsGraph(val f0: US39) : US24 { override val tag: Int = 14 }
data class US24_PHsWork(val f0: US40) : US24 { override val tag: Int = 15 }
data class US24_PHsCtx(val f0: US41) : US24 { override val tag: Int = 16 }
data class US24_PDiverge(val f0: US42) : US24 { override val tag: Int = 17 }
data class US24_PCodegen(val f0: US43) : US24 { override val tag: Int = 18 }
data class US24_PBackends(val f0: US44) : US24 { override val tag: Int = 19 }
data class US24_PServer(val f0: US45) : US24 { override val tag: Int = 20 }
data class US24_PEnv(val f0: US46) : US24 { override val tag: Int = 21 }
sealed interface US47 { val tag: Int }
object US47_Flow : US47 { override val tag: Int = 0 }
object US47_Call : US47 { override val tag: Int = 1 }
object US47_DataFlow : US47 { override val tag: Int = 2 }
object US47_SeamLink : US47 { override val tag: Int = 3 }
object US47_ToSingleFlight : US47 { override val tag: Int = 4 }
object US47_ToHopacsota : US47 { override val tag: Int = 5 }
object US47_Engine : US47 { override val tag: Int = 6 }
object US47_Reuse : US47 { override val tag: Int = 7 }
object US47_NoteLink : US47 { override val tag: Int = 8 }
sealed interface UH23 { val tag: Int }
object UH23_Nil : UH23 { override val tag: Int = 0 }
data class UH23_Cons(val f0: US24, val f1: US24, val f2: US47, val f3: String, val f4: UH23) : UH23 { override val tag: Int = 1 }
sealed interface US48 { val tag: Int }
object US48_RWhole : US48 { override val tag: Int = 0 }
object US48_RInfra : US48 { override val tag: Int = 1 }
object US48_RFront : US48 { override val tag: Int = 2 }
object US48_RProj : US48 { override val tag: Int = 3 }
object US48_RTyping : US48 { override val tag: Int = 4 }
object US48_RPrep : US48 { override val tag: Int = 5 }
object US48_RPe : US48 { override val tag: Int = 6 }
object US48_RPeTables : US48 { override val tag: Int = 7 }
object US48_RPeOps : US48 { override val tag: Int = 8 }
object US48_RPeGuards : US48 { override val tag: Int = 9 }
object US48_RSeams : US48 { override val tag: Int = 10 }
object US48_RSf : US48 { override val tag: Int = 11 }
object US48_RHs : US48 { override val tag: Int = 12 }
object US48_RHsSeams : US48 { override val tag: Int = 13 }
object US48_RHsEngine : US48 { override val tag: Int = 14 }
object US48_RHsTable : US48 { override val tag: Int = 15 }
object US48_RHsGraph : US48 { override val tag: Int = 16 }
object US48_RHsWork : US48 { override val tag: Int = 17 }
object US48_RHsCtx : US48 { override val tag: Int = 18 }
object US48_RDiverge : US48 { override val tag: Int = 19 }
object US48_RCodegen : US48 { override val tag: Int = 20 }
object US48_RBackends : US48 { override val tag: Int = 21 }
object US48_RServer : US48 { override val tag: Int = 22 }
object US48_REnv : US48 { override val tag: Int = 23 }
sealed interface US49 { val tag: Int }
object US49_Entry : US49 { override val tag: Int = 0 }
object US49_Fn : US49 { override val tag: Int = 1 }
object US49_Data : US49 { override val tag: Int = 2 }
object US49_Type : US49 { override val tag: Int = 3 }
object US49_Api : US49 { override val tag: Int = 4 }
object US49_Op : US49 { override val tag: Int = 5 }
object US49_Seam : US49 { override val tag: Int = 6 }
object US49_Note : US49 { override val tag: Int = 7 }
object US49_Cfg : US49 { override val tag: Int = 8 }
object US49_Err : US49 { override val tag: Int = 9 }
object US49_Be : US49 { override val tag: Int = 10 }
object US49_Ext : US49 { override val tag: Int = 11 }
object US49_Class : US49 { override val tag: Int = 12 }
object US49_Impl : US49 { override val tag: Int = 13 }
sealed interface UH50 { val tag: Int }
object UH50_Nil : UH50 { override val tag: Int = 0 }
data class UH50_Cons(val f0: US24, val f1: UH50) : UH50 { override val tag: Int = 1 }
sealed interface US51 { val tag: Int }
object US51_SingleFlight : US51 { override val tag: Int = 0 }
object US51_Hopacsota : US51 { override val tag: Int = 1 }
object US51_Shared : US51 { override val tag: Int = 2 }
sealed interface UH52 { val tag: Int }
object UH52_Nil : UH52 { override val tag: Int = 0 }
data class UH52_Cons(val f0: US48, val f1: UH52) : UH52 { override val tag: Int = 1 }
sealed interface US53 { val tag: Int }
object US53_TopLevel : US53 { override val tag: Int = 0 }
data class US53_Under(val f0: US48) : US53 { override val tag: Int = 1 }
fun spiralUtf8(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)
fun spiralStringLength(value: String): Int = spiralUtf8(value).size
fun spiralStringIndex(value: String, index: Int): Int {
    val bytes = spiralUtf8(value)
    if (!(index >= 0 && index < bytes.size)) {
        System.err.println("string index out of bounds")
        System.exit(1)
        throw RuntimeException()
    }
    return bytes[index].toInt()
}
fun spiralStringSlice(value: String, from: Int, to: Int): String {
    val bytes = spiralUtf8(value)
    val length = bytes.size
    if (from < 0 || from > length || to < from - 1 || to >= length) {
        System.err.println("string slice out of bounds")
        System.exit(3)
        throw RuntimeException()
    }
    if (to < from) return ""
    val start = bytes[from].toInt()
    val follows = to + 1 < length && ((bytes[to + 1].toInt() and 192) == 128)
    if ((start and 192) == 128 || follows) {
        System.err.println("string slice splits a code point")
        System.exit(3)
        throw RuntimeException()
    }
    return bytes.copyOfRange(from, to + 1).toString(Charsets.UTF_8)
}
class SpiralTuple0(val v0: Int, val v1: String)
class SpiralTuple1(val v0: String, val v1: String, val v2: UH10, val v3: UH15, val v4: UH17)
class SpiralTuple2(val v0: String, val v1: String, val v2: UH5, val v3: US12, val v4: String, val v5: String, val v6: US13, val v7: Int, val v8: String, val v9: Int, val v10: Int, val v11: Int, val v12: Boolean)
class SpiralTuple3(val v0: US12, val v1: String, val v2: String, val v3: US13, val v4: Int, val v5: String)
class SpiralTuple4(val v0: String, val v1: String)
class SpiralTuple5(val v0: String, val v1: String, val v2: String, val v3: String, val v4: US13, val v5: Int, val v6: US14)
class SpiralTuple6(val v0: String, val v1: US48, val v2: US49, val v3: String, val v4: String)
class SpiralTuple7(val v0: String, val v1: US13, val v2: Int, val v3: US14)
class SpiralTuple8(val v0: String, val v1: US53, val v2: String, val v3: String)
class SpiralTuple9(val v0: String, val v1: String, val v2: String, val v3: String, val v4: Boolean, val v5: Int)
fun sp_method1(p0: UH0, p1: UH0): UH0 {
    var v0: UH0 = p0
    var v1: UH0 = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH0_Cons).f0
            var v3: String = (v0 as UH0_Cons).f1
            var v4: US1 = (v0 as UH0_Cons).f2
            var v5: US2 = (v0 as UH0_Cons).f3
            var v6: US3 = (v0 as UH0_Cons).f4
            var v7: US4 = (v0 as UH0_Cons).f5
            var v8: UH5 = (v0 as UH0_Cons).f6
            var v9: UH0 = (v0 as UH0_Cons).f7
            var v10: UH0 = UH0_Nil
            v10 = UH0_Cons(v2, v3, v4, v5, v6, v7, v8, v1)
            return sp_method1(v9, v10)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_method3(p0: UH5, p1: UH5): UH5 {
    var v0: UH5 = p0
    var v1: UH5 = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH5_Cons).f0
            var v3: UH5 = (v0 as UH5_Cons).f1
            var v4: UH5 = UH5_Nil
            v4 = UH5_Cons(v2, v1)
            return sp_method3(v3, v4)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_method4(p0: Int, p1: Mut0): Boolean {
    var v0: Int = p0
    var v1: Mut0 = p1
    var v2: Int = 0
    v2 = v1.l0
    var v3: Boolean = false
    v3 = (v2 < v0)
    return v3
}
fun sp_split_list_2(p0: String, p1: String): UH5 {
    var v0: String = p0
    var v1: String = p1
    var v2: Int = 0
    v2 = spiralStringLength(v0)
    var v3: Boolean = false
    v3 = (v2 == 0)
    if (v3) {
        var v4: UH5 = UH5_Nil
        v4 = UH5_Nil
        return UH5_Cons(v1, v4)
    } else {
        var v6: Array<String> = emptyArray<String>()
        v6 = (v1).split(v0).toTypedArray()
        var v7: Int = 0
        v7 = v6.size
        var v8: UH5 = UH5_Nil
        v8 = UH5_Nil
        var v9: Mut0 = Mut0(0, UH5_Nil)
        v9 = Mut0(0, v8)
        while (true) {
            if (!(sp_method4(v7, v9))) break
            var v11: Int = 0
            v11 = v9.l0
            var v12: UH5 = UH5_Nil
            v12 = v9.l1
            var v13: String = ""
            v13 = v6[v11]
            var v14: Int = 0
            v14 = (v11 + 1)
            var v15: UH5 = UH5_Nil
            v15 = UH5_Cons(v13, v12)
            v9.l0 = v14
            v9.l1 = v15
        }
        var v16: UH5 = UH5_Nil
        v16 = v9.l1
        var v17: UH5 = UH5_Nil
        v17 = UH5_Nil
        return sp_method3(v16, v17)
    }
}
fun sp_last_at_8(p0: String, p1: String, p2: Int, p3: Int): Int {
    var v0: String = p0
    var v1: String = p1
    var v2: Int = p2
    var v3: Int = p3
    while (true) {
        var v4: Int = 0
        v4 = (v1).indexOf(v0).let { i -> if (i < 0) -1 else (v1).substring(0, i).toByteArray(Charsets.UTF_8).size }
        var v5: Boolean = false
        v5 = (v4 < 0)
        if (v5) {
            return v3
        } else {
            var v6: Int = 0
            v6 = (v4 + 1)
            var v7: Int = 0
            v7 = spiralStringLength(v1)
            var v8: Int = 0
            v8 = (v7 - 1)
            var v9: String = ""
            v9 = spiralStringSlice(v1, v6, v8)
            var v10: Int = 0
            v10 = (v2 + v4)
            var v11: Int = 0
            v11 = (v10 + 1)
            v1 = v9
            v2 = v11
            v3 = v10
            continue
        }
    }
    return 0
}
fun sp_kind_marker_7(p0: UH5, p1: String, p2: Int): Int {
    var v0: UH5 = p0
    var v1: String = p1
    var v2: Int = p2
    when (v0.tag) {
        1 -> {
            var v3: String = (v0 as UH5_Cons).f0
            var v4: UH5 = (v0 as UH5_Cons).f1
            var v5: Int = 0
            v5 = 0
            var v6: Int = 0
            v6 = -1
            var v7: Int = 0
            v7 = sp_last_at_8(v3, v1, v5, v6)
            var v8: Boolean = false
            v8 = (v7 > v2)
            var v9: Int = 0
            if (v8) {
                v9 = v7
            } else {
                v9 = v2
            }
            return sp_kind_marker_7(v4, v1, v9)
        }
        0 -> {
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_unescaped_9(p0: String): String {
    var v0: String = p0
    var v1: String = ""
    v1 = "\\\""
    var v2: String = ""
    v2 = "\""
    var v3: String = ""
    v3 = (v0).replace(v1, v2)
    var v4: String = ""
    v4 = "\\\\"
    var v5: String = ""
    v5 = "\\"
    var v6: String = ""
    v6 = (v3).replace(v4, v5)
    return v6
}
fun sp_method11(p0: Int, p1: UH5, p2: Int, p3: String): SpiralTuple0 {
    var v0: Int = p0
    var v1: UH5 = p1
    var v2: Int = p2
    var v3: String = p3
    when (v1.tag) {
        1 -> {
            var v4: String = (v1 as UH5_Cons).f0
            var v5: UH5 = (v1 as UH5_Cons).f1
            var v6: Int = 0
            v6 = (v2 + 1)
            var v7: Boolean = false
            v7 = (v2 == v0)
            var v8: String = ""
            if (v7) {
                v8 = v4
            } else {
                v8 = v3
            }
            return sp_method11(v0, v5, v6, v8)
        }
        0 -> {
            return SpiralTuple0(v2, v3)
        }
        else -> throw RuntimeException()
    }
}
fun sp_field_10(p0: UH5, p1: Int, p2: String): String {
    var v0: UH5 = p0
    var v1: Int = p1
    var v2: String = p2
    var v3: Int = 0
    v3 = 0
    var v4: Int = 0
    var v5: String = ""
    val spiral_tuple197 = sp_method11(v1, v0, v3, v2)
    v4 = spiral_tuple197.v0
    v5 = spiral_tuple197.v1
    return v5
}
fun sp_unquoted_12(p0: String): String {
    var v0: String = p0
    var v1: Int = 0
    v1 = spiralStringLength(v0)
    var v2: Boolean = false
    v2 = (v1 >= 2)
    if (v2) {
        var v3: Int = 0
        v3 = (v1 - 2)
        var v4: String = ""
        v4 = spiralStringSlice(v0, 1, v3)
        return v4
    } else {
        return v0
    }
}
fun sp_work_of_13(p0: String): US1 {
    var v0: String = p0
    var v1: String = ""
    v1 = "Epic"
    var v2: Boolean = false
    v2 = (v0 == v1)
    if (v2) {
        return US1_Epic
    } else {
        var v4: String = ""
        v4 = "Feature"
        var v5: Boolean = false
        v5 = (v0 == v4)
        if (v5) {
            return US1_Feature
        } else {
            var v7: String = ""
            v7 = "Story"
            var v8: Boolean = false
            v8 = (v0 == v7)
            if (v8) {
                return US1_Story
            } else {
                return US1_Chore
            }
        }
    }
}
fun sp_priority_of_14(p0: String): US2 {
    var v0: String = p0
    var v1: String = ""
    v1 = "P0"
    var v2: Boolean = false
    v2 = (v0 == v1)
    if (v2) {
        return US2_P0
    } else {
        var v4: String = ""
        v4 = "P1"
        var v5: Boolean = false
        v5 = (v0 == v4)
        if (v5) {
            return US2_P1
        } else {
            var v7: String = ""
            v7 = "P2"
            var v8: Boolean = false
            v8 = (v0 == v7)
            if (v8) {
                return US2_P2
            } else {
                return US2_Later
            }
        }
    }
}
fun sp_status_of_15(p0: String): US3 {
    var v0: String = p0
    var v1: String = ""
    v1 = "Active"
    var v2: Boolean = false
    v2 = (v0 == v1)
    if (v2) {
        return US3_Active
    } else {
        var v4: String = ""
        v4 = "Blocked"
        var v5: Boolean = false
        v5 = (v0 == v4)
        if (v5) {
            return US3_Blocked
        } else {
            var v7: String = ""
            v7 = "Paused"
            var v8: Boolean = false
            v8 = (v0 == v7)
            if (v8) {
                return US3_Paused
            } else {
                var v10: String = ""
                v10 = "Done"
                var v11: Boolean = false
                v11 = (v0 == v10)
                if (v11) {
                    return US3_Done
                } else {
                    return US3_Planned
                }
            }
        }
    }
}
fun sp_first_team_17(p0: UH5): US7 {
    var v0: UH5 = p0
    when (v0.tag) {
        1 -> {
            var v1: String = (v0 as UH5_Cons).f0
            var v2: UH5 = (v0 as UH5_Cons).f1
            var v3: String = ""
            v3 = "team:"
            var v4: Boolean = false
            v4 = (v1).startsWith(v3)
            if (v4) {
                var v5: Int = 0
                v5 = spiralStringLength(v1)
                var v6: Int = 0
                v6 = (v5 - 1)
                var v7: String = ""
                v7 = spiralStringSlice(v1, 5, v6)
                return US7_Some(v7)
            } else {
                return sp_first_team_17(v2)
            }
        }
        0 -> {
            return US7_None
        }
        else -> throw RuntimeException()
    }
}
fun sp_area_of_word_20(p0: String): US4 {
    var v0: String = p0
    var v1: String = ""
    v1 = "main"
    var v2: Boolean = false
    v2 = (v0 == v1)
    if (v2) {
        return US4_Main
    } else {
        var v4: String = ""
        v4 = "kit"
        var v5: Boolean = false
        v5 = (v0 == v4)
        var v8: Boolean = false
        if (v5) {
            v8 = true
        } else {
            var v6: String = ""
            v6 = "eoie"
            var v7: Boolean = false
            v7 = (v0 == v6)
            v8 = v7
        }
        if (v8) {
            return US4_Kit
        } else {
            var v10: String = ""
            v10 = "x"
            var v11: Boolean = false
            v11 = (v0 == v10)
            var v14: Boolean = false
            if (v11) {
                v14 = true
            } else {
                var v12: String = ""
                v12 = "x-dumps"
                var v13: Boolean = false
                v13 = (v0 == v12)
                v14 = v13
            }
            if (v14) {
                return US4_Xlane
            } else {
                var v16: String = ""
                v16 = "fiscal"
                var v17: Boolean = false
                v17 = (v0 == v16)
                var v20: Boolean = false
                if (v17) {
                    v20 = true
                } else {
                    var v18: String = ""
                    v18 = "sefaz"
                    var v19: Boolean = false
                    v19 = (v0 == v18)
                    v20 = v19
                }
                if (v20) {
                    return US4_Fiscal
                } else {
                    var v22: String = ""
                    v22 = "compiler"
                    var v23: Boolean = false
                    v23 = (v0 == v22)
                    var v26: Boolean = false
                    if (v23) {
                        v26 = true
                    } else {
                        var v24: String = ""
                        v24 = "spiral"
                        var v25: Boolean = false
                        v25 = (v0 == v24)
                        v26 = v25
                    }
                    var v29: Boolean = false
                    if (v26) {
                        v29 = true
                    } else {
                        var v27: String = ""
                        v27 = "backend"
                        var v28: Boolean = false
                        v28 = (v0 == v27)
                        v29 = v28
                    }
                    var v32: Boolean = false
                    if (v29) {
                        v32 = true
                    } else {
                        var v30: String = ""
                        v30 = "lib"
                        var v31: Boolean = false
                        v31 = (v0 == v30)
                        v32 = v31
                    }
                    var v35: Boolean = false
                    if (v32) {
                        v35 = true
                    } else {
                        var v33: String = ""
                        v33 = "core"
                        var v34: Boolean = false
                        v34 = (v0 == v33)
                        v35 = v34
                    }
                    var v38: Boolean = false
                    if (v35) {
                        v38 = true
                    } else {
                        var v36: String = ""
                        v36 = "zig"
                        var v37: Boolean = false
                        v37 = (v0 == v36)
                        v38 = v37
                    }
                    var v41: Boolean = false
                    if (v38) {
                        v41 = true
                    } else {
                        var v39: String = ""
                        v39 = "gleam"
                        var v40: Boolean = false
                        v40 = (v0 == v39)
                        v41 = v40
                    }
                    var v44: Boolean = false
                    if (v41) {
                        v44 = true
                    } else {
                        var v42: String = ""
                        v42 = "formatter"
                        var v43: Boolean = false
                        v43 = (v0 == v42)
                        v44 = v43
                    }
                    var v47: Boolean = false
                    if (v44) {
                        v47 = true
                    } else {
                        var v45: String = ""
                        v45 = "hopacsota"
                        var v46: Boolean = false
                        v46 = (v0 == v45)
                        v47 = v46
                    }
                    var v50: Boolean = false
                    if (v47) {
                        v50 = true
                    } else {
                        var v48: String = ""
                        v48 = "edition"
                        var v49: Boolean = false
                        v49 = (v0 == v48)
                        v50 = v49
                    }
                    if (v50) {
                        return US4_Compiler
                    } else {
                        var v52: String = ""
                        v52 = "tibia"
                        var v53: Boolean = false
                        v53 = (v0 == v52)
                        if (v53) {
                            return US4_Tibia
                        } else {
                            var v55: String = ""
                            v55 = "resilience"
                            var v56: Boolean = false
                            v56 = (v0 == v55)
                            if (v56) {
                                return US4_Resilience
                            } else {
                                var v58: String = ""
                                v58 = "cells"
                                var v59: Boolean = false
                                v59 = (v0 == v58)
                                var v62: Boolean = false
                                if (v59) {
                                    v62 = true
                                } else {
                                    var v60: String = ""
                                    v60 = "kino"
                                    var v61: Boolean = false
                                    v61 = (v0 == v60)
                                    v62 = v61
                                }
                                if (v62) {
                                    return US4_Cells
                                } else {
                                    var v64: String = ""
                                    v64 = "ci"
                                    var v65: Boolean = false
                                    v65 = (v0 == v64)
                                    var v68: Boolean = false
                                    if (v65) {
                                        v68 = true
                                    } else {
                                        var v66: String = ""
                                        v66 = "matrix"
                                        var v67: Boolean = false
                                        v67 = (v0 == v66)
                                        v68 = v67
                                    }
                                    var v71: Boolean = false
                                    if (v68) {
                                        v71 = true
                                    } else {
                                        var v69: String = ""
                                        v69 = "harness"
                                        var v70: Boolean = false
                                        v70 = (v0 == v69)
                                        v71 = v70
                                    }
                                    var v74: Boolean = false
                                    if (v71) {
                                        v74 = true
                                    } else {
                                        var v72: String = ""
                                        v72 = "tests"
                                        var v73: Boolean = false
                                        v73 = (v0 == v72)
                                        v74 = v73
                                    }
                                    if (v74) {
                                        return US4_Ci
                                    } else {
                                        return US4_Other
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
fun sp_is_lane_19(p0: String): Boolean {
    var v0: String = p0
    var v1: US4 = US4_Main
    v1 = sp_area_of_word_20(v0)
    when (v1.tag) {
        9 -> {
            return false
        }
        else -> {
            var v2: String = ""
            v2 = "lib"
            var v3: Boolean = false
            v3 = (v0 == v2)
            var v6: Boolean = false
            if (v3) {
                v6 = true
            } else {
                var v4: String = ""
                v4 = "core"
                var v5: Boolean = false
                v5 = (v0 == v4)
                v6 = v5
            }
            var v9: Boolean = false
            if (v6) {
                v9 = true
            } else {
                var v7: String = ""
                v7 = "zig"
                var v8: Boolean = false
                v8 = (v0 == v7)
                v9 = v8
            }
            var v12: Boolean = false
            if (v9) {
                v12 = true
            } else {
                var v10: String = ""
                v10 = "gleam"
                var v11: Boolean = false
                v11 = (v0 == v10)
                v12 = v11
            }
            var v15: Boolean = false
            if (v12) {
                v15 = true
            } else {
                var v13: String = ""
                v13 = "formatter"
                var v14: Boolean = false
                v14 = (v0 == v13)
                v15 = v14
            }
            var v18: Boolean = false
            if (v15) {
                v18 = true
            } else {
                var v16: String = ""
                v16 = "hopacsota"
                var v17: Boolean = false
                v17 = (v0 == v16)
                v18 = v17
            }
            var v21: Boolean = false
            if (v18) {
                v21 = true
            } else {
                var v19: String = ""
                v19 = "edition"
                var v20: Boolean = false
                v20 = (v0 == v19)
                v21 = v20
            }
            var v24: Boolean = false
            if (v21) {
                v24 = true
            } else {
                var v22: String = ""
                v22 = "sefaz"
                var v23: Boolean = false
                v23 = (v0 == v22)
                v24 = v23
            }
            var v27: Boolean = false
            if (v24) {
                v27 = true
            } else {
                var v25: String = ""
                v25 = "matrix"
                var v26: Boolean = false
                v26 = (v0 == v25)
                v27 = v26
            }
            var v30: Boolean = false
            if (v27) {
                v30 = true
            } else {
                var v28: String = ""
                v28 = "harness"
                var v29: Boolean = false
                v29 = (v0 == v28)
                v30 = v29
            }
            var v33: Boolean = false
            if (v30) {
                v33 = true
            } else {
                var v31: String = ""
                v31 = "tests"
                var v32: Boolean = false
                v32 = (v0 == v31)
                v33 = v32
            }
            var v36: Boolean = false
            if (v33) {
                v36 = true
            } else {
                var v34: String = ""
                v34 = "x-dumps"
                var v35: Boolean = false
                v35 = (v0 == v34)
                v36 = v35
            }
            var v37: Boolean = false
            v37 = (v36 == false)
            return v37
        }
    }
}
fun sp_first_lane_18(p0: UH5): US7 {
    var v0: UH5 = p0
    when (v0.tag) {
        1 -> {
            var v1: String = (v0 as UH5_Cons).f0
            var v2: UH5 = (v0 as UH5_Cons).f1
            var v3: Boolean = false
            v3 = sp_is_lane_19(v1)
            if (v3) {
                return US7_Some(v1)
            } else {
                return sp_first_lane_18(v2)
            }
        }
        0 -> {
            return US7_None
        }
        else -> throw RuntimeException()
    }
}
fun sp_area_of_tags_16(p0: String): US4 {
    var v0: String = p0
    var v1: String = ""
    v1 = ","
    var v2: UH5 = UH5_Nil
    v2 = sp_split_list_2(v1, v0)
    var v3: US7 = US7_Some("")
    v3 = sp_first_team_17(v2)
    when (v3.tag) {
        1 -> {
            var v6: US7 = US7_Some("")
            v6 = sp_first_lane_18(v2)
            when (v6.tag) {
                1 -> {
                    when (v2.tag) {
                        1 -> {
                            var v9: String = (v2 as UH5_Cons).f0
                            var v10: UH5 = (v2 as UH5_Cons).f1
                            return sp_area_of_word_20(v9)
                        }
                        0 -> {
                            return US4_Other
                        }
                        else -> throw RuntimeException()
                    }
                }
                0 -> {
                    var v7: String = (v6 as US7_Some).f0
                    return sp_area_of_word_20(v7)
                }
                else -> throw RuntimeException()
            }
        }
        0 -> {
            var v4: String = (v3 as US7_Some).f0
            return sp_area_of_word_20(v4)
        }
        else -> throw RuntimeException()
    }
}
fun sp_parse_task_6(p0: String): US6 {
    var v0: String = p0
    var v1: String = ""
    v1 = "() = Task (\""
    var v2: Int = 0
    v2 = (v0).indexOf(v1).let { i -> if (i < 0) -1 else (v0).substring(0, i).toByteArray(Charsets.UTF_8).size }
    var v3: Boolean = false
    v3 = (v2 < 0)
    if (v3) {
        return US6_None
    } else {
        var v5: Int = 0
        v5 = (v2 + 12)
        var v6: Int = 0
        v6 = spiralStringLength(v0)
        var v7: Int = 0
        v7 = (v6 - 1)
        var v8: String = ""
        v8 = spiralStringSlice(v0, v5, v7)
        var v9: String = ""
        v9 = "\", \""
        var v10: Int = 0
        v10 = (v8).indexOf(v9).let { i -> if (i < 0) -1 else (v8).substring(0, i).toByteArray(Charsets.UTF_8).size }
        var v11: String = ""
        v11 = "\", Epic, P"
        var v12: String = ""
        v12 = "\", Feature, P"
        var v13: String = ""
        v13 = "\", Story, P"
        var v14: String = ""
        v14 = "\", TaskKind, P"
        var v15: UH5 = UH5_Nil
        v15 = UH5_Nil
        var v16: UH5 = UH5_Nil
        v16 = UH5_Cons(v14, v15)
        var v17: UH5 = UH5_Nil
        v17 = UH5_Cons(v13, v16)
        var v18: UH5 = UH5_Nil
        v18 = UH5_Cons(v12, v17)
        var v19: UH5 = UH5_Nil
        v19 = UH5_Cons(v11, v18)
        var v20: Int = 0
        v20 = -1
        var v21: Int = 0
        v21 = sp_kind_marker_7(v19, v8, v20)
        var v22: Boolean = false
        v22 = (v10 < 0)
        var v24: Boolean = false
        if (v22) {
            v24 = true
        } else {
            var v23: Boolean = false
            v23 = (v21 < v10)
            v24 = v23
        }
        if (v24) {
            return US6_None
        } else {
            var v26: Int = 0
            v26 = (v10 - 1)
            var v27: String = ""
            v27 = spiralStringSlice(v8, 0, v26)
            var v28: Int = 0
            v28 = (v10 + 4)
            var v29: Int = 0
            v29 = (v21 - 1)
            var v30: String = ""
            v30 = spiralStringSlice(v8, v28, v29)
            var v31: String = ""
            v31 = sp_unescaped_9(v30)
            var v32: Int = 0
            v32 = (v21 + 3)
            var v33: Int = 0
            v33 = spiralStringLength(v8)
            var v34: Int = 0
            v34 = (v33 - 2)
            var v35: String = ""
            v35 = spiralStringSlice(v8, v32, v34)
            var v36: String = ""
            v36 = ", "
            var v37: UH5 = UH5_Nil
            v37 = sp_split_list_2(v36, v35)
            var v38: Int = 0
            v38 = 5
            var v39: String = ""
            v39 = "\"\""
            var v40: String = ""
            v40 = sp_field_10(v37, v38, v39)
            var v41: String = ""
            v41 = sp_unquoted_12(v40)
            var v42: Int = 0
            v42 = 0
            var v43: String = ""
            v43 = ""
            var v44: String = ""
            v44 = sp_field_10(v37, v42, v43)
            var v45: US1 = US1_Epic
            v45 = sp_work_of_13(v44)
            var v46: Int = 0
            v46 = 1
            var v47: String = ""
            v47 = sp_field_10(v37, v46, v43)
            var v48: US2 = US2_P0
            v48 = sp_priority_of_14(v47)
            var v49: Int = 0
            v49 = 4
            var v50: String = ""
            v50 = sp_field_10(v37, v49, v43)
            var v51: US3 = US3_Planned
            v51 = sp_status_of_15(v50)
            var v52: Int = 0
            v52 = 6
            var v53: String = ""
            v53 = sp_field_10(v37, v52, v39)
            var v54: String = ""
            v54 = sp_unquoted_12(v53)
            var v55: US4 = US4_Main
            v55 = sp_area_of_tags_16(v54)
            var v56: Boolean = false
            v56 = (v41 == v43)
            var v60: UH5 = UH5_Nil
            if (v56) {
                v60 = UH5_Nil
            } else {
                var v58: String = ""
                v58 = ","
                v60 = sp_split_list_2(v58, v41)
            }
            return US6_Some(v27, v31, v45, v48, v51, v55, v60)
        }
    }
}
fun sp_method5(p0: UH5, p1: UH0): UH0 {
    var v0: UH5 = p0
    var v1: UH0 = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH5_Cons).f0
            var v3: UH5 = (v0 as UH5_Cons).f1
            var v4: String = ""
            v4 = "inl "
            var v5: Boolean = false
            v5 = (v2).startsWith(v4)
            var v17: UH0 = UH0_Nil
            if (v5) {
                var v6: US6 = US6_Some("", "", US1_Epic, US2_P0, US3_Planned, US4_Main, UH5_Nil)
                v6 = sp_parse_task_6(v2)
                when (v6.tag) {
                    1 -> {
                        v17 = v1
                    }
                    0 -> {
                        var v7: String = (v6 as US6_Some).f0
                        var v8: String = (v6 as US6_Some).f1
                        var v9: US1 = (v6 as US6_Some).f2
                        var v10: US2 = (v6 as US6_Some).f3
                        var v11: US3 = (v6 as US6_Some).f4
                        var v12: US4 = (v6 as US6_Some).f5
                        var v13: UH5 = (v6 as US6_Some).f6
                        v17 = UH0_Cons(v7, v8, v9, v10, v11, v12, v13, v1)
                    }
                    else -> throw RuntimeException()
                }
            } else {
                v17 = v1
            }
            return sp_method5(v3, v17)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_parse_items_0(p0: String): UH0 {
    var v0: String = p0
    var v1: String = ""
    v1 = "\n"
    var v2: UH5 = UH5_Nil
    v2 = sp_split_list_2(v1, v0)
    var v3: UH0 = UH0_Nil
    v3 = UH0_Nil
    var v4: UH0 = UH0_Nil
    v4 = sp_method5(v2, v3)
    var v5: UH0 = UH0_Nil
    v5 = UH0_Nil
    return sp_method1(v4, v5)
}
fun sp_method21(p0: UH0, p1: Int): Int {
    var v0: UH0 = p0
    var v1: Int = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH0_Cons).f0
            var v3: String = (v0 as UH0_Cons).f1
            var v4: US1 = (v0 as UH0_Cons).f2
            var v5: US2 = (v0 as UH0_Cons).f3
            var v6: US3 = (v0 as UH0_Cons).f4
            var v7: US4 = (v0 as UH0_Cons).f5
            var v8: UH5 = (v0 as UH0_Cons).f6
            var v9: UH0 = (v0 as UH0_Cons).f7
            var v10: Int = 0
            v10 = (v1 + 1)
            return sp_method21(v9, v10)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_method23(p0: UH0, p1: UH5): UH5 {
    var v0: UH0 = p0
    var v1: UH5 = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH0_Cons).f0
            var v3: String = (v0 as UH0_Cons).f1
            var v4: US1 = (v0 as UH0_Cons).f2
            var v5: US2 = (v0 as UH0_Cons).f3
            var v6: US3 = (v0 as UH0_Cons).f4
            var v7: US4 = (v0 as UH0_Cons).f5
            var v8: UH5 = (v0 as UH0_Cons).f6
            var v9: UH0 = (v0 as UH0_Cons).f7
            var v10: UH5 = UH5_Nil
            v10 = sp_method23(v9, v1)
            return UH5_Cons(v2, v10)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_method27(p0: String, p1: UH5, p2: Boolean): Boolean {
    var v0: String = p0
    var v1: UH5 = p1
    var v2: Boolean = p2
    when (v1.tag) {
        1 -> {
            var v3: String = (v1 as UH5_Cons).f0
            var v4: UH5 = (v1 as UH5_Cons).f1
            var v6: Boolean = false
            if (v2) {
                v6 = true
            } else {
                var v5: Boolean = false
                v5 = (v3 == v0)
                v6 = v5
            }
            return sp_method27(v0, v4, v6)
        }
        0 -> {
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_known_26(p0: UH5, p1: String): Boolean {
    var v0: UH5 = p0
    var v1: String = p1
    var v2: Boolean = false
    v2 = false
    return sp_method27(v1, v0, v2)
}
fun sp_method25(p0: UH5, p1: String, p2: String, p3: US1, p4: US2, p5: US3, p6: US4, p7: UH5, p8: UH5, p9: UH17): UH17 {
    var v0: UH5 = p0
    var v1: String = p1
    var v2: String = p2
    var v3: US1 = p3
    var v4: US2 = p4
    var v5: US3 = p5
    var v6: US4 = p6
    var v7: UH5 = p7
    var v8: UH5 = p8
    var v9: UH17 = p9
    when (v8.tag) {
        1 -> {
            var v10: String = (v8 as UH5_Cons).f0
            var v11: UH5 = (v8 as UH5_Cons).f1
            var v12: Boolean = false
            v12 = sp_known_26(v0, v10)
            var v15: Boolean = false
            if (v12) {
                var v13: Boolean = false
                v13 = (v10 == v1)
                var v14: Boolean = false
                v14 = (v13 == false)
                v15 = v14
            } else {
                v15 = false
            }
            var v21: UH17 = UH17_Nil
            if (v15) {
                var v16: String = ""
                v16 = ""
                var v17: String = ""
                v17 = "#55606B"
                var v18: US13 = US13_Solid
                v18 = US13_Solid
                var v19: US14 = US14_Standard
                v19 = US14_Standard
                v21 = UH17_Cons(v10, v1, v16, v17, v18, 12, v19, v9)
            } else {
                v21 = v9
            }
            return sp_method25(v0, v1, v2, v3, v4, v5, v6, v7, v11, v21)
        }
        0 -> {
            return v9
        }
        else -> throw RuntimeException()
    }
}
fun sp_method24(p0: UH5, p1: UH0, p2: UH17): UH17 {
    var v0: UH5 = p0
    var v1: UH0 = p1
    var v2: UH17 = p2
    when (v1.tag) {
        1 -> {
            var v3: String = (v1 as UH0_Cons).f0
            var v4: String = (v1 as UH0_Cons).f1
            var v5: US1 = (v1 as UH0_Cons).f2
            var v6: US2 = (v1 as UH0_Cons).f3
            var v7: US3 = (v1 as UH0_Cons).f4
            var v8: US4 = (v1 as UH0_Cons).f5
            var v9: UH5 = (v1 as UH0_Cons).f6
            var v10: UH0 = (v1 as UH0_Cons).f7
            var v11: UH17 = UH17_Nil
            v11 = sp_method25(v0, v3, v4, v5, v6, v7, v8, v9, v9, v2)
            return sp_method24(v0, v10, v11)
        }
        0 -> {
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_method28(p0: UH17, p1: UH17): UH17 {
    var v0: UH17 = p0
    var v1: UH17 = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH17_Cons).f0
            var v3: String = (v0 as UH17_Cons).f1
            var v4: String = (v0 as UH17_Cons).f2
            var v5: String = (v0 as UH17_Cons).f3
            var v6: US13 = (v0 as UH17_Cons).f4
            var v7: Int = (v0 as UH17_Cons).f5
            var v8: US14 = (v0 as UH17_Cons).f6
            var v9: UH17 = (v0 as UH17_Cons).f7
            var v10: UH17 = UH17_Nil
            v10 = UH17_Cons(v2, v3, v4, v5, v6, v7, v8, v1)
            return sp_method28(v9, v10)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_method29(p0: UH17, p1: Int): Int {
    var v0: UH17 = p0
    var v1: Int = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH17_Cons).f0
            var v3: String = (v0 as UH17_Cons).f1
            var v4: String = (v0 as UH17_Cons).f2
            var v5: String = (v0 as UH17_Cons).f3
            var v6: US13 = (v0 as UH17_Cons).f4
            var v7: Int = (v0 as UH17_Cons).f5
            var v8: US14 = (v0 as UH17_Cons).f6
            var v9: UH17 = (v0 as UH17_Cons).f7
            var v10: Int = 0
            v10 = (v1 + 1)
            return sp_method29(v9, v10)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_status_fill_31(p0: US3): String {
    var v0: US3 = p0
    when (v0.tag) {
        1 -> {
            var v2: String = ""
            v2 = "#FFB25B"
            return v2
        }
        2 -> {
            var v3: String = ""
            v3 = "#FF6F6F"
            return v3
        }
        4 -> {
            var v1: String = ""
            v1 = "#BFE3C0"
            return v1
        }
        3 -> {
            var v4: String = ""
            v4 = "#CDB4E6"
            return v4
        }
        0 -> {
            var v5: String = ""
            v5 = "#A9CCFF"
            return v5
        }
        else -> throw RuntimeException()
    }
}
fun sp_legend_30(): UH10 {
    var v0: US3 = US3_Planned
    v0 = US3_Planned
    var v1: String = ""
    v1 = sp_status_fill_31(v0)
    var v2: US3 = US3_Planned
    v2 = US3_Active
    var v3: String = ""
    v3 = sp_status_fill_31(v2)
    var v4: US3 = US3_Planned
    v4 = US3_Blocked
    var v5: String = ""
    v5 = sp_status_fill_31(v4)
    var v6: US3 = US3_Planned
    v6 = US3_Paused
    var v7: String = ""
    v7 = sp_status_fill_31(v6)
    var v8: US3 = US3_Planned
    v8 = US3_Done
    var v9: String = ""
    v9 = sp_status_fill_31(v8)
    var v10: String = ""
    v10 = "Epic"
    var v11: US12 = US12_Rectangle
    v11 = US12_Hexagon
    var v12: String = ""
    v12 = "#FFFFFF"
    var v13: String = ""
    v13 = "#333333"
    var v14: US13 = US13_Solid
    v14 = US13_Solid
    var v15: String = ""
    v15 = "#000000"
    var v16: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v16 = US11_Swatch(v10, v11, v12, v13, v14, 15, v15)
    var v17: String = ""
    v17 = "Feature"
    var v18: US12 = US12_Rectangle
    v18 = US12_RoundRectangle
    var v19: US13 = US13_Solid
    v19 = US13_Solid
    var v20: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v20 = US11_Swatch(v17, v18, v12, v13, v19, 15, v15)
    var v21: String = ""
    v21 = "Story"
    var v22: US12 = US12_Rectangle
    v22 = US12_Rectangle
    var v23: US13 = US13_Solid
    v23 = US13_Solid
    var v24: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v24 = US11_Swatch(v21, v22, v12, v13, v23, 15, v15)
    var v25: String = ""
    v25 = "Task"
    var v26: US12 = US12_Rectangle
    v26 = US12_Parallelogram
    var v27: US13 = US13_Solid
    v27 = US13_Dashed
    var v28: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v28 = US11_Swatch(v25, v26, v12, v13, v27, 15, v15)
    var v29: String = ""
    v29 = "Planned"
    var v30: US12 = US12_Rectangle
    v30 = US12_Rectangle
    var v31: US13 = US13_Solid
    v31 = US13_Solid
    var v32: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v32 = US11_Swatch(v29, v30, v1, v13, v31, 10, v15)
    var v33: String = ""
    v33 = "Active"
    var v34: US12 = US12_Rectangle
    v34 = US12_Rectangle
    var v35: US13 = US13_Solid
    v35 = US13_Solid
    var v36: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v36 = US11_Swatch(v33, v34, v3, v13, v35, 10, v15)
    var v37: String = ""
    v37 = "Blocked"
    var v38: US12 = US12_Rectangle
    v38 = US12_Rectangle
    var v39: US13 = US13_Solid
    v39 = US13_Solid
    var v40: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v40 = US11_Swatch(v37, v38, v5, v13, v39, 10, v15)
    var v41: String = ""
    v41 = "Paused"
    var v42: US12 = US12_Rectangle
    v42 = US12_Rectangle
    var v43: US13 = US13_Solid
    v43 = US13_Solid
    var v44: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v44 = US11_Swatch(v41, v42, v7, v13, v43, 10, v15)
    var v45: String = ""
    v45 = "Done"
    var v46: US12 = US12_Rectangle
    v46 = US12_Rectangle
    var v47: US13 = US13_Solid
    v47 = US13_Solid
    var v48: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v48 = US11_Swatch(v45, v46, v9, v13, v47, 10, v15)
    var v49: String = ""
    v49 = "priority P0"
    var v50: US12 = US12_Rectangle
    v50 = US12_Rectangle
    var v51: String = ""
    v51 = "#B00020"
    var v52: US13 = US13_Solid
    v52 = US13_Solid
    var v53: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v53 = US11_Swatch(v49, v50, v12, v51, v52, 40, v15)
    var v54: String = ""
    v54 = "priority P1"
    var v55: US12 = US12_Rectangle
    v55 = US12_Rectangle
    var v56: US13 = US13_Solid
    v56 = US13_Solid
    var v57: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v57 = US11_Swatch(v54, v55, v12, v13, v56, 25, v15)
    var v58: String = ""
    v58 = "prerequisite -> dependent"
    var v59: String = ""
    v59 = "#55606B"
    var v60: US13 = US13_Solid
    v60 = US13_Solid
    var v61: US14 = US14_Standard
    v61 = US14_Standard
    var v62: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v62 = US11_Stroke(v58, v59, v60, 12, v61)
    var v63: UH10 = UH10_Nil
    v63 = UH10_Nil
    var v64: UH10 = UH10_Nil
    v64 = UH10_Cons(v62, v63)
    var v65: UH10 = UH10_Nil
    v65 = UH10_Cons(v57, v64)
    var v66: UH10 = UH10_Nil
    v66 = UH10_Cons(v53, v65)
    var v67: UH10 = UH10_Nil
    v67 = UH10_Cons(v48, v66)
    var v68: UH10 = UH10_Nil
    v68 = UH10_Cons(v44, v67)
    var v69: UH10 = UH10_Nil
    v69 = UH10_Cons(v40, v68)
    var v70: UH10 = UH10_Nil
    v70 = UH10_Cons(v36, v69)
    var v71: UH10 = UH10_Nil
    v71 = UH10_Cons(v32, v70)
    var v72: UH10 = UH10_Nil
    v72 = UH10_Cons(v28, v71)
    var v73: UH10 = UH10_Nil
    v73 = UH10_Cons(v24, v72)
    var v74: UH10 = UH10_Nil
    v74 = UH10_Cons(v20, v73)
    return UH10_Cons(v16, v74)
}
fun sp_area_name_34(p0: US4): String {
    var v0: US4 = p0
    when (v0.tag) {
        7 -> {
            var v8: String = ""
            v8 = "cells"
            return v8
        }
        8 -> {
            var v9: String = ""
            v9 = "ci"
            return v9
        }
        4 -> {
            var v5: String = ""
            v5 = "compiler"
            return v5
        }
        3 -> {
            var v4: String = ""
            v4 = "fiscal"
            return v4
        }
        1 -> {
            var v2: String = ""
            v2 = "kit"
            return v2
        }
        0 -> {
            var v1: String = ""
            v1 = "main"
            return v1
        }
        9 -> {
            var v10: String = ""
            v10 = "other"
            return v10
        }
        6 -> {
            var v7: String = ""
            v7 = "resilience"
            return v7
        }
        5 -> {
            var v6: String = ""
            v6 = "tibia"
            return v6
        }
        2 -> {
            var v3: String = ""
            v3 = "x"
            return v3
        }
        else -> throw RuntimeException()
    }
}
fun sp_shortened_35(p0: Int, p1: String): String {
    var v0: Int = p0
    var v1: String = p1
    var v2: Int = 0
    v2 = spiralStringLength(v1)
    var v3: Boolean = false
    v3 = (v2 > v0)
    if (v3) {
        var v4: Int = 0
        v4 = (v0 - 2)
        var v5: String = ""
        v5 = spiralStringSlice(v1, 0, v4)
        var v6: String = ""
        v6 = "..."
        var v7: String = ""
        v7 = v5 + v6 
        return v7
    } else {
        return v1
    }
}
fun sp_item_style_36(p0: String, p1: String, p2: US1, p3: US2, p4: US3, p5: US4, p6: UH5): SpiralTuple3 {
    var v0: String = p0
    var v1: String = p1
    var v2: US1 = p2
    var v3: US2 = p3
    var v4: US3 = p4
    var v5: US4 = p5
    var v6: UH5 = p6
    var v14: US12 = US12_Rectangle
    when (v2.tag) {
        3 -> {
            v14 = US12_Parallelogram
        }
        0 -> {
            v14 = US12_Hexagon
        }
        1 -> {
            v14 = US12_RoundRectangle
        }
        2 -> {
            v14 = US12_Rectangle
        }
        else -> throw RuntimeException()
    }
    var v18: Int = 0
    when (v3.tag) {
        3 -> {
            v18 = 10
        }
        0 -> {
            v18 = 40
        }
        1 -> {
            v18 = 25
        }
        2 -> {
            v18 = 15
        }
        else -> throw RuntimeException()
    }
    var v21: String = ""
    when (v3.tag) {
        0 -> {
            var v19: String = ""
            v19 = "#B00020"
            v21 = v19
        }
        else -> {
            var v20: String = ""
            v20 = "#333333"
            v21 = v20
        }
    }
    var v24: US13 = US13_Solid
    when (v2.tag) {
        3 -> {
            v24 = US13_Dashed
        }
        else -> {
            v24 = US13_Solid
        }
    }
    var v25: String = ""
    v25 = sp_status_fill_31(v4)
    var v26: String = ""
    v26 = "#000000"
    return SpiralTuple3(v14, v25, v21, v24, v18, v26)
}
fun sp_item_box_33(p0: String, p1: String, p2: US1, p3: US2, p4: US3, p5: US4, p6: UH5): SpiralTuple2 {
    var v0: String = p0
    var v1: String = p1
    var v2: US1 = p2
    var v3: US2 = p3
    var v4: US3 = p4
    var v5: US4 = p5
    var v6: UH5 = p6
    var v8: Int = 0
    when (v2.tag) {
        0 -> {
            v8 = 18
        }
        1 -> {
            v8 = 14
        }
        else -> {
            v8 = 12
        }
    }
    var v9: Int = 0
    v9 = 52
    var v10: String = ""
    v10 = sp_area_name_34(v5)
    var v11: String = ""
    v11 = "["
    var v12: String = ""
    v12 = v11 + v10 
    var v13: String = ""
    v13 = "] "
    var v14: String = ""
    v14 = v12 + v13 
    var v15: String = ""
    v15 = v14 + v1 
    var v16: String = ""
    v16 = sp_shortened_35(v9, v15)
    var v18: Boolean = false
    when (v2.tag) {
        0 -> {
            v18 = true
        }
        1 -> {
            v18 = true
        }
        else -> {
            v18 = false
        }
    }
    var v19: Int = 0
    v19 = spiralStringLength(v0)
    var v20: Int = 0
    v20 = (v19 * v8)
    var v21: Int = 0
    v21 = (v20 * 62)
    var v22: Int = 0
    v22 = (v21 / 100)
    var v23: Int = 0
    v23 = (v22 + 16)
    var v24: Int = 0
    v24 = spiralStringLength(v16)
    var v25: Int = 0
    v25 = (v24 * v8)
    var v26: Int = 0
    if (v18) {
        v26 = 62
    } else {
        v26 = 56
    }
    var v27: Int = 0
    v27 = (v25 * v26)
    var v28: Int = 0
    v28 = (v27 / 100)
    var v29: Int = 0
    v29 = (v28 + 24)
    var v30: Boolean = false
    v30 = (v23 > v29)
    var v31: Int = 0
    if (v30) {
        v31 = v23
    } else {
        v31 = v29
    }
    var v37: Int = 0
    when (v2.tag) {
        3 -> {
            var v32: Int = 0
            v32 = (v31 + 24)
            v37 = v32
        }
        0 -> {
            var v33: Int = 0
            v33 = (v31 * 118)
            var v34: Int = 0
            v34 = (v33 / 100)
            var v35: Int = 0
            v35 = (v34 + 10)
            v37 = v35
        }
        else -> {
            v37 = v31
        }
    }
    var v39: Int = 0
    when (v2.tag) {
        0 -> {
            v39 = 44
        }
        1 -> {
            v39 = 32
        }
        else -> {
            v39 = 26
        }
    }
    var v40: Int = 0
    v40 = (v39 + 16)
    var v41: US12 = US12_Rectangle
    var v42: String = ""
    var v43: String = ""
    var v44: US13 = US13_Solid
    var v45: Int = 0
    var v46: String = ""
    val spiral_tuple2503 = sp_item_style_36(v0, v1, v2, v3, v4, v5, v6)
    v41 = spiral_tuple2503.v0
    v42 = spiral_tuple2503.v1
    v43 = spiral_tuple2503.v2
    v44 = spiral_tuple2503.v3
    v45 = spiral_tuple2503.v4
    v46 = spiral_tuple2503.v5
    var v47: UH5 = UH5_Nil
    v47 = UH5_Nil
    var v48: UH5 = UH5_Nil
    v48 = UH5_Cons(v16, v47)
    return SpiralTuple2(v0, v0, v48, v41, v42, v43, v44, v45, v46, v37, v40, v8, v18)
}
fun sp_method32(p0: UH0, p1: UH15): UH15 {
    var v0: UH0 = p0
    var v1: UH15 = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH0_Cons).f0
            var v3: String = (v0 as UH0_Cons).f1
            var v4: US1 = (v0 as UH0_Cons).f2
            var v5: US2 = (v0 as UH0_Cons).f3
            var v6: US3 = (v0 as UH0_Cons).f4
            var v7: US4 = (v0 as UH0_Cons).f5
            var v8: UH5 = (v0 as UH0_Cons).f6
            var v9: UH0 = (v0 as UH0_Cons).f7
            var v10: UH15 = UH15_Nil
            v10 = sp_method32(v9, v1)
            var v11: String = ""
            var v12: String = ""
            var v13: UH5 = UH5_Nil
            var v14: US12 = US12_Rectangle
            var v15: String = ""
            var v16: String = ""
            var v17: US13 = US13_Solid
            var v18: Int = 0
            var v19: String = ""
            var v20: Int = 0
            var v21: Int = 0
            var v22: Int = 0
            var v23: Boolean = false
            val spiral_tuple1005 = sp_item_box_33(v2, v3, v4, v5, v6, v7, v8)
            v11 = spiral_tuple1005.v0
            v12 = spiral_tuple1005.v1
            v13 = spiral_tuple1005.v2
            v14 = spiral_tuple1005.v3
            v15 = spiral_tuple1005.v4
            v16 = spiral_tuple1005.v5
            v17 = spiral_tuple1005.v6
            v18 = spiral_tuple1005.v7
            v19 = spiral_tuple1005.v8
            v20 = spiral_tuple1005.v9
            v21 = spiral_tuple1005.v10
            v22 = spiral_tuple1005.v11
            v23 = spiral_tuple1005.v12
            var v24: UH16 = UH16_Leaf("", "", UH5_Nil, US12_Rectangle, "", "", US13_Solid, 0, "", 0, 0, 0, false)
            v24 = UH16_Leaf(v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21, v22, v23)
            return UH15_Cons(v24, v10)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_backlog_diagram_22(p0: UH0): SpiralTuple1 {
    var v0: UH0 = p0
    var v1: UH5 = UH5_Nil
    v1 = UH5_Nil
    var v2: UH5 = UH5_Nil
    v2 = sp_method23(v0, v1)
    var v3: UH17 = UH17_Nil
    v3 = UH17_Nil
    var v4: UH17 = UH17_Nil
    v4 = sp_method24(v2, v0, v3)
    var v5: UH17 = UH17_Nil
    v5 = UH17_Nil
    var v6: UH17 = UH17_Nil
    v6 = sp_method28(v4, v5)
    var v7: Int = 0
    v7 = 0
    var v8: Int = 0
    v8 = sp_method21(v0, v7)
    var v9: String = ""
    v9 = (v8).toString()
    var v10: String = ""
    v10 = "i574n backlog - "
    var v11: String = ""
    v11 = v10 + v9 
    var v12: String = ""
    v12 = " items, "
    var v13: String = ""
    v13 = v11 + v12 
    var v14: Int = 0
    v14 = 0
    var v15: Int = 0
    v15 = sp_method29(v6, v14)
    var v16: String = ""
    v16 = (v15).toString()
    var v17: String = ""
    v17 = v13 + v16 
    var v18: String = ""
    v18 = " dependencies (docs/backlog/state/agile.spi)"
    var v19: String = ""
    v19 = v17 + v18 
    var v20: UH10 = UH10_Nil
    v20 = sp_legend_30()
    var v21: UH15 = UH15_Nil
    v21 = UH15_Nil
    var v22: UH15 = UH15_Nil
    v22 = sp_method32(v0, v21)
    var v23: String = ""
    v23 = "Layout: yEd Compact Orthogonal, aspect 1.2, grid 10 (headless yFiles). Node text: ID / [area] title. Arrow = prerequisite -> dependent."
    return SpiralTuple1(v19, v23, v20, v22, v6)
}
fun sp_xml_42(p0: String): String {
    var v0: String = p0
    var v1: String = ""
    v1 = "&"
    var v2: String = ""
    v2 = "&amp;"
    var v3: String = ""
    v3 = (v0).replace(v1, v2)
    var v4: String = ""
    v4 = "<"
    var v5: String = ""
    v5 = "&lt;"
    var v6: String = ""
    v6 = (v3).replace(v4, v5)
    var v7: String = ""
    v7 = ">"
    var v8: String = ""
    v8 = "&gt;"
    var v9: String = ""
    v9 = (v6).replace(v7, v8)
    var v10: String = ""
    v10 = "\""
    var v11: String = ""
    v11 = "&quot;"
    var v12: String = ""
    v12 = (v9).replace(v10, v11)
    return v12
}
fun sp_stroke_name_43(p0: US13): String {
    var v0: US13 = p0
    when (v0.tag) {
        3 -> {
            var v4: String = ""
            v4 = "dashed_dotted"
            return v4
        }
        1 -> {
            var v2: String = ""
            v2 = "dashed"
            return v2
        }
        2 -> {
            var v3: String = ""
            v3 = "dotted"
            return v3
        }
        0 -> {
            var v1: String = ""
            v1 = "line"
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_tenths_text_44(p0: Int): String {
    var v0: Int = p0
    var v1: Int = 0
    v1 = (v0 / 10)
    var v2: String = ""
    v2 = (v1).toString()
    var v3: String = ""
    v3 = "."
    var v4: String = ""
    v4 = v2 + v3 
    var v5: Int = 0
    v5 = (v1 * 10)
    var v6: Int = 0
    v6 = (v0 - v5)
    var v7: String = ""
    v7 = (v6).toString()
    var v8: String = ""
    v8 = v4 + v7 
    return v8
}
fun sp_method46(p0: UH5, p1: String): String {
    var v0: UH5 = p0
    var v1: String = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH5_Cons).f0
            var v3: UH5 = (v0 as UH5_Cons).f1
            var v4: String = ""
            v4 = "<br>"
            var v5: String = ""
            v5 = v1 + v4 
            var v6: String = ""
            v6 = sp_xml_42(v2)
            var v7: String = ""
            v7 = v5 + v6 
            return sp_method46(v3, v7)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_label_html_45(p0: String, p1: String, p2: UH5, p3: US12, p4: String, p5: String, p6: US13, p7: Int, p8: String, p9: Int, p10: Int, p11: Int, p12: Boolean): String {
    var v0: String = p0
    var v1: String = p1
    var v2: UH5 = p2
    var v3: US12 = p3
    var v4: String = p4
    var v5: String = p5
    var v6: US13 = p6
    var v7: Int = p7
    var v8: String = p8
    var v9: Int = p9
    var v10: Int = p10
    var v11: Int = p11
    var v12: Boolean = p12
    var v13: String = ""
    v13 = ""
    var v14: String = ""
    v14 = sp_method46(v2, v13)
    var v15: String = ""
    v15 = sp_xml_42(v1)
    var v16: String = ""
    v16 = "<html><div style='text-align:center'><b>"
    var v17: String = ""
    v17 = v16 + v15 
    var v18: String = ""
    v18 = "</b>"
    var v19: String = ""
    v19 = v17 + v18 
    var v20: String = ""
    v20 = v19 + v14 
    var v21: String = ""
    v21 = "</div></html>"
    var v22: String = ""
    v22 = v20 + v21 
    return v22
}
fun sp_shape_name_47(p0: US12): String {
    var v0: US12 = p0
    when (v0.tag) {
        5 -> {
            var v6: String = ""
            v6 = "ellipse"
            return v6
        }
        2 -> {
            var v3: String = ""
            v3 = "hexagon"
            return v3
        }
        3 -> {
            var v4: String = ""
            v4 = "octagon"
            return v4
        }
        4 -> {
            var v5: String = ""
            v5 = "parallelogram"
            return v5
        }
        0 -> {
            var v1: String = ""
            v1 = "rectangle"
            return v1
        }
        1 -> {
            var v2: String = ""
            v2 = "roundrectangle"
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_graphml_box_41(p0: US20, p1: String, p2: String, p3: UH5, p4: US12, p5: String, p6: String, p7: US13, p8: Int, p9: String, p10: Int, p11: Int, p12: Int, p13: Boolean) {
    var v0: US20 = p0
    var v1: String = p1
    var v2: String = p2
    var v3: UH5 = p3
    var v4: US12 = p4
    var v5: String = p5
    var v6: String = p6
    var v7: US13 = p7
    var v8: Int = p8
    var v9: String = p9
    var v10: Int = p10
    var v11: Int = p11
    var v12: Int = p12
    var v13: Boolean = p13
    var v14: String = ""
    v14 = sp_xml_42(v1)
    var v15: String = ""
    v15 = "<node id=\""
    var v16: String = ""
    v16 = v15 + v14 
    var v17: String = ""
    v17 = "\"><data key=\"d0\"><y:ShapeNode><y:Geometry height=\""
    var v18: String = ""
    v18 = v16 + v17 
    var v19: String = ""
    v19 = (v11).toString()
    var v20: String = ""
    v20 = v18 + v19 
    var v21: String = ""
    v21 = "\" width=\""
    var v22: String = ""
    v22 = v20 + v21 
    var v23: String = ""
    v23 = (v10).toString()
    var v24: String = ""
    v24 = v22 + v23 
    var v25: String = ""
    v25 = "\" x=\"0\" y=\"0\"/>"
    var v26: String = ""
    v26 = v24 + v25 
    var v28: Int = 0
    when (v0.tag) {
        0 -> {
            var v27: Int = (v0 as US20_Handle).f0
            v28 = v27
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v28).append(v26)
    var v29: String = ""
    v29 = "<y:Fill color=\""
    var v30: String = ""
    v30 = v29 + v5 
    var v31: String = ""
    v31 = "\" transparent=\"false\"/><y:BorderStyle color=\""
    var v32: String = ""
    v32 = v30 + v31 
    var v33: String = ""
    v33 = v32 + v6 
    var v34: String = ""
    v34 = "\" type=\""
    var v35: String = ""
    v35 = v33 + v34 
    var v36: String = ""
    v36 = sp_stroke_name_43(v7)
    var v37: String = ""
    v37 = v35 + v36 
    var v38: String = ""
    v38 = v37 + v21 
    var v39: String = ""
    v39 = sp_tenths_text_44(v8)
    var v40: String = ""
    v40 = v38 + v39 
    var v41: String = ""
    v41 = "\"/>"
    var v42: String = ""
    v42 = v40 + v41 
    var v44: Int = 0
    when (v0.tag) {
        0 -> {
            var v43: Int = (v0 as US20_Handle).f0
            v44 = v43
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v44).append(v42)
    var v45: String = ""
    v45 = (v12).toString()
    var v46: String = ""
    v46 = "<y:NodeLabel fontSize=\""
    var v47: String = ""
    v47 = v46 + v45 
    var v48: String = ""
    v48 = "\" textColor=\""
    var v49: String = ""
    v49 = v47 + v48 
    var v50: String = ""
    v50 = v49 + v9 
    var v51: String = ""
    v51 = "\" modelName=\"internal\" modelPosition=\"c\">"
    var v52: String = ""
    v52 = v50 + v51 
    var v53: String = ""
    v53 = sp_label_html_45(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13)
    var v54: String = ""
    v54 = sp_xml_42(v53)
    var v55: String = ""
    v55 = v52 + v54 
    var v56: String = ""
    v56 = "</y:NodeLabel>"
    var v57: String = ""
    v57 = v55 + v56 
    var v59: Int = 0
    when (v0.tag) {
        0 -> {
            var v58: Int = (v0 as US20_Handle).f0
            v59 = v58
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v59).append(v57)
    var v60: String = ""
    v60 = sp_shape_name_47(v4)
    var v61: String = ""
    v61 = "<y:Shape type=\""
    var v62: String = ""
    v62 = v61 + v60 
    var v63: String = ""
    v63 = "\"/></y:ShapeNode></data></node>\n"
    var v64: String = ""
    v64 = v62 + v63 
    var v66: Int = 0
    when (v0.tag) {
        0 -> {
            var v65: Int = (v0 as US20_Handle).f0
            v66 = v65
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v66).append(v64)
}
fun sp_graphml_frame_head_48(p0: US20, p1: String, p2: String, p3: String, p4: String, p5: Boolean, p6: Int) {
    var v0: US20 = p0
    var v1: String = p1
    var v2: String = p2
    var v3: String = p3
    var v4: String = p4
    var v5: Boolean = p5
    var v6: Int = p6
    var v9: String = ""
    if (v5) {
        var v7: String = ""
        v7 = "dashed"
        v9 = v7
    } else {
        var v8: String = ""
        v8 = "line"
        v9 = v8
    }
    var v12: String = ""
    if (v5) {
        var v10: String = ""
        v10 = "1.5"
        v12 = v10
    } else {
        var v11: String = ""
        v11 = "3.0"
        v12 = v11
    }
    var v15: String = ""
    if (v5) {
        var v13: String = ""
        v13 = "true"
        v15 = v13
    } else {
        var v14: String = ""
        v14 = "false"
        v15 = v14
    }
    var v16: String = ""
    v16 = sp_xml_42(v1)
    var v17: String = ""
    v17 = "<node id=\""
    var v18: String = ""
    v18 = v17 + v16 
    var v19: String = ""
    v19 = "\" yfiles.foldertype=\"group\"><data key=\"d0\"><y:ProxyAutoBoundsNode><y:Realizers active=\"0\"><y:GroupNode>"
    var v20: String = ""
    v20 = v18 + v19 
    var v22: Int = 0
    when (v0.tag) {
        0 -> {
            var v21: Int = (v0 as US20_Handle).f0
            v22 = v21
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v22).append(v20)
    var v23: String = ""
    v23 = "<y:Geometry height=\"100\" width=\"200\" x=\"0\" y=\"0\"/><y:Fill color=\""
    var v24: String = ""
    v24 = v23 + v3 
    var v25: String = ""
    v25 = "\" transparent=\""
    var v26: String = ""
    v26 = v24 + v25 
    var v27: String = ""
    v27 = v26 + v15 
    var v28: String = ""
    v28 = "\"/>"
    var v29: String = ""
    v29 = v27 + v28 
    var v31: Int = 0
    when (v0.tag) {
        0 -> {
            var v30: Int = (v0 as US20_Handle).f0
            v31 = v30
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v31).append(v29)
    var v32: String = ""
    v32 = "<y:BorderStyle color=\""
    var v33: String = ""
    v33 = v32 + v4 
    var v34: String = ""
    v34 = "\" type=\""
    var v35: String = ""
    v35 = v33 + v34 
    var v36: String = ""
    v36 = v35 + v9 
    var v37: String = ""
    v37 = "\" width=\""
    var v38: String = ""
    v38 = v36 + v37 
    var v39: String = ""
    v39 = v38 + v12 
    var v40: String = ""
    v40 = v39 + v28 
    var v42: Int = 0
    when (v0.tag) {
        0 -> {
            var v41: Int = (v0 as US20_Handle).f0
            v42 = v41
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v42).append(v40)
    var v43: String = ""
    v43 = "<y:NodeLabel alignment=\"left\" autoSizePolicy=\"node_width\" backgroundColor=\""
    var v44: String = ""
    v44 = v43 + v4 
    var v45: String = ""
    v45 = "\" textColor=\"#FFFFFF\" fontSize=\""
    var v46: String = ""
    v46 = v44 + v45 
    var v47: String = ""
    v47 = (v6).toString()
    var v48: String = ""
    v48 = v46 + v47 
    var v49: String = ""
    v49 = "\" fontStyle=\"bold\" modelName=\"internal\" modelPosition=\"t\">"
    var v50: String = ""
    v50 = v48 + v49 
    var v51: String = ""
    v51 = "      "
    var v52: String = ""
    v52 = v51 + v2 
    var v53: String = ""
    v53 = sp_xml_42(v52)
    var v54: String = ""
    v54 = v50 + v53 
    var v55: String = ""
    v55 = "</y:NodeLabel>"
    var v56: String = ""
    v56 = v54 + v55 
    var v58: Int = 0
    when (v0.tag) {
        0 -> {
            var v57: Int = (v0 as US20_Handle).f0
            v58 = v57
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v58).append(v56)
    var v59: String = ""
    v59 = sp_xml_42(v1)
    var v60: String = ""
    v60 = "<y:Shape type=\"roundrectangle\"/><y:State closed=\"false\"/><y:Insets bottom=\"20\" left=\"20\" right=\"20\" top=\"20\"/></y:GroupNode></y:Realizers></y:ProxyAutoBoundsNode></data><graph edgedefault=\"directed\" id=\""
    var v61: String = ""
    v61 = v60 + v59 
    var v62: String = ""
    v62 = ":\">\n"
    var v63: String = ""
    v63 = v61 + v62 
    var v65: Int = 0
    when (v0.tag) {
        0 -> {
            var v64: Int = (v0 as US20_Handle).f0
            v65 = v64
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v65).append(v63)
}
fun sp_graphml_member_40(p0: US20, p1: UH16) {
    var v0: US20 = p0
    var v1: UH16 = p1
    when (v1.tag) {
        0 -> {
            var v2: String = (v1 as UH16_Leaf).f0
            var v3: String = (v1 as UH16_Leaf).f1
            var v4: UH5 = (v1 as UH16_Leaf).f2
            var v5: US12 = (v1 as UH16_Leaf).f3
            var v6: String = (v1 as UH16_Leaf).f4
            var v7: String = (v1 as UH16_Leaf).f5
            var v8: US13 = (v1 as UH16_Leaf).f6
            var v9: Int = (v1 as UH16_Leaf).f7
            var v10: String = (v1 as UH16_Leaf).f8
            var v11: Int = (v1 as UH16_Leaf).f9
            var v12: Int = (v1 as UH16_Leaf).f10
            var v13: Int = (v1 as UH16_Leaf).f11
            var v14: Boolean = (v1 as UH16_Leaf).f12
            sp_graphml_box_41(v0, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14)
        }
        1 -> {
            var v15: String = (v1 as UH16_Nest).f0
            var v16: String = (v1 as UH16_Nest).f1
            var v17: String = (v1 as UH16_Nest).f2
            var v18: String = (v1 as UH16_Nest).f3
            var v19: Boolean = (v1 as UH16_Nest).f4
            var v20: Int = (v1 as UH16_Nest).f5
            var v21: UH15 = (v1 as UH16_Nest).f6
            sp_graphml_frame_head_48(v0, v15, v16, v17, v18, v19, v20)
            sp_graphml_members_39(v0, v21)
            var v23: Int = 0
            when (v0.tag) {
                0 -> {
                    var v22: Int = (v0 as US20_Handle).f0
                    v23 = v22
                }
                else -> throw RuntimeException()
            }
            var v24: String = ""
            v24 = "</graph></node>\n"
            GraphsHost.sb(v23).append(v24)
        }
        else -> throw RuntimeException()
    }
}
fun sp_graphml_members_39(p0: US20, p1: UH15) {
    var v0: US20 = p0
    var v1: UH15 = p1
    when (v1.tag) {
        1 -> {
            var v2: UH16 = (v1 as UH15_Cons).f0
            var v3: UH15 = (v1 as UH15_Cons).f1
            sp_graphml_member_40(v0, v2)
            sp_graphml_members_39(v0, v3)
        }
        0 -> {
        }
        else -> throw RuntimeException()
    }
}
fun sp_head_name_51(p0: US14): String {
    var v0: US14 = p0
    when (v0.tag) {
        3 -> {
            var v4: String = ""
            v4 = "none"
            return v4
        }
        0 -> {
            var v1: String = ""
            v1 = "standard"
            return v1
        }
        1 -> {
            var v2: String = ""
            v2 = "white_delta"
            return v2
        }
        2 -> {
            var v3: String = ""
            v3 = "white_diamond"
            return v3
        }
        else -> throw RuntimeException()
    }
}
fun sp_graphml_link_50(p0: US20, p1: Int, p2: String, p3: String, p4: String, p5: String, p6: US13, p7: Int, p8: US14) {
    var v0: US20 = p0
    var v1: Int = p1
    var v2: String = p2
    var v3: String = p3
    var v4: String = p4
    var v5: String = p5
    var v6: US13 = p6
    var v7: Int = p7
    var v8: US14 = p8
    var v9: String = ""
    v9 = (v1).toString()
    var v10: String = ""
    v10 = "<edge id=\"e"
    var v11: String = ""
    v11 = v10 + v9 
    var v12: String = ""
    v12 = "\" source=\""
    var v13: String = ""
    v13 = v11 + v12 
    var v14: String = ""
    v14 = sp_xml_42(v2)
    var v15: String = ""
    v15 = v13 + v14 
    var v16: String = ""
    v16 = "\" target=\""
    var v17: String = ""
    v17 = v15 + v16 
    var v18: String = ""
    v18 = sp_xml_42(v3)
    var v19: String = ""
    v19 = v17 + v18 
    var v20: String = ""
    v20 = "\"><data key=\"d2\"><y:PolyLineEdge>"
    var v21: String = ""
    v21 = v19 + v20 
    var v23: Int = 0
    when (v0.tag) {
        0 -> {
            var v22: Int = (v0 as US20_Handle).f0
            v23 = v22
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v23).append(v21)
    var v24: String = ""
    v24 = "<y:LineStyle color=\""
    var v25: String = ""
    v25 = v24 + v5 
    var v26: String = ""
    v26 = "\" type=\""
    var v27: String = ""
    v27 = v25 + v26 
    var v28: String = ""
    v28 = sp_stroke_name_43(v6)
    var v29: String = ""
    v29 = v27 + v28 
    var v30: String = ""
    v30 = "\" width=\""
    var v31: String = ""
    v31 = v29 + v30 
    var v32: String = ""
    v32 = sp_tenths_text_44(v7)
    var v33: String = ""
    v33 = v31 + v32 
    var v34: String = ""
    v34 = "\"/><y:Arrows source=\"none\" target=\""
    var v35: String = ""
    v35 = v33 + v34 
    var v36: String = ""
    v36 = sp_head_name_51(v8)
    var v37: String = ""
    v37 = v35 + v36 
    var v38: String = ""
    v38 = "\"/>"
    var v39: String = ""
    v39 = v37 + v38 
    var v41: Int = 0
    when (v0.tag) {
        0 -> {
            var v40: Int = (v0 as US20_Handle).f0
            v41 = v40
        }
        else -> throw RuntimeException()
    }
    GraphsHost.sb(v41).append(v39)
    var v42: String = ""
    v42 = ""
    var v43: Boolean = false
    v43 = (v4 == v42)
    if (v43) {
    } else {
        var v44: String = ""
        v44 = "<y:EdgeLabel fontSize=\"11\" textColor=\""
        var v45: String = ""
        v45 = v44 + v5 
        var v46: String = ""
        v46 = "\" backgroundColor=\"#FFFFFF\" modelName=\"side_slider\" preferredPlacement=\"anywhere\">"
        var v47: String = ""
        v47 = v45 + v46 
        var v48: String = ""
        v48 = sp_xml_42(v4)
        var v49: String = ""
        v49 = v47 + v48 
        var v50: String = ""
        v50 = "</y:EdgeLabel>"
        var v51: String = ""
        v51 = v49 + v50 
        var v53: Int = 0
        when (v0.tag) {
            0 -> {
                var v52: Int = (v0 as US20_Handle).f0
                v53 = v52
            }
            else -> throw RuntimeException()
        }
        GraphsHost.sb(v53).append(v51)
    }
    var v55: Int = 0
    when (v0.tag) {
        0 -> {
            var v54: Int = (v0 as US20_Handle).f0
            v55 = v54
        }
        else -> throw RuntimeException()
    }
    var v56: String = ""
    v56 = "</y:PolyLineEdge></data></edge>\n"
    GraphsHost.sb(v55).append(v56)
}
fun sp_method49(p0: Int, p1: UH17, p2: Int): Int {
    var v0: Int = p0
    var v1: UH17 = p1
    var v2: Int = p2
    when (v1.tag) {
        1 -> {
            var v3: String = (v1 as UH17_Cons).f0
            var v4: String = (v1 as UH17_Cons).f1
            var v5: String = (v1 as UH17_Cons).f2
            var v6: String = (v1 as UH17_Cons).f3
            var v7: US13 = (v1 as UH17_Cons).f4
            var v8: Int = (v1 as UH17_Cons).f5
            var v9: US14 = (v1 as UH17_Cons).f6
            var v10: UH17 = (v1 as UH17_Cons).f7
            var v11: US20 = US20_Handle(0)
            v11 = US20_Handle(v0)
            sp_graphml_link_50(v11, v2, v3, v4, v5, v6, v7, v8, v9)
            var v12: Int = 0
            v12 = (v2 + 1)
            return sp_method49(v0, v10, v12)
        }
        0 -> {
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_graphml_text_38(p0: String, p1: String, p2: UH10, p3: UH15, p4: UH17): String {
    var v0: String = p0
    var v1: String = p1
    var v2: UH10 = p2
    var v3: UH15 = p3
    var v4: UH17 = p4
    var v5: Int = 0
    v5 = GraphsHost.keep(StringBuilder())
    var v6: String = ""
    v6 = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:y=\"http://www.yworks.com/xml/graphml\">\n"
    GraphsHost.sb(v5).append(v6)
    var v7: String = ""
    v7 = "<key for=\"node\" id=\"d0\" yfiles.type=\"nodegraphics\"/>\n<key for=\"edge\" id=\"d2\" yfiles.type=\"edgegraphics\"/>\n<graph edgedefault=\"directed\" id=\"G\">\n"
    GraphsHost.sb(v5).append(v7)
    var v8: US20 = US20_Handle(0)
    v8 = US20_Handle(v5)
    sp_graphml_members_39(v8, v3)
    var v9: Int = 0
    v9 = 0
    var v10: Int = 0
    v10 = sp_method49(v5, v4, v9)
    var v11: String = ""
    v11 = "</graph>\n</graphml>\n"
    GraphsHost.sb(v5).append(v11)
    var v12: String = ""
    v12 = GraphsHost.sb(v5).toString()
    return v12
}
fun sp_layout_module_52(p0: US8): String {
    var v0: US8 = p0
    when (v0.tag) {
        1 -> {
            var v3: Int = (v0 as US8_CompactOrthogonal).f0
            var v4: Int = (v0 as US8_CompactOrthogonal).f1
            var v5: String = ""
            v5 = "y.B.o"
            return v5
        }
        0 -> {
            var v1: US9 = (v0 as US8_Hierarchic).f0
            var v2: String = ""
            v2 = "y.B.J"
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_layout_settings_53(p0: US8): UH21 {
    var v0: US8 = p0
    when (v0.tag) {
        1 -> {
            var v15: Int = (v0 as US8_CompactOrthogonal).f0
            var v16: Int = (v0 as US8_CompactOrthogonal).f1
            var v17: String = ""
            v17 = sp_tenths_text_44(v15)
            var v18: String = ""
            v18 = (v16).toString()
            var v19: String = ""
            v19 = "USE_VIEW_ASPECT_RATIO"
            var v20: String = ""
            v20 = "false"
            var v21: String = ""
            v21 = "ASPECT_RATIO"
            var v22: String = ""
            v22 = "GRID"
            var v23: UH21 = UH21_Nil
            v23 = UH21_Nil
            var v24: UH21 = UH21_Nil
            v24 = UH21_Cons(v22, v18, v23)
            var v25: UH21 = UH21_Nil
            v25 = UH21_Cons(v21, v17, v24)
            return UH21_Cons(v19, v20, v25)
        }
        0 -> {
            var v1: US9 = (v0 as US8_Hierarchic).f0
            var v5: String = ""
            when (v1.tag) {
                1 -> {
                    var v3: String = ""
                    v3 = "LEFT_TO_RIGHT"
                    v5 = v3
                }
                0 -> {
                    var v2: String = ""
                    v2 = "TOP_TO_BOTTOM"
                    v5 = v2
                }
                else -> throw RuntimeException()
            }
            var v6: String = ""
            v6 = "ORIENTATION"
            var v7: String = ""
            v7 = "EDGE_LABELING"
            var v8: String = ""
            v8 = "EDGE_LABELING_GENERIC"
            var v9: String = ""
            v9 = "COMPONENT_ARRANGEMENT_POLICY"
            var v10: String = ""
            v10 = "POLICY_COMPACT"
            var v11: UH21 = UH21_Nil
            v11 = UH21_Nil
            var v12: UH21 = UH21_Nil
            v12 = UH21_Cons(v9, v10, v11)
            var v13: UH21 = UH21_Nil
            v13 = UH21_Cons(v7, v8, v12)
            return UH21_Cons(v6, v5, v13)
        }
        else -> throw RuntimeException()
    }
}
fun sp_method54(p0: Int, p1: UH21) {
    var v0: Int = p0
    var v1: UH21 = p1
    when (v1.tag) {
        1 -> {
            var v2: String = (v1 as UH21_Cons).f0
            var v3: String = (v1 as UH21_Cons).f1
            var v4: UH21 = (v1 as UH21_Cons).f2
            GraphsHost.set(GraphsHost.mo(v0), v2, v3)
            sp_method54(v0, v4)
        }
        0 -> {
        }
        else -> throw RuntimeException()
    }
}
fun sp_lay_out_37(p0: US8, p1: US18): US19 {
    var v0: US8 = p0
    var v1: US18 = p1
    when (v1.tag) {
        0 -> {
            var v2: String = (v1 as US18_Modeled).f0
            var v3: String = (v1 as US18_Modeled).f1
            var v4: UH10 = (v1 as US18_Modeled).f2
            var v5: UH15 = (v1 as US18_Modeled).f3
            var v6: UH17 = (v1 as US18_Modeled).f4
            var v7: String = ""
            v7 = sp_graphml_text_38(v2, v3, v4, v5, v6)
            var v8: Int = 0
            v8 = GraphsHost.keep(GraphsHost.open(v7))
            var v9: String = ""
            v9 = sp_layout_module_52(v0)
            var v10: Int = 0
            v10 = GraphsHost.keep(GraphsHost.module(v9))
            var v11: UH21 = UH21_Nil
            v11 = sp_layout_settings_53(v0)
            sp_method54(v10, v11)
            var v12: Int = 0
            v12 = GraphsHost.run(GraphsHost.mo(v10), GraphsHost.gr(v8))
            var v13: Boolean = false
            v13 = (v12 == 0)
            if (v13) {
            } else {
                var v14: String = ""
                v14 = (v12).toString()
                var v15: String = ""
                v15 = "layout status "
                var v16: String = ""
                v16 = v15 + v14 
                println(v16)
            }
            var v17: US20 = US20_Handle(0)
            v17 = US20_Handle(v8)
            return US19_LaidOut(v2, v3, v4, v5, v6, v17)
        }
        else -> throw RuntimeException()
    }
}
fun sp_draw_legend_57(p0: US20, p1: Int, p2: UH10) {
    var v0: US20 = p0
    var v1: Int = p1
    var v2: UH10 = p2
    when (v2.tag) {
        1 -> {
            var v3: US11 = (v2 as UH10_Cons).f0
            var v4: UH10 = (v2 as UH10_Cons).f1
            when (v3.tag) {
                1 -> {
                    var v19: String = (v3 as US11_Stroke).f0
                    var v20: String = (v3 as US11_Stroke).f1
                    var v21: US13 = (v3 as US11_Stroke).f2
                    var v22: Int = (v3 as US11_Stroke).f3
                    var v23: US14 = (v3 as US11_Stroke).f4
                    var v24: Int = 0
                    v24 = (v1 + 70)
                    var v25: String = ""
                    v25 = sp_stroke_name_43(v21)
                    var v27: Int = 0
                    when (v0.tag) {
                        0 -> {
                            var v26: Int = (v0 as US20_Handle).f0
                            v27 = v26
                        }
                        else -> throw RuntimeException()
                    }
                    GraphsHost.line(GraphsHost.cv(v27), v1, 147, v24, 147, v20, v22, v25)
                    var v28: Int = 0
                    v28 = (v1 + 77)
                    var v30: Int = 0
                    when (v0.tag) {
                        0 -> {
                            var v29: Int = (v0 as US20_Handle).f0
                            v30 = v29
                        }
                        else -> throw RuntimeException()
                    }
                    var v31: String = ""
                    v31 = "#222222"
                    var v32: Int = 0
                    v32 = GraphsHost.text(GraphsHost.cv(v30), v19, v28, 153, 18, false, v31)
                    var v33: Int = 0
                    v33 = (v1 + 98)
                    var v34: Int = 0
                    v34 = (v33 + v32)
                    sp_draw_legend_57(v0, v34, v4)
                }
                0 -> {
                    var v5: String = (v3 as US11_Swatch).f0
                    var v6: US12 = (v3 as US11_Swatch).f1
                    var v7: String = (v3 as US11_Swatch).f2
                    var v8: String = (v3 as US11_Swatch).f3
                    var v9: US13 = (v3 as US11_Swatch).f4
                    var v10: Int = (v3 as US11_Swatch).f5
                    var v11: String = (v3 as US11_Swatch).f6
                    var v12: String = ""
                    v12 = sp_shape_name_47(v6)
                    var v13: String = ""
                    v13 = sp_stroke_name_43(v9)
                    var v15: Int = 0
                    when (v0.tag) {
                        0 -> {
                            var v14: Int = (v0 as US20_Handle).f0
                            v15 = v14
                        }
                        else -> throw RuntimeException()
                    }
                    GraphsHost.shape(GraphsHost.cv(v15), v12, v1, 130, 187, 34, v7, v8, v10, v13)
                    var v17: Int = 0
                    when (v0.tag) {
                        0 -> {
                            var v16: Int = (v0 as US20_Handle).f0
                            v17 = v16
                        }
                        else -> throw RuntimeException()
                    }
                    GraphsHost.centered(GraphsHost.cv(v17), v5, v1, 130, 187, 34, 18)
                    var v18: Int = 0
                    v18 = (v1 + 201)
                    sp_draw_legend_57(v0, v18, v4)
                }
                else -> throw RuntimeException()
            }
        }
        0 -> {
        }
        else -> throw RuntimeException()
    }
}
fun sp_compose_png_56(p0: String, p1: String, p2: UH10, p3: UH15, p4: UH17, p5: String, p6: String) {
    var v0: String = p0
    var v1: String = p1
    var v2: UH10 = p2
    var v3: UH15 = p3
    var v4: UH17 = p4
    var v5: String = p5
    var v6: String = p6
    var v7: Int = 0
    v7 = GraphsHost.keep(GraphsHost.canvas(v5, 230))
    var v8: String = ""
    v8 = "#222222"
    GraphsHost.rect(GraphsHost.cv(v7), 0, 0, -1, 70, v8)
    var v9: String = ""
    v9 = "#FFFFFF"
    var v10: Int = 0
    v10 = GraphsHost.text(GraphsHost.cv(v7), v0, 30, 50, 40, true, v9)
    var v11: String = ""
    v11 = "#333333"
    var v12: Int = 0
    v12 = GraphsHost.text(GraphsHost.cv(v7), v1, 30, 105, 24, false, v11)
    var v13: US20 = US20_Handle(0)
    v13 = US20_Handle(v7)
    var v14: Int = 0
    v14 = 30
    sp_draw_legend_57(v13, v14, v2)
    var v15: String = ""
    v15 = "#BBBBBB"
    GraphsHost.rect(GraphsHost.cv(v7), 0, 227, -1, 3, v15)
    GraphsHost.save(GraphsHost.cv(v7), v6)
}
fun sp_mermaid_text_62(p0: String): String {
    var v0: String = p0
    var v1: String = ""
    v1 = "#"
    var v2: String = ""
    v2 = "#35;"
    var v3: String = ""
    v3 = (v0).replace(v1, v2)
    var v4: String = ""
    v4 = "&"
    var v5: String = ""
    v5 = "#amp;"
    var v6: String = ""
    v6 = (v3).replace(v4, v5)
    var v7: String = ""
    v7 = "\""
    var v8: String = ""
    v8 = "#quot;"
    var v9: String = ""
    v9 = (v6).replace(v7, v8)
    var v10: String = ""
    v10 = "<"
    var v11: String = ""
    v11 = "#lt;"
    var v12: String = ""
    v12 = (v9).replace(v10, v11)
    var v13: String = ""
    v13 = ">"
    var v14: String = ""
    v14 = "#gt;"
    var v15: String = ""
    v15 = (v12).replace(v13, v14)
    var v16: String = ""
    v16 = "|"
    var v17: String = ""
    v17 = "#124;"
    var v18: String = ""
    v18 = (v15).replace(v16, v17)
    return v18
}
fun sp_method61(p0: UH5, p1: String): String {
    var v0: UH5 = p0
    var v1: String = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH5_Cons).f0
            var v3: UH5 = (v0 as UH5_Cons).f1
            var v4: String = ""
            v4 = "<br/>"
            var v5: String = ""
            v5 = v1 + v4 
            var v6: String = ""
            v6 = sp_mermaid_text_62(v2)
            var v7: String = ""
            v7 = v5 + v6 
            return sp_method61(v3, v7)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_mermaid_brackets_63(p0: US12): SpiralTuple4 {
    var v0: US12 = p0
    when (v0.tag) {
        5 -> {
            var v11: String = ""
            v11 = "((\""
            var v12: String = ""
            v12 = "\"))"
            return SpiralTuple4(v11, v12)
        }
        2 -> {
            var v5: String = ""
            v5 = "{{\""
            var v6: String = ""
            v6 = "\"}}"
            return SpiralTuple4(v5, v6)
        }
        3 -> {
            var v7: String = ""
            v7 = "{{\""
            var v8: String = ""
            v8 = "\"}}"
            return SpiralTuple4(v7, v8)
        }
        4 -> {
            var v9: String = ""
            v9 = "[/\""
            var v10: String = ""
            v10 = "\"/]"
            return SpiralTuple4(v9, v10)
        }
        0 -> {
            var v1: String = ""
            v1 = "[\""
            var v2: String = ""
            v2 = "\"]"
            return SpiralTuple4(v1, v2)
        }
        1 -> {
            var v3: String = ""
            v3 = "(\""
            var v4: String = ""
            v4 = "\")"
            return SpiralTuple4(v3, v4)
        }
        else -> throw RuntimeException()
    }
}
fun sp_mermaid_id_64(p0: String): String {
    var v0: String = p0
    var v1: String = ""
    v1 = "-"
    var v2: String = ""
    v2 = "_"
    var v3: String = ""
    v3 = (v0).replace(v1, v2)
    var v4: String = ""
    v4 = "."
    var v5: String = ""
    v5 = (v3).replace(v4, v2)
    var v6: String = ""
    v6 = ":"
    var v7: String = ""
    v7 = (v5).replace(v6, v2)
    var v8: String = ""
    v8 = "n_"
    var v9: String = ""
    v9 = v8 + v7 
    return v9
}
fun sp_mermaid_class_65(p0: US12, p1: String, p2: String, p3: US13, p4: Int, p5: String): String {
    var v0: US12 = p0
    var v1: String = p1
    var v2: String = p2
    var v3: US13 = p3
    var v4: Int = p4
    var v5: String = p5
    var v6: String = ""
    v6 = "#"
    var v7: String = ""
    v7 = ""
    var v8: String = ""
    v8 = (v1).replace(v6, v7)
    var v9: String = ""
    v9 = "c_"
    var v10: String = ""
    v10 = v9 + v8 
    var v11: String = ""
    v11 = "_"
    var v12: String = ""
    v12 = v10 + v11 
    var v13: String = ""
    v13 = (v2).replace(v6, v7)
    var v14: String = ""
    v14 = v12 + v13 
    return v14
}
fun sp_mermaid_member_60(p0: US20, p1: String, p2: UH16) {
    var v0: US20 = p0
    var v1: String = p1
    var v2: UH16 = p2
    when (v2.tag) {
        0 -> {
            var v3: String = (v2 as UH16_Leaf).f0
            var v4: String = (v2 as UH16_Leaf).f1
            var v5: UH5 = (v2 as UH16_Leaf).f2
            var v6: US12 = (v2 as UH16_Leaf).f3
            var v7: String = (v2 as UH16_Leaf).f4
            var v8: String = (v2 as UH16_Leaf).f5
            var v9: US13 = (v2 as UH16_Leaf).f6
            var v10: Int = (v2 as UH16_Leaf).f7
            var v11: String = (v2 as UH16_Leaf).f8
            var v12: Int = (v2 as UH16_Leaf).f9
            var v13: Int = (v2 as UH16_Leaf).f10
            var v14: Int = (v2 as UH16_Leaf).f11
            var v15: Boolean = (v2 as UH16_Leaf).f12
            var v16: String = ""
            v16 = ""
            var v17: String = ""
            v17 = sp_method61(v5, v16)
            var v18: String = ""
            var v19: String = ""
            val spiral_tuple995 = sp_mermaid_brackets_63(v6)
            v18 = spiral_tuple995.v0
            v19 = spiral_tuple995.v1
            var v20: String = ""
            v20 = sp_mermaid_id_64(v3)
            var v21: String = ""
            v21 = v1 + v20 
            var v22: String = ""
            v22 = v21 + v18 
            var v23: String = ""
            v23 = "<b>"
            var v24: String = ""
            v24 = v22 + v23 
            var v25: String = ""
            v25 = sp_mermaid_text_62(v4)
            var v26: String = ""
            v26 = v24 + v25 
            var v27: String = ""
            v27 = "</b>"
            var v28: String = ""
            v28 = v26 + v27 
            var v29: String = ""
            v29 = v28 + v17 
            var v30: String = ""
            v30 = v29 + v19 
            var v31: String = ""
            v31 = ":::"
            var v32: String = ""
            v32 = v30 + v31 
            var v33: String = ""
            v33 = sp_mermaid_class_65(v6, v7, v8, v9, v10, v11)
            var v34: String = ""
            v34 = v32 + v33 
            var v35: String = ""
            v35 = "\n"
            var v36: String = ""
            v36 = v34 + v35 
            var v38: Int = 0
            when (v0.tag) {
                0 -> {
                    var v37: Int = (v0 as US20_Handle).f0
                    v38 = v37
                }
                else -> throw RuntimeException()
            }
            GraphsHost.sb(v38).append(v36)
        }
        1 -> {
            var v39: String = (v2 as UH16_Nest).f0
            var v40: String = (v2 as UH16_Nest).f1
            var v41: String = (v2 as UH16_Nest).f2
            var v42: String = (v2 as UH16_Nest).f3
            var v43: Boolean = (v2 as UH16_Nest).f4
            var v44: Int = (v2 as UH16_Nest).f5
            var v45: UH15 = (v2 as UH16_Nest).f6
            var v46: String = ""
            v46 = "subgraph g_"
            var v47: String = ""
            v47 = v1 + v46 
            var v48: String = ""
            v48 = sp_mermaid_id_64(v39)
            var v49: String = ""
            v49 = v47 + v48 
            var v50: String = ""
            v50 = "[\""
            var v51: String = ""
            v51 = v49 + v50 
            var v52: String = ""
            v52 = sp_mermaid_text_62(v40)
            var v53: String = ""
            v53 = v51 + v52 
            var v54: String = ""
            v54 = "\"]\n"
            var v55: String = ""
            v55 = v53 + v54 
            var v57: Int = 0
            when (v0.tag) {
                0 -> {
                    var v56: Int = (v0 as US20_Handle).f0
                    v57 = v56
                }
                else -> throw RuntimeException()
            }
            GraphsHost.sb(v57).append(v55)
            var v58: String = ""
            v58 = "  "
            var v59: String = ""
            v59 = v1 + v58 
            sp_mermaid_members_59(v0, v59, v45)
            var v60: String = ""
            v60 = "end\n"
            var v61: String = ""
            v61 = v1 + v60 
            var v63: Int = 0
            when (v0.tag) {
                0 -> {
                    var v62: Int = (v0 as US20_Handle).f0
                    v63 = v62
                }
                else -> throw RuntimeException()
            }
            GraphsHost.sb(v63).append(v61)
        }
        else -> throw RuntimeException()
    }
}
fun sp_mermaid_members_59(p0: US20, p1: String, p2: UH15) {
    var v0: US20 = p0
    var v1: String = p1
    var v2: UH15 = p2
    when (v2.tag) {
        1 -> {
            var v3: UH16 = (v2 as UH15_Cons).f0
            var v4: UH15 = (v2 as UH15_Cons).f1
            sp_mermaid_member_60(v0, v1, v3)
            sp_mermaid_members_59(v0, v1, v4)
        }
        0 -> {
        }
        else -> throw RuntimeException()
    }
}
fun sp_arrow_of_67(p0: String, p1: US13, p2: Int, p3: US14): String {
    var v0: String = p0
    var v1: US13 = p1
    var v2: Int = p2
    var v3: US14 = p3
    when (v1.tag) {
        3 -> {
            var v10: String = ""
            v10 = "-.->"
            return v10
        }
        1 -> {
            var v8: String = ""
            v8 = "-.->"
            return v8
        }
        2 -> {
            var v9: String = ""
            v9 = "-.->"
            return v9
        }
        0 -> {
            var v4: Boolean = false
            v4 = (v2 >= 25)
            if (v4) {
                var v5: String = ""
                v5 = "==>"
                return v5
            } else {
                var v6: String = ""
                v6 = "-->"
                return v6
            }
        }
        else -> throw RuntimeException()
    }
}
fun sp_method66(p0: Int, p1: UH17) {
    var v0: Int = p0
    var v1: UH17 = p1
    when (v1.tag) {
        1 -> {
            var v2: String = (v1 as UH17_Cons).f0
            var v3: String = (v1 as UH17_Cons).f1
            var v4: String = (v1 as UH17_Cons).f2
            var v5: String = (v1 as UH17_Cons).f3
            var v6: US13 = (v1 as UH17_Cons).f4
            var v7: Int = (v1 as UH17_Cons).f5
            var v8: US14 = (v1 as UH17_Cons).f6
            var v9: UH17 = (v1 as UH17_Cons).f7
            var v10: String = ""
            v10 = ""
            var v11: Boolean = false
            v11 = (v4 == v10)
            var v17: String = ""
            if (v11) {
                v17 = v10
            } else {
                var v12: String = ""
                v12 = sp_mermaid_text_62(v4)
                var v13: String = ""
                v13 = "|\""
                var v14: String = ""
                v14 = v13 + v12 
                var v15: String = ""
                v15 = "\"|"
                var v16: String = ""
                v16 = v14 + v15 
                v17 = v16
            }
            var v18: String = ""
            v18 = sp_mermaid_id_64(v2)
            var v19: String = ""
            v19 = "  "
            var v20: String = ""
            v20 = v19 + v18 
            var v21: String = ""
            v21 = " "
            var v22: String = ""
            v22 = v20 + v21 
            var v23: String = ""
            v23 = sp_arrow_of_67(v5, v6, v7, v8)
            var v24: String = ""
            v24 = v22 + v23 
            var v25: String = ""
            v25 = v24 + v17 
            var v26: String = ""
            v26 = v25 + v21 
            var v27: String = ""
            v27 = sp_mermaid_id_64(v3)
            var v28: String = ""
            v28 = v26 + v27 
            var v29: String = ""
            v29 = "\n"
            var v30: String = ""
            v30 = v28 + v29 
            GraphsHost.sb(v0).append(v30)
            sp_method66(v0, v9)
        }
        0 -> {
        }
        else -> throw RuntimeException()
    }
}
fun sp_method68(p0: Int, p1: UH17, p2: Int): Int {
    var v0: Int = p0
    var v1: UH17 = p1
    var v2: Int = p2
    when (v1.tag) {
        1 -> {
            var v3: String = (v1 as UH17_Cons).f0
            var v4: String = (v1 as UH17_Cons).f1
            var v5: String = (v1 as UH17_Cons).f2
            var v6: String = (v1 as UH17_Cons).f3
            var v7: US13 = (v1 as UH17_Cons).f4
            var v8: Int = (v1 as UH17_Cons).f5
            var v9: US14 = (v1 as UH17_Cons).f6
            var v10: UH17 = (v1 as UH17_Cons).f7
            var v11: String = ""
            v11 = (v2).toString()
            var v12: String = ""
            v12 = "  linkStyle "
            var v13: String = ""
            v13 = v12 + v11 
            var v14: String = ""
            v14 = " stroke:"
            var v15: String = ""
            v15 = v13 + v14 
            var v16: String = ""
            v16 = v15 + v6 
            var v17: String = ""
            v17 = ",stroke-width:"
            var v18: String = ""
            v18 = v16 + v17 
            var v19: String = ""
            v19 = sp_tenths_text_44(v8)
            var v20: String = ""
            v20 = v18 + v19 
            var v21: String = ""
            v21 = "px\n"
            var v22: String = ""
            v22 = v20 + v21 
            GraphsHost.sb(v0).append(v22)
            var v23: Int = 0
            v23 = (v2 + 1)
            return sp_method68(v0, v10, v23)
        }
        0 -> {
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_method70(p0: String, p1: String, p2: UH5, p3: US12, p4: String, p5: String, p6: US13, p7: Int, p8: String, p9: Int, p10: Int, p11: Int, p12: Boolean, p13: UH22, p14: Boolean): Boolean {
    var v0: String = p0
    var v1: String = p1
    var v2: UH5 = p2
    var v3: US12 = p3
    var v4: String = p4
    var v5: String = p5
    var v6: US13 = p6
    var v7: Int = p7
    var v8: String = p8
    var v9: Int = p9
    var v10: Int = p10
    var v11: Int = p11
    var v12: Boolean = p12
    var v13: UH22 = p13
    var v14: Boolean = p14
    when (v13.tag) {
        1 -> {
            var v15: US12 = (v13 as UH22_Cons).f0
            var v16: String = (v13 as UH22_Cons).f1
            var v17: String = (v13 as UH22_Cons).f2
            var v18: US13 = (v13 as UH22_Cons).f3
            var v19: Int = (v13 as UH22_Cons).f4
            var v20: String = (v13 as UH22_Cons).f5
            var v21: UH22 = (v13 as UH22_Cons).f6
            var v25: Boolean = false
            if (v14) {
                v25 = true
            } else {
                var v22: String = ""
                v22 = sp_mermaid_class_65(v15, v16, v17, v18, v19, v20)
                var v23: String = ""
                v23 = sp_mermaid_class_65(v3, v4, v5, v6, v7, v8)
                var v24: Boolean = false
                v24 = (v22 == v23)
                v25 = v24
            }
            return sp_method70(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v21, v25)
        }
        0 -> {
            return v14
        }
        else -> throw RuntimeException()
    }
}
fun sp_styles_of_69(p0: UH15, p1: UH22): UH22 {
    var v0: UH15 = p0
    var v1: UH22 = p1
    when (v0.tag) {
        1 -> {
            var v2: UH16 = (v0 as UH15_Cons).f0
            var v3: UH15 = (v0 as UH15_Cons).f1
            when (v2.tag) {
                0 -> {
                    var v4: String = (v2 as UH16_Leaf).f0
                    var v5: String = (v2 as UH16_Leaf).f1
                    var v6: UH5 = (v2 as UH16_Leaf).f2
                    var v7: US12 = (v2 as UH16_Leaf).f3
                    var v8: String = (v2 as UH16_Leaf).f4
                    var v9: String = (v2 as UH16_Leaf).f5
                    var v10: US13 = (v2 as UH16_Leaf).f6
                    var v11: Int = (v2 as UH16_Leaf).f7
                    var v12: String = (v2 as UH16_Leaf).f8
                    var v13: Int = (v2 as UH16_Leaf).f9
                    var v14: Int = (v2 as UH16_Leaf).f10
                    var v15: Int = (v2 as UH16_Leaf).f11
                    var v16: Boolean = (v2 as UH16_Leaf).f12
                    var v17: Boolean = false
                    v17 = false
                    var v18: Boolean = false
                    v18 = sp_method70(v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v1, v17)
                    var v20: UH22 = UH22_Nil
                    if (v18) {
                        v20 = v1
                    } else {
                        v20 = UH22_Cons(v7, v8, v9, v10, v11, v12, v1)
                    }
                    return sp_styles_of_69(v3, v20)
                }
                1 -> {
                    var v22: String = (v2 as UH16_Nest).f0
                    var v23: String = (v2 as UH16_Nest).f1
                    var v24: String = (v2 as UH16_Nest).f2
                    var v25: String = (v2 as UH16_Nest).f3
                    var v26: Boolean = (v2 as UH16_Nest).f4
                    var v27: Int = (v2 as UH16_Nest).f5
                    var v28: UH15 = (v2 as UH16_Nest).f6
                    var v29: UH22 = UH22_Nil
                    v29 = sp_styles_of_69(v28, v1)
                    return sp_styles_of_69(v3, v29)
                }
                else -> throw RuntimeException()
            }
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_method71(p0: UH22, p1: UH22): UH22 {
    var v0: UH22 = p0
    var v1: UH22 = p1
    when (v0.tag) {
        1 -> {
            var v2: US12 = (v0 as UH22_Cons).f0
            var v3: String = (v0 as UH22_Cons).f1
            var v4: String = (v0 as UH22_Cons).f2
            var v5: US13 = (v0 as UH22_Cons).f3
            var v6: Int = (v0 as UH22_Cons).f4
            var v7: String = (v0 as UH22_Cons).f5
            var v8: UH22 = (v0 as UH22_Cons).f6
            var v9: UH22 = UH22_Nil
            v9 = UH22_Cons(v2, v3, v4, v5, v6, v7, v1)
            return sp_method71(v8, v9)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_method72(p0: Int, p1: UH22) {
    var v0: Int = p0
    var v1: UH22 = p1
    when (v1.tag) {
        1 -> {
            var v2: US12 = (v1 as UH22_Cons).f0
            var v3: String = (v1 as UH22_Cons).f1
            var v4: String = (v1 as UH22_Cons).f2
            var v5: US13 = (v1 as UH22_Cons).f3
            var v6: Int = (v1 as UH22_Cons).f4
            var v7: String = (v1 as UH22_Cons).f5
            var v8: UH22 = (v1 as UH22_Cons).f6
            var v9: String = ""
            v9 = sp_mermaid_class_65(v2, v3, v4, v5, v6, v7)
            var v10: String = ""
            v10 = "  classDef "
            var v11: String = ""
            v11 = v10 + v9 
            var v12: String = ""
            v12 = " fill:"
            var v13: String = ""
            v13 = v11 + v12 
            var v14: String = ""
            v14 = v13 + v3 
            var v15: String = ""
            v15 = ",stroke:"
            var v16: String = ""
            v16 = v14 + v15 
            var v17: String = ""
            v17 = v16 + v4 
            var v18: String = ""
            v18 = ",color:"
            var v19: String = ""
            v19 = v17 + v18 
            var v20: String = ""
            v20 = v19 + v7 
            var v21: String = ""
            v21 = "\n"
            var v22: String = ""
            v22 = v20 + v21 
            GraphsHost.sb(v0).append(v22)
            sp_method72(v0, v8)
        }
        0 -> {
        }
        else -> throw RuntimeException()
    }
}
fun sp_mermaid_diagram_58(p0: String, p1: String, p2: UH10, p3: UH15, p4: UH17): String {
    var v0: String = p0
    var v1: String = p1
    var v2: UH10 = p2
    var v3: UH15 = p3
    var v4: UH17 = p4
    var v5: Int = 0
    v5 = GraphsHost.keep(StringBuilder())
    var v6: String = ""
    v6 = "flowchart LR\n"
    GraphsHost.sb(v5).append(v6)
    var v7: US20 = US20_Handle(0)
    v7 = US20_Handle(v5)
    var v8: String = ""
    v8 = "  "
    sp_mermaid_members_59(v7, v8, v3)
    sp_method66(v5, v4)
    var v9: Int = 0
    v9 = 0
    var v10: Int = 0
    v10 = sp_method68(v5, v4, v9)
    var v11: UH22 = UH22_Nil
    v11 = UH22_Nil
    var v12: UH22 = UH22_Nil
    v12 = sp_styles_of_69(v3, v11)
    var v13: UH22 = UH22_Nil
    v13 = UH22_Nil
    var v14: UH22 = UH22_Nil
    v14 = sp_method71(v12, v13)
    sp_method72(v5, v14)
    var v15: String = ""
    v15 = GraphsHost.sb(v5).toString()
    return v15
}
fun sp_publish_55(p0: US19, p1: String) {
    var v0: US19 = p0
    var v1: String = p1
    var v2: String = ""
    v2 = ".graphml"
    var v3: String = ""
    v3 = v1 + v2 
    when (v0.tag) {
        1 -> {
            var v4: String = (v0 as US19_LaidOut).f0
            var v5: String = (v0 as US19_LaidOut).f1
            var v6: UH10 = (v0 as US19_LaidOut).f2
            var v7: UH15 = (v0 as US19_LaidOut).f3
            var v8: UH17 = (v0 as US19_LaidOut).f4
            var v9: US20 = (v0 as US19_LaidOut).f5
            var v11: Int = 0
            when (v9.tag) {
                0 -> {
                    var v10: Int = (v9 as US20_Handle).f0
                    v11 = v10
                }
                else -> throw RuntimeException()
            }
            GraphsHost.writeGraphml(GraphsHost.gr(v11), v3)
        }
        else -> throw RuntimeException()
    }
    var v12: String = ""
    v12 = ".svg"
    var v13: String = ""
    v13 = v1 + v12 
    when (v0.tag) {
        1 -> {
            var v14: String = (v0 as US19_LaidOut).f0
            var v15: String = (v0 as US19_LaidOut).f1
            var v16: UH10 = (v0 as US19_LaidOut).f2
            var v17: UH15 = (v0 as US19_LaidOut).f3
            var v18: UH17 = (v0 as US19_LaidOut).f4
            var v19: US20 = (v0 as US19_LaidOut).f5
            var v21: Int = 0
            when (v19.tag) {
                0 -> {
                    var v20: Int = (v19 as US20_Handle).f0
                    v21 = v20
                }
                else -> throw RuntimeException()
            }
            GraphsHost.writeSvg(GraphsHost.gr(v21), v13)
        }
        else -> throw RuntimeException()
    }
    var v22: String = ""
    v22 = ".png"
    var v23: String = ""
    v23 = v1 + v22 
    when (v0.tag) {
        1 -> {
            var v24: String = (v0 as US19_LaidOut).f0
            var v25: String = (v0 as US19_LaidOut).f1
            var v26: UH10 = (v0 as US19_LaidOut).f2
            var v27: UH15 = (v0 as US19_LaidOut).f3
            var v28: UH17 = (v0 as US19_LaidOut).f4
            var v29: US20 = (v0 as US19_LaidOut).f5
            var v30: String = ""
            v30 = ".raw.png"
            var v31: String = ""
            v31 = v23 + v30 
            var v33: Int = 0
            when (v29.tag) {
                0 -> {
                    var v32: Int = (v29 as US20_Handle).f0
                    v33 = v32
                }
                else -> throw RuntimeException()
            }
            GraphsHost.writePng(GraphsHost.gr(v33), v31, (100).toLong() * 1000000L)
            sp_compose_png_56(v24, v25, v26, v27, v28, v31, v23)
            java.io.File(v31).delete()
        }
        else -> throw RuntimeException()
    }
    var v34: String = ""
    v34 = ".mmd"
    var v35: String = ""
    v35 = v1 + v34 
    when (v0.tag) {
        1 -> {
            var v36: String = (v0 as US19_LaidOut).f0
            var v37: String = (v0 as US19_LaidOut).f1
            var v38: UH10 = (v0 as US19_LaidOut).f2
            var v39: UH15 = (v0 as US19_LaidOut).f3
            var v40: UH17 = (v0 as US19_LaidOut).f4
            var v41: US20 = (v0 as US19_LaidOut).f5
            var v42: String = ""
            v42 = sp_mermaid_diagram_58(v36, v37, v38, v39, v40)
            java.io.File(v35).writeText(v42, Charsets.UTF_8)
        }
        else -> throw RuntimeException()
    }
}
fun sp_relations_74(): UH23 {
    var v0: US45 = US45_NSvMain
    v0 = US45_NSvMain
    var v1: US24 = US24_PInfra(US25_NHashcons)
    v1 = US24_PServer(v0)
    var v2: US45 = US45_NSvMain
    v2 = US45_NSvNew
    var v3: US24 = US24_PInfra(US25_NHashcons)
    v3 = US24_PServer(v2)
    var v4: US47 = US47_Flow
    v4 = US47_Flow
    var v5: String = ""
    v5 = "starts"
    var v6: US45 = US45_NSvMain
    v6 = US45_NSvNew
    var v7: US24 = US24_PInfra(US25_NHashcons)
    v7 = US24_PServer(v6)
    var v8: US45 = US45_NSvMain
    v8 = US45_NSvSuper
    var v9: US24 = US24_PInfra(US25_NHashcons)
    v9 = US24_PServer(v8)
    var v10: US47 = US47_Flow
    v10 = US47_Flow
    var v11: String = ""
    v11 = ""
    var v12: US45 = US45_NSvMain
    v12 = US45_NSvHub
    var v13: US24 = US24_PInfra(US25_NHashcons)
    v13 = US24_PServer(v12)
    var v14: US45 = US45_NSvMain
    v14 = US45_NSvSuper
    var v15: US24 = US24_PInfra(US25_NHashcons)
    v15 = US24_PServer(v14)
    var v16: US47 = US47_Flow
    v16 = US47_Flow
    var v17: String = ""
    v17 = "client requests"
    var v18: US45 = US45_NSvMain
    v18 = US45_NSvSuper
    var v19: US24 = US24_PInfra(US25_NHashcons)
    v19 = US24_PServer(v18)
    var v20: US27 = US27_NSpiproj
    v20 = US27_NLoader
    var v21: US24 = US24_PInfra(US25_NHashcons)
    v21 = US24_PProj(v20)
    var v22: US47 = US47_Flow
    v22 = US47_Flow
    var v23: String = ""
    v23 = "project load"
    var v24: US27 = US27_NSpiproj
    v24 = US27_NLoader
    var v25: US24 = US24_PInfra(US25_NHashcons)
    v25 = US24_PProj(v24)
    var v26: US27 = US27_NSpiproj
    v26 = US27_NSpiproj
    var v27: US24 = US24_PInfra(US25_NHashcons)
    v27 = US24_PProj(v26)
    var v28: US47 = US47_Flow
    v28 = US47_Flow
    var v29: String = ""
    v29 = "schema"
    var v30: US27 = US27_NSpiproj
    v30 = US27_NLoader
    var v31: US24 = US24_PInfra(US25_NHashcons)
    v31 = US24_PProj(v30)
    var v32: US27 = US27_NSpiproj
    v32 = US27_NGraph
    var v33: US24 = US24_PInfra(US25_NHashcons)
    v33 = US24_PProj(v32)
    var v34: US47 = US47_Flow
    v34 = US47_Flow
    var v35: String = ""
    v35 = "package order"
    var v36: US27 = US27_NSpiproj
    v36 = US27_NLoader
    var v37: US24 = US24_PInfra(US25_NHashcons)
    v37 = US24_PProj(v36)
    var v38: US27 = US27_NSpiproj
    v38 = US27_NWdiff
    var v39: US24 = US24_PInfra(US25_NHashcons)
    v39 = US24_PProj(v38)
    var v40: US47 = US47_Flow
    v40 = US47_Flow
    var v41: String = ""
    v41 = "dirty nodes"
    var v42: US27 = US27_NSpiproj
    v42 = US27_NWdiff
    var v43: US24 = US24_PInfra(US25_NHashcons)
    v43 = US24_PProj(v42)
    var v44: US26 = US26_NStartup
    v44 = US26_NTokenize
    var v45: US24 = US24_PInfra(US25_NHashcons)
    v45 = US24_PFront(v44)
    var v46: US47 = US47_Flow
    v46 = US47_Flow
    var v47: US26 = US26_NStartup
    v47 = US26_NTokenize
    var v48: US24 = US24_PInfra(US25_NHashcons)
    v48 = US24_PFront(v47)
    var v49: US26 = US26_NStartup
    v49 = US26_NBlocksplit
    var v50: US24 = US24_PInfra(US25_NHashcons)
    v50 = US24_PFront(v49)
    var v51: US47 = US47_Flow
    v51 = US47_Flow
    var v52: US26 = US26_NStartup
    v52 = US26_NBlocksplit
    var v53: US24 = US24_PInfra(US25_NHashcons)
    v53 = US24_PFront(v52)
    var v54: US26 = US26_NStartup
    v54 = US26_NBlockparse
    var v55: US24 = US24_PInfra(US25_NHashcons)
    v55 = US24_PFront(v54)
    var v56: US47 = US47_Flow
    v56 = US47_Flow
    var v57: US26 = US26_NStartup
    v57 = US26_NBlockparse
    var v58: US24 = US24_PInfra(US25_NHashcons)
    v58 = US24_PFront(v57)
    var v59: US26 = US26_NStartup
    v59 = US26_NBundle
    var v60: US24 = US24_PInfra(US25_NHashcons)
    v60 = US24_PFront(v59)
    var v61: US47 = US47_Flow
    v61 = US47_Flow
    var v62: US26 = US26_NStartup
    v62 = US26_NBundle
    var v63: US24 = US24_PInfra(US25_NHashcons)
    v63 = US24_PFront(v62)
    var v64: US28 = US28_NInfer
    v64 = US28_NInfer
    var v65: US24 = US24_PInfra(US25_NHashcons)
    v65 = US24_PTyping(v64)
    var v66: US47 = US47_Flow
    v66 = US47_Flow
    var v67: String = ""
    v67 = "typecheck"
    var v68: US27 = US27_NSpiproj
    v68 = US27_NWdiff
    var v69: US24 = US24_PInfra(US25_NHashcons)
    v69 = US24_PProj(v68)
    var v70: US27 = US27_NSpiproj
    v70 = US27_NWdiffprep
    var v71: US24 = US24_PInfra(US25_NHashcons)
    v71 = US24_PProj(v70)
    var v72: US47 = US47_Flow
    v72 = US47_Flow
    var v73: US27 = US27_NSpiproj
    v73 = US27_NWdiffprep
    var v74: US24 = US24_PInfra(US25_NHashcons)
    v74 = US24_PProj(v73)
    var v75: US29 = US29_NPrepass
    v75 = US29_NPrepass
    var v76: US24 = US24_PInfra(US25_NHashcons)
    v76 = US24_PPrep(v75)
    var v77: US47 = US47_Flow
    v77 = US47_Flow
    var v78: US28 = US28_NInfer
    v78 = US28_NInfer
    var v79: US24 = US24_PInfra(US25_NHashcons)
    v79 = US24_PTyping(v78)
    var v80: US29 = US29_NPrepass
    v80 = US29_NPrepass
    var v81: US24 = US24_PInfra(US25_NHashcons)
    v81 = US24_PPrep(v80)
    var v82: US47 = US47_Flow
    v82 = US47_Flow
    var v83: String = ""
    v83 = "FilledTop"
    var v84: US45 = US45_NSvMain
    v84 = US45_NSvSuper
    var v85: US24 = US24_PInfra(US25_NHashcons)
    v85 = US24_PServer(v84)
    var v86: US45 = US45_NSvMain
    v86 = US45_NSvXcompat
    var v87: US24 = US24_PInfra(US25_NHashcons)
    v87 = US24_PServer(v86)
    var v88: US47 = US47_Flow
    v88 = US47_Flow
    var v89: US45 = US45_NSvMain
    v89 = US45_NSvSuper
    var v90: US24 = US24_PInfra(US25_NHashcons)
    v90 = US24_PServer(v89)
    var v91: US45 = US45_NSvMain
    v91 = US45_NSvBuild
    var v92: US24 = US24_PInfra(US25_NHashcons)
    v92 = US24_PServer(v91)
    var v93: US47 = US47_Flow
    v93 = US47_Flow
    var v94: String = ""
    v94 = "BuildFile"
    var v95: US45 = US45_NSvMain
    v95 = US45_NSvBuild
    var v96: US24 = US24_PInfra(US25_NHashcons)
    v96 = US24_PServer(v95)
    var v97: US45 = US45_NSvMain
    v97 = US45_NSvMany
    var v98: US24 = US24_PInfra(US25_NHashcons)
    v98 = US24_PServer(v97)
    var v99: US47 = US47_Flow
    v99 = US47_Flow
    var v100: US45 = US45_NSvMain
    v100 = US45_NSvMany
    var v101: US24 = US24_PInfra(US25_NHashcons)
    v101 = US24_PServer(v100)
    var v102: US34 = US34_NAdapter
    v102 = US34_NSRestart
    var v103: US24 = US24_PInfra(US25_NHashcons)
    v103 = US24_PSeams(v102)
    var v104: US47 = US47_Flow
    v104 = US47_Call
    var v105: String = ""
    v105 = "peval under restart"
    var v106: US34 = US34_NAdapter
    v106 = US34_NSRestart
    var v107: US24 = US24_PInfra(US25_NHashcons)
    v107 = US24_PSeams(v106)
    var v108: US30 = US30_NPeval
    v108 = US30_NPeval
    var v109: US24 = US24_PInfra(US25_NHashcons)
    v109 = US24_PPe(v108)
    var v110: US47 = US47_Flow
    v110 = US47_Flow
    var v111: String = ""
    v111 = "inline_restart"
    var v112: US30 = US30_NPeval
    v112 = US30_NPeval
    var v113: US24 = US24_PInfra(US25_NHashcons)
    v113 = US24_PPe(v112)
    var v114: US30 = US30_NPeval
    v114 = US30_NResult
    var v115: US24 = US24_PInfra(US25_NHashcons)
    v115 = US24_PPe(v114)
    var v116: US47 = US47_Flow
    v116 = US47_Flow
    var v117: String = ""
    v117 = "returns"
    var v118: US30 = US30_NPeval
    v118 = US30_NResult
    var v119: US24 = US24_PInfra(US25_NHashcons)
    v119 = US24_PPe(v118)
    var v120: US43 = US43_NCgAdapter
    v120 = US43_NCgAdapter
    var v121: US24 = US24_PInfra(US25_NHashcons)
    v121 = US24_PCodegen(v120)
    var v122: US47 = US47_Flow
    v122 = US47_Flow
    var v123: String = ""
    v123 = "tables"
    var v124: US45 = US45_NSvMain
    v124 = US45_NSvMany
    var v125: US24 = US24_PInfra(US25_NHashcons)
    v125 = US24_PServer(v124)
    var v126: US43 = US43_NCgAdapter
    v126 = US43_NCgDead
    var v127: US24 = US24_PInfra(US25_NHashcons)
    v127 = US24_PCodegen(v126)
    var v128: US47 = US47_Flow
    v128 = US47_Flow
    var v129: US43 = US43_NCgAdapter
    v129 = US43_NCgDead
    var v130: US24 = US24_PInfra(US25_NHashcons)
    v130 = US24_PCodegen(v129)
    var v131: US43 = US43_NCgAdapter
    v131 = US43_NCgUnused
    var v132: US24 = US24_PInfra(US25_NHashcons)
    v132 = US24_PCodegen(v131)
    var v133: US47 = US47_Flow
    v133 = US47_Flow
    var v134: US43 = US43_NCgAdapter
    v134 = US43_NCgAdapter
    var v135: US24 = US24_PInfra(US25_NHashcons)
    v135 = US24_PCodegen(v134)
    var v136: US43 = US43_NCgAdapter
    v136 = US43_NCgUtils
    var v137: US24 = US24_PInfra(US25_NHashcons)
    v137 = US24_PCodegen(v136)
    var v138: US47 = US47_Flow
    v138 = US47_Flow
    var v139: US43 = US43_NCgAdapter
    v139 = US43_NCgUtils
    var v140: US24 = US24_PInfra(US25_NHashcons)
    v140 = US24_PCodegen(v139)
    var v141: US44 = US44_NBFsharp
    v141 = US44_NBFsharp
    var v142: US24 = US24_PInfra(US25_NHashcons)
    v142 = US24_PBackends(v141)
    var v143: US47 = US47_Flow
    v143 = US47_Flow
    var v144: US43 = US43_NCgAdapter
    v144 = US43_NCgUtils
    var v145: US24 = US24_PInfra(US25_NHashcons)
    v145 = US24_PCodegen(v144)
    var v146: US44 = US44_NBFsharp
    v146 = US44_NBRust
    var v147: US24 = US24_PInfra(US25_NHashcons)
    v147 = US24_PBackends(v146)
    var v148: US47 = US47_Flow
    v148 = US47_Flow
    var v149: US43 = US43_NCgAdapter
    v149 = US43_NCgUtils
    var v150: US24 = US24_PInfra(US25_NHashcons)
    v150 = US24_PCodegen(v149)
    var v151: US44 = US44_NBFsharp
    v151 = US44_NBDelphi
    var v152: US24 = US24_PInfra(US25_NHashcons)
    v152 = US24_PBackends(v151)
    var v153: US47 = US47_Flow
    v153 = US47_Flow
    var v154: US43 = US43_NCgAdapter
    v154 = US43_NCgUtils
    var v155: US24 = US24_PInfra(US25_NHashcons)
    v155 = US24_PCodegen(v154)
    var v156: US44 = US44_NBFsharp
    v156 = US44_NBZig
    var v157: US24 = US24_PInfra(US25_NHashcons)
    v157 = US24_PBackends(v156)
    var v158: US47 = US47_Flow
    v158 = US47_Flow
    var v159: US43 = US43_NCgAdapter
    v159 = US43_NCgUtils
    var v160: US24 = US24_PInfra(US25_NHashcons)
    v160 = US24_PCodegen(v159)
    var v161: US44 = US44_NBFsharp
    v161 = US44_NBLean
    var v162: US24 = US24_PInfra(US25_NHashcons)
    v162 = US24_PBackends(v161)
    var v163: US47 = US47_Flow
    v163 = US47_Flow
    var v164: US43 = US43_NCgAdapter
    v164 = US43_NCgUtils
    var v165: US24 = US24_PInfra(US25_NHashcons)
    v165 = US24_PCodegen(v164)
    var v166: US44 = US44_NBFsharp
    v166 = US44_NBBend
    var v167: US24 = US24_PInfra(US25_NHashcons)
    v167 = US24_PBackends(v166)
    var v168: US47 = US47_Flow
    v168 = US47_Flow
    var v169: US43 = US43_NCgAdapter
    v169 = US43_NCgUtils
    var v170: US24 = US24_PInfra(US25_NHashcons)
    v170 = US24_PCodegen(v169)
    var v171: US44 = US44_NBFsharp
    v171 = US44_NBGleam
    var v172: US24 = US24_PInfra(US25_NHashcons)
    v172 = US24_PBackends(v171)
    var v173: US47 = US47_Flow
    v173 = US47_Flow
    var v174: US43 = US43_NCgAdapter
    v174 = US43_NCgUtils
    var v175: US24 = US24_PInfra(US25_NHashcons)
    v175 = US24_PCodegen(v174)
    var v176: US44 = US44_NBFsharp
    v176 = US44_NBLua
    var v177: US24 = US24_PInfra(US25_NHashcons)
    v177 = US24_PBackends(v176)
    var v178: US47 = US47_Flow
    v178 = US47_Flow
    var v179: US43 = US43_NCgAdapter
    v179 = US43_NCgUtils
    var v180: US24 = US24_PInfra(US25_NHashcons)
    v180 = US24_PCodegen(v179)
    var v181: US44 = US44_NBFsharp
    v181 = US44_NBWasm
    var v182: US24 = US24_PInfra(US25_NHashcons)
    v182 = US24_PBackends(v181)
    var v183: US47 = US47_Flow
    v183 = US47_Flow
    var v184: US43 = US43_NCgAdapter
    v184 = US43_NCgUtils
    var v185: US24 = US24_PInfra(US25_NHashcons)
    v185 = US24_PCodegen(v184)
    var v186: US44 = US44_NBFsharp
    v186 = US44_NBWgsl
    var v187: US24 = US24_PInfra(US25_NHashcons)
    v187 = US24_PBackends(v186)
    var v188: US47 = US47_Flow
    v188 = US47_Flow
    var v189: US43 = US43_NCgAdapter
    v189 = US43_NCgUtils
    var v190: US24 = US24_PInfra(US25_NHashcons)
    v190 = US24_PCodegen(v189)
    var v191: US44 = US44_NBFsharp
    v191 = US44_NBCalyx
    var v192: US24 = US24_PInfra(US25_NHashcons)
    v192 = US24_PBackends(v191)
    var v193: US47 = US47_Flow
    v193 = US47_Flow
    var v194: US43 = US43_NCgAdapter
    v194 = US43_NCgUtils
    var v195: US24 = US24_PInfra(US25_NHashcons)
    v195 = US24_PCodegen(v194)
    var v196: US44 = US44_NBFsharp
    v196 = US44_NBKotlin
    var v197: US24 = US24_PInfra(US25_NHashcons)
    v197 = US24_PBackends(v196)
    var v198: US47 = US47_Flow
    v198 = US47_Flow
    var v199: US43 = US43_NCgAdapter
    v199 = US43_NCgUtils
    var v200: US24 = US24_PInfra(US25_NHashcons)
    v200 = US24_PCodegen(v199)
    var v201: US44 = US44_NBFsharp
    v201 = US44_NBSwift
    var v202: US24 = US24_PInfra(US25_NHashcons)
    v202 = US24_PBackends(v201)
    var v203: US47 = US47_Flow
    v203 = US47_Flow
    var v204: US43 = US43_NCgAdapter
    v204 = US43_NCgUtils
    var v205: US24 = US24_PInfra(US25_NHashcons)
    v205 = US24_PCodegen(v204)
    var v206: US44 = US44_NBFsharp
    v206 = US44_NBQasm
    var v207: US24 = US24_PInfra(US25_NHashcons)
    v207 = US24_PBackends(v206)
    var v208: US47 = US47_Flow
    v208 = US47_Flow
    var v209: US43 = US43_NCgAdapter
    v209 = US43_NCgUtils
    var v210: US24 = US24_PInfra(US25_NHashcons)
    v210 = US24_PCodegen(v209)
    var v211: US44 = US44_NBFsharp
    v211 = US44_NBEbpf
    var v212: US24 = US24_PInfra(US25_NHashcons)
    v212 = US24_PBackends(v211)
    var v213: US47 = US47_Flow
    v213 = US47_Flow
    var v214: US43 = US43_NCgAdapter
    v214 = US43_NCgUtils
    var v215: US24 = US24_PInfra(US25_NHashcons)
    v215 = US24_PCodegen(v214)
    var v216: US44 = US44_NBFsharp
    v216 = US44_NBTs
    var v217: US24 = US24_PInfra(US25_NHashcons)
    v217 = US24_PBackends(v216)
    var v218: US47 = US47_Flow
    v218 = US47_Flow
    var v219: US43 = US43_NCgAdapter
    v219 = US43_NCgRefc
    var v220: US24 = US24_PInfra(US25_NHashcons)
    v220 = US24_PCodegen(v219)
    var v221: US44 = US44_NBFsharp
    v221 = US44_NBC
    var v222: US24 = US24_PInfra(US25_NHashcons)
    v222 = US24_PBackends(v221)
    var v223: US47 = US47_Flow
    v223 = US47_Flow
    var v224: US43 = US43_NCgAdapter
    v224 = US43_NCgUtils
    var v225: US24 = US24_PInfra(US25_NHashcons)
    v225 = US24_PCodegen(v224)
    var v226: US44 = US44_NBFsharp
    v226 = US44_NBCpp
    var v227: US24 = US24_PInfra(US25_NHashcons)
    v227 = US24_PBackends(v226)
    var v228: US47 = US47_Flow
    v228 = US47_Flow
    var v229: US43 = US43_NCgAdapter
    v229 = US43_NCgUtils
    var v230: US24 = US24_PInfra(US25_NHashcons)
    v230 = US24_PCodegen(v229)
    var v231: US44 = US44_NBFsharp
    v231 = US44_NBPy
    var v232: US24 = US24_PInfra(US25_NHashcons)
    v232 = US24_PBackends(v231)
    var v233: US47 = US47_Flow
    v233 = US47_Flow
    var v234: US45 = US45_NSvMain
    v234 = US45_NSvMany
    var v235: US24 = US24_PInfra(US25_NHashcons)
    v235 = US24_PServer(v234)
    var v236: US45 = US45_NSvMain
    v236 = US45_NSvOut
    var v237: US24 = US24_PInfra(US25_NHashcons)
    v237 = US24_PServer(v236)
    var v238: US47 = US47_Flow
    v238 = US47_Flow
    var v239: String = ""
    v239 = "generated files"
    var v240: US26 = US26_NStartup
    v240 = US26_NStartup
    var v241: US24 = US24_PInfra(US25_NHashcons)
    v241 = US24_PFront(v240)
    var v242: US45 = US45_NSvMain
    v242 = US45_NSvMain
    var v243: US24 = US24_PInfra(US25_NHashcons)
    v243 = US24_PServer(v242)
    var v244: US47 = US47_Flow
    v244 = US47_Flow
    var v245: String = ""
    v245 = "CLI args"
    var v246: US45 = US45_NSvMain
    v246 = US45_NSvMany
    var v247: US24 = US24_PInfra(US25_NHashcons)
    v247 = US24_PServer(v246)
    var v248: US33 = US33_NRunaway
    v248 = US33_NRustgap
    var v249: US24 = US24_PInfra(US25_NHashcons)
    v249 = US24_PPeGuards(v248)
    var v250: US47 = US47_Flow
    v250 = US47_DataFlow
    var v251: String = ""
    v251 = "missing arm check"
    var v252: US26 = US26_NStartup
    v252 = US26_NFormatter
    var v253: US24 = US24_PInfra(US25_NHashcons)
    v253 = US24_PFront(v252)
    var v254: US26 = US26_NStartup
    v254 = US26_NTokenize
    var v255: US24 = US24_PInfra(US25_NHashcons)
    v255 = US24_PFront(v254)
    var v256: US47 = US47_Flow
    v256 = US47_DataFlow
    var v257: String = ""
    v257 = "tokens"
    var v258: US25 = US25_NHashcons
    v258 = US25_NParsec
    var v259: US24 = US24_PInfra(US25_NHashcons)
    v259 = US24_PInfra(v258)
    var v260: US26 = US26_NStartup
    v260 = US26_NBlockparse
    var v261: US24 = US24_PInfra(US25_NHashcons)
    v261 = US24_PFront(v260)
    var v262: US47 = US47_Flow
    v262 = US47_DataFlow
    var v263: US25 = US25_NHashcons
    v263 = US25_NPvec
    var v264: US24 = US24_PInfra(US25_NHashcons)
    v264 = US24_PInfra(v263)
    var v265: US27 = US27_NSpiproj
    v265 = US27_NWdiff
    var v266: US24 = US24_PInfra(US25_NHashcons)
    v266 = US24_PProj(v265)
    var v267: US47 = US47_Flow
    v267 = US47_DataFlow
    var v268: US25 = US25_NHashcons
    v268 = US25_NHopacext
    var v269: US24 = US24_PInfra(US25_NHashcons)
    v269 = US24_PInfra(v268)
    var v270: US27 = US27_NSpiproj
    v270 = US27_NWdiff
    var v271: US24 = US24_PInfra(US25_NHashcons)
    v271 = US24_PProj(v270)
    var v272: US47 = US47_Flow
    v272 = US47_DataFlow
    var v273: String = ""
    v273 = "promises/streams"
    var v274: US25 = US25_NHashcons
    v274 = US25_NHopacext
    var v275: US24 = US24_PInfra(US25_NHashcons)
    v275 = US24_PInfra(v274)
    var v276: US28 = US28_NInfer
    v276 = US28_NInfer
    var v277: US24 = US24_PInfra(US25_NHashcons)
    v277 = US24_PTyping(v276)
    var v278: US47 = US47_Flow
    v278 = US47_DataFlow
    var v279: String = ""
    v279 = "FilledTop promise"
    var v280: US25 = US25_NHashcons
    v280 = US25_NHopaclib
    var v281: US24 = US24_PInfra(US25_NHashcons)
    v281 = US24_PInfra(v280)
    var v282: US25 = US25_NHashcons
    v282 = US25_NHopacext
    var v283: US24 = US24_PInfra(US25_NHashcons)
    v283 = US24_PInfra(v282)
    var v284: US47 = US47_Flow
    v284 = US47_DataFlow
    var v285: US25 = US25_NHashcons
    v285 = US25_NHopaclib
    var v286: US24 = US24_PInfra(US25_NHashcons)
    v286 = US24_PInfra(v285)
    var v287: US37 = US37_NEEngine
    v287 = US37_NEControl
    var v288: US24 = US24_PInfra(US25_NHashcons)
    v288 = US24_PHsEngine(v287)
    var v289: US47 = US47_Flow
    v289 = US47_DataFlow
    var v290: String = ""
    v290 = "Scheduler"
    var v291: US25 = US25_NHashcons
    v291 = US25_NHopaclib
    var v292: US24 = US24_PInfra(US25_NHashcons)
    v292 = US24_PInfra(v291)
    var v293: US38 = US38_NTTable
    v293 = US38_NTGuard
    var v294: US24 = US24_PInfra(US25_NHashcons)
    v294 = US24_PHsTable(v293)
    var v295: US47 = US47_Flow
    v295 = US47_DataFlow
    var v296: String = ""
    v296 = "MVar"
    var v297: US25 = US25_NHashcons
    v297 = US25_NHopaclib
    var v298: US24 = US24_PInfra(US25_NHashcons)
    v298 = US24_PInfra(v297)
    var v299: US39 = US39_NGGraph
    v299 = US39_NGCh
    var v300: US24 = US24_PInfra(US25_NHashcons)
    v300 = US24_PHsGraph(v299)
    var v301: US47 = US47_Flow
    v301 = US47_DataFlow
    var v302: String = ""
    v302 = "Ch"
    var v303: US25 = US25_NHashcons
    v303 = US25_NIdadapter
    var v304: US24 = US24_PInfra(US25_NHashcons)
    v304 = US24_PInfra(v303)
    var v305: US28 = US28_NInfer
    v305 = US28_NInfer
    var v306: US24 = US24_PInfra(US25_NHashcons)
    v306 = US24_PTyping(v305)
    var v307: US47 = US47_Flow
    v307 = US47_DataFlow
    var v308: US28 = US28_NInfer
    v308 = US28_NInferadapter
    var v309: US24 = US24_PInfra(US25_NHashcons)
    v309 = US24_PTyping(v308)
    var v310: US28 = US28_NInfer
    v310 = US28_NInfer
    var v311: US24 = US24_PInfra(US25_NHashcons)
    v311 = US24_PTyping(v310)
    var v312: US47 = US47_Flow
    v312 = US47_SeamLink
    var v313: US29 = US29_NPrepass
    v313 = US29_NPrepassadapter
    var v314: US24 = US24_PInfra(US25_NHashcons)
    v314 = US24_PPrep(v313)
    var v315: US29 = US29_NPrepass
    v315 = US29_NPrepass
    var v316: US24 = US24_PInfra(US25_NHashcons)
    v316 = US24_PPrep(v315)
    var v317: US47 = US47_Flow
    v317 = US47_SeamLink
    var v318: US28 = US28_NInfer
    v318 = US28_NInlinedepth
    var v319: US24 = US24_PInfra(US25_NHashcons)
    v319 = US24_PTyping(v318)
    var v320: US28 = US28_NInfer
    v320 = US28_NInfer
    var v321: US24 = US24_PInfra(US25_NHashcons)
    v321 = US24_PTyping(v320)
    var v322: US47 = US47_Flow
    v322 = US47_DataFlow
    var v323: US28 = US28_NInfer
    v323 = US28_NTypeerr
    var v324: US24 = US24_PInfra(US25_NHashcons)
    v324 = US24_PTyping(v323)
    var v325: US28 = US28_NInfer
    v325 = US28_NInfer
    var v326: US24 = US24_PInfra(US25_NHashcons)
    v326 = US24_PTyping(v325)
    var v327: US47 = US47_Flow
    v327 = US47_DataFlow
    var v328: US29 = US29_NPrepass
    v328 = US29_NPrepenv
    var v329: US24 = US24_PInfra(US25_NHashcons)
    v329 = US24_PPrep(v328)
    var v330: US29 = US29_NPrepass
    v330 = US29_NPrepass
    var v331: US24 = US24_PInfra(US25_NHashcons)
    v331 = US24_PPrep(v330)
    var v332: US47 = US47_Flow
    v332 = US47_DataFlow
    var v333: US25 = US25_NHashcons
    v333 = US25_NConsed
    var v334: US24 = US24_PInfra(US25_NHashcons)
    v334 = US24_PInfra(v333)
    var v335: US25 = US25_NHashcons
    v335 = US25_NHashcons
    var v336: US24 = US24_PInfra(US25_NHashcons)
    v336 = US24_PInfra(v335)
    var v337: US47 = US47_Flow
    v337 = US47_DataFlow
    var v338: US25 = US25_NHashcons
    v338 = US25_NStriped
    var v339: US24 = US24_PInfra(US25_NHashcons)
    v339 = US24_PInfra(v338)
    var v340: US25 = US25_NHashcons
    v340 = US25_NHashcons
    var v341: US24 = US24_PInfra(US25_NHashcons)
    v341 = US24_PInfra(v340)
    var v342: US47 = US47_Flow
    v342 = US47_Reuse
    var v343: String = ""
    v343 = "32 x HashConsTable"
    var v344: US30 = US30_NPeval
    v344 = US30_NPeval
    var v345: US24 = US24_PInfra(US25_NHashcons)
    v345 = US24_PPe(v344)
    var v346: US30 = US30_NPeval
    v346 = US30_NTopenv
    var v347: US24 = US24_PInfra(US25_NHashcons)
    v347 = US24_PPe(v346)
    var v348: US47 = US47_Flow
    v348 = US47_DataFlow
    var v349: US30 = US30_NPeval
    v349 = US30_NPeval
    var v350: US24 = US24_PInfra(US25_NHashcons)
    v350 = US24_PPe(v349)
    var v351: US30 = US30_NPeval
    v351 = US30_NLangenv
    var v352: US24 = US24_PInfra(US25_NHashcons)
    v352 = US24_PPe(v351)
    var v353: US47 = US47_Flow
    v353 = US47_DataFlow
    var v354: US30 = US30_NPeval
    v354 = US30_NPeval
    var v355: US24 = US24_PInfra(US25_NHashcons)
    v355 = US24_PPe(v354)
    var v356: US31 = US31_NJpm
    v356 = US31_NJpm
    var v357: US24 = US24_PInfra(US25_NHashcons)
    v357 = US24_PPeTables(v356)
    var v358: US47 = US47_Flow
    v358 = US47_DataFlow
    var v359: US30 = US30_NPeval
    v359 = US30_NPeval
    var v360: US24 = US24_PInfra(US25_NHashcons)
    v360 = US24_PPe(v359)
    var v361: US31 = US31_NJpm
    v361 = US31_NJpc
    var v362: US24 = US24_PInfra(US25_NHashcons)
    v362 = US24_PPeTables(v361)
    var v363: US47 = US47_Flow
    v363 = US47_DataFlow
    var v364: US30 = US30_NPeval
    v364 = US30_NPeval
    var v365: US24 = US24_PInfra(US25_NHashcons)
    v365 = US24_PPe(v364)
    var v366: US31 = US31_NJpm
    v366 = US31_NJpt
    var v367: US24 = US24_PInfra(US25_NHashcons)
    v367 = US24_PPeTables(v366)
    var v368: US47 = US47_Flow
    v368 = US47_DataFlow
    var v369: US30 = US30_NPeval
    v369 = US30_NPeval
    var v370: US24 = US24_PInfra(US25_NHashcons)
    v370 = US24_PPe(v369)
    var v371: US31 = US31_NJpm
    v371 = US31_NBsv
    var v372: US24 = US24_PInfra(US25_NHashcons)
    v372 = US24_PPeTables(v371)
    var v373: US47 = US47_Flow
    v373 = US47_DataFlow
    var v374: US30 = US30_NPeval
    v374 = US30_NPeval
    var v375: US24 = US24_PInfra(US25_NHashcons)
    v375 = US24_PPe(v374)
    var v376: US34 = US34_NAdapter
    v376 = US34_NSStart
    var v377: US24 = US24_PInfra(US25_NHashcons)
    v377 = US24_PSeams(v376)
    var v378: US47 = US47_Flow
    v378 = US47_Call
    var v379: String = ""
    v379 = "start_for env.inline_restart"
    var v380: US30 = US30_NPeval
    v380 = US30_NPeval
    var v381: US24 = US24_PInfra(US25_NHashcons)
    v381 = US24_PPe(v380)
    var v382: US34 = US34_NAdapter
    v382 = US34_NAdapter
    var v383: US24 = US24_PInfra(US25_NHashcons)
    v383 = US24_PSeams(v382)
    var v384: US47 = US47_Flow
    v384 = US47_DataFlow
    var v385: String = ""
    v385 = "builds the adapter"
    var v386: US34 = US34_NAdapter
    v386 = US34_NAdapter
    var v387: US24 = US24_PInfra(US25_NHashcons)
    v387 = US24_PSeams(v386)
    var v388: US33 = US33_NRunaway
    v388 = US33_NDynif
    var v389: US24 = US24_PInfra(US25_NHashcons)
    v389 = US24_PPeGuards(v388)
    var v390: US47 = US47_Flow
    v390 = US47_DataFlow
    var v391: String = ""
    v391 = "dynamic_if / set"
    var v392: US34 = US34_NAdapter
    v392 = US34_NAdapter
    var v393: US24 = US24_PInfra(US25_NHashcons)
    v393 = US24_PSeams(v392)
    var v394: US33 = US33_NRunaway
    v394 = US33_NRustgap
    var v395: US24 = US24_PInfra(US25_NHashcons)
    v395 = US24_PPeGuards(v394)
    var v396: US47 = US47_Flow
    v396 = US47_DataFlow
    var v397: String = ""
    v397 = "validating / set"
    var v398: US34 = US34_NAdapter
    v398 = US34_NAdapter
    var v399: US24 = US24_PInfra(US25_NHashcons)
    v399 = US24_PSeams(v398)
    var v400: US33 = US33_NRunaway
    v400 = US33_NGindex
    var v401: US24 = US24_PInfra(US25_NHashcons)
    v401 = US24_PPeGuards(v400)
    var v402: US47 = US47_Flow
    v402 = US47_DataFlow
    var v403: String = ""
    v403 = "rebuild_index"
    var v404: US34 = US34_NAdapter
    v404 = US34_NAdapter
    var v405: US24 = US24_PInfra(US25_NHashcons)
    v405 = US24_PSeams(v404)
    var v406: US30 = US30_NPeval
    v406 = US30_NLangenv
    var v407: US24 = US24_PInfra(US25_NHashcons)
    v407 = US24_PPe(v406)
    var v408: US47 = US47_Flow
    v408 = US47_DataFlow
    var v409: String = ""
    v409 = "globals / with_globals"
    var v410: US30 = US30_NPeval
    v410 = US30_NPeval
    var v411: US24 = US24_PInfra(US25_NHashcons)
    v411 = US24_PPe(v410)
    var v412: US32 = US32_NClosure
    v412 = US32_NMainroot
    var v413: US24 = US24_PInfra(US25_NHashcons)
    v413 = US24_PPeOps(v412)
    var v414: US47 = US47_Flow
    v414 = US47_Call
    var v415: US32 = US32_NClosure
    v415 = US32_NMainroot
    var v416: US24 = US24_PInfra(US25_NHashcons)
    v416 = US24_PPeOps(v415)
    var v417: US34 = US34_NAdapter
    v417 = US34_NSRoot
    var v418: US24 = US24_PInfra(US25_NHashcons)
    v418 = US24_PSeams(v417)
    var v419: US47 = US47_Flow
    v419 = US47_Call
    var v420: US32 = US32_NClosure
    v420 = US32_NMainroot
    var v421: US24 = US24_PInfra(US25_NHashcons)
    v421 = US24_PPeOps(v420)
    var v422: US30 = US30_NPeval
    v422 = US30_NTermscope
    var v423: US24 = US24_PInfra(US25_NHashcons)
    v423 = US24_PPe(v422)
    var v424: US47 = US47_Flow
    v424 = US47_Call
    var v425: String = ""
    v425 = "main ()"
    var v426: US30 = US30_NPeval
    v426 = US30_NTermscope
    var v427: US24 = US24_PInfra(US25_NHashcons)
    v427 = US24_PPe(v426)
    var v428: US32 = US32_NClosure
    v428 = US32_NClosure
    var v429: US24 = US24_PInfra(US25_NHashcons)
    v429 = US24_PPeOps(v428)
    var v430: US47 = US47_Flow
    v430 = US47_Call
    var v431: US30 = US30_NPeval
    v431 = US30_NTermscope
    var v432: US24 = US24_PInfra(US25_NHashcons)
    v432 = US24_PPe(v431)
    var v433: US32 = US32_NClosure
    v433 = US32_NTjp
    var v434: US24 = US24_PInfra(US25_NHashcons)
    v434 = US24_PPeOps(v433)
    var v435: US47 = US47_Flow
    v435 = US47_Call
    var v436: US30 = US30_NPeval
    v436 = US30_NTermscope
    var v437: US24 = US24_PInfra(US25_NHashcons)
    v437 = US24_PPe(v436)
    var v438: US32 = US32_NClosure
    v438 = US32_NMjp
    var v439: US24 = US24_PInfra(US25_NHashcons)
    v439 = US24_PPeOps(v438)
    var v440: US47 = US47_Flow
    v440 = US47_Call
    var v441: US30 = US30_NPeval
    v441 = US30_NTermscope
    var v442: US24 = US24_PInfra(US25_NHashcons)
    v442 = US24_PPe(v441)
    var v443: US32 = US32_NClosure
    v443 = US32_NBswitch
    var v444: US24 = US24_PInfra(US25_NHashcons)
    v444 = US24_PPeOps(v443)
    var v445: US47 = US47_Flow
    v445 = US47_Call
    var v446: US30 = US30_NPeval
    v446 = US30_NTermscope
    var v447: US24 = US24_PInfra(US25_NHashcons)
    v447 = US24_PPe(v446)
    var v448: US32 = US32_NClosure
    v448 = US32_NUninh
    var v449: US24 = US24_PInfra(US25_NHashcons)
    v449 = US24_PPeOps(v448)
    var v450: US47 = US47_Flow
    v450 = US47_Call
    var v451: US30 = US30_NPeval
    v451 = US30_NTermscope
    var v452: US24 = US24_PInfra(US25_NHashcons)
    v452 = US24_PPe(v451)
    var v453: US32 = US32_NClosure
    v453 = US32_NPrintst
    var v454: US24 = US24_PInfra(US25_NHashcons)
    v454 = US24_PPeOps(v453)
    var v455: US47 = US47_Flow
    v455 = US47_Call
    var v456: US30 = US30_NPeval
    v456 = US30_NTermscope
    var v457: US24 = US24_PInfra(US25_NHashcons)
    v457 = US24_PPe(v456)
    var v458: US30 = US30_NPeval
    v458 = US30_NCse
    var v459: US24 = US24_PInfra(US25_NHashcons)
    v459 = US24_PPe(v458)
    var v460: US47 = US47_Flow
    v460 = US47_Call
    var v461: US30 = US30_NPeval
    v461 = US30_NTermscope
    var v462: US24 = US24_PInfra(US25_NHashcons)
    v462 = US24_PPe(v461)
    var v463: US33 = US33_NRunaway
    v463 = US33_NRunaway
    var v464: US24 = US24_PInfra(US25_NHashcons)
    v464 = US24_PPeGuards(v463)
    var v465: US47 = US47_Flow
    v465 = US47_Call
    var v466: String = ""
    v466 = "EJP0041 / unroll"
    var v467: US30 = US30_NPeval
    v467 = US30_NTermscope
    var v468: US24 = US24_PInfra(US25_NHashcons)
    v468 = US24_PPe(v467)
    var v469: US33 = US33_NRunaway
    v469 = US33_NUnionnest
    var v470: US24 = US24_PInfra(US25_NHashcons)
    v470 = US24_PPeGuards(v469)
    var v471: US47 = US47_Flow
    v471 = US47_DataFlow
    var v472: US30 = US30_NPeval
    v472 = US30_NTermscope
    var v473: US24 = US24_PInfra(US25_NHashcons)
    v473 = US24_PPe(v472)
    var v474: US33 = US33_NRunaway
    v474 = US33_NDynif
    var v475: US24 = US24_PInfra(US25_NHashcons)
    v475 = US24_PPeGuards(v474)
    var v476: US47 = US47_Flow
    v476 = US47_DataFlow
    var v477: US30 = US30_NPeval
    v477 = US30_NLangenv
    var v478: US24 = US24_PInfra(US25_NHashcons)
    v478 = US24_PPe(v477)
    var v479: US31 = US31_NJpm
    v479 = US31_NGlobals
    var v480: US24 = US24_PInfra(US25_NHashcons)
    v480 = US24_PPeTables(v479)
    var v481: US47 = US47_Flow
    v481 = US47_DataFlow
    var v482: US33 = US33_NRunaway
    v482 = US33_NGindex
    var v483: US24 = US24_PInfra(US25_NHashcons)
    v483 = US24_PPeGuards(v482)
    var v484: US31 = US31_NJpm
    v484 = US31_NGlobals
    var v485: US24 = US24_PInfra(US25_NHashcons)
    v485 = US24_PPeTables(v484)
    var v486: US47 = US47_Flow
    v486 = US47_DataFlow
    var v487: US31 = US31_NJpm
    v487 = US31_NJpm
    var v488: US24 = US24_PInfra(US25_NHashcons)
    v488 = US24_PPeTables(v487)
    var v489: US25 = US25_NHashcons
    v489 = US25_NHashcons
    var v490: US24 = US24_PInfra(US25_NHashcons)
    v490 = US24_PInfra(v489)
    var v491: US47 = US47_Flow
    v491 = US47_DataFlow
    var v492: String = ""
    v492 = "key interning"
    var v493: US31 = US31_NJpm
    v493 = US31_NJpc
    var v494: US24 = US24_PInfra(US25_NHashcons)
    v494 = US24_PPeTables(v493)
    var v495: US25 = US25_NHashcons
    v495 = US25_NHashcons
    var v496: US24 = US24_PInfra(US25_NHashcons)
    v496 = US24_PInfra(v495)
    var v497: US47 = US47_Flow
    v497 = US47_DataFlow
    var v498: US31 = US31_NJpm
    v498 = US31_NJpt
    var v499: US24 = US24_PInfra(US25_NHashcons)
    v499 = US24_PPeTables(v498)
    var v500: US25 = US25_NHashcons
    v500 = US25_NHashcons
    var v501: US24 = US24_PInfra(US25_NHashcons)
    v501 = US24_PInfra(v500)
    var v502: US47 = US47_Flow
    v502 = US47_DataFlow
    var v503: US31 = US31_NJpm
    v503 = US31_NBsv
    var v504: US24 = US24_PInfra(US25_NHashcons)
    v504 = US24_PPeTables(v503)
    var v505: US25 = US25_NHashcons
    v505 = US25_NHashcons
    var v506: US24 = US24_PInfra(US25_NHashcons)
    v506 = US24_PInfra(v505)
    var v507: US47 = US47_Flow
    v507 = US47_DataFlow
    var v508: US30 = US30_NPeval
    v508 = US30_NResult
    var v509: US24 = US24_PInfra(US25_NHashcons)
    v509 = US24_PPe(v508)
    var v510: US31 = US31_NJpm
    v510 = US31_NJpm
    var v511: US24 = US24_PInfra(US25_NHashcons)
    v511 = US24_PPeTables(v510)
    var v512: US47 = US47_Flow
    v512 = US47_DataFlow
    var v513: US30 = US30_NPeval
    v513 = US30_NResult
    var v514: US24 = US24_PInfra(US25_NHashcons)
    v514 = US24_PPe(v513)
    var v515: US31 = US31_NJpm
    v515 = US31_NJpc
    var v516: US24 = US24_PInfra(US25_NHashcons)
    v516 = US24_PPeTables(v515)
    var v517: US47 = US47_Flow
    v517 = US47_DataFlow
    var v518: US30 = US30_NPeval
    v518 = US30_NResult
    var v519: US24 = US24_PInfra(US25_NHashcons)
    v519 = US24_PPe(v518)
    var v520: US31 = US31_NJpm
    v520 = US31_NGlobals
    var v521: US24 = US24_PInfra(US25_NHashcons)
    v521 = US24_PPeTables(v520)
    var v522: US47 = US47_Flow
    v522 = US47_DataFlow
    var v523: US43 = US43_NCgAdapter
    v523 = US43_NCgAdapter
    var v524: US24 = US24_PInfra(US25_NHashcons)
    v524 = US24_PCodegen(v523)
    var v525: US31 = US31_NJpm
    v525 = US31_NJpm
    var v526: US24 = US24_PInfra(US25_NHashcons)
    v526 = US24_PPeTables(v525)
    var v527: US47 = US47_Flow
    v527 = US47_DataFlow
    var v528: String = ""
    v528 = "codegenMethodBody"
    var v529: US43 = US43_NCgAdapter
    v529 = US43_NCgAdapter
    var v530: US24 = US24_PInfra(US25_NHashcons)
    v530 = US24_PCodegen(v529)
    var v531: US31 = US31_NJpm
    v531 = US31_NJpc
    var v532: US24 = US24_PInfra(US25_NHashcons)
    v532 = US24_PPeTables(v531)
    var v533: US47 = US47_Flow
    v533 = US47_DataFlow
    var v534: String = ""
    v534 = "codegenClosureBody"
    var v535: US32 = US32_NClosure
    v535 = US32_NClosure
    var v536: US24 = US24_PInfra(US25_NHashcons)
    v536 = US24_PPeOps(v535)
    var v537: US34 = US34_NAdapter
    v537 = US34_NSFamily
    var v538: US24 = US24_PInfra(US25_NHashcons)
    v538 = US24_PSeams(v537)
    var v539: US47 = US47_Flow
    v539 = US47_Call
    var v540: String = ""
    v540 = "1"
    var v541: US32 = US32_NClosure
    v541 = US32_NClosure
    var v542: US24 = US24_PInfra(US25_NHashcons)
    v542 = US24_PPeOps(v541)
    var v543: US34 = US34_NAdapter
    v543 = US34_NSClaim
    var v544: US24 = US24_PInfra(US25_NHashcons)
    v544 = US24_PSeams(v543)
    var v545: US47 = US47_Flow
    v545 = US47_Call
    var v546: String = ""
    v546 = "2"
    var v547: US32 = US32_NClosure
    v547 = US32_NClosure
    var v548: US24 = US24_PInfra(US25_NHashcons)
    v548 = US24_PPeOps(v547)
    var v549: US34 = US34_NAdapter
    v549 = US34_NSAnnounce
    var v550: US24 = US24_PInfra(US25_NHashcons)
    v550 = US24_PSeams(v549)
    var v551: US47 = US47_Flow
    v551 = US47_Call
    var v552: String = ""
    v552 = "3 usable"
    var v553: US32 = US32_NClosure
    v553 = US32_NClosure
    var v554: US24 = US24_PInfra(US25_NHashcons)
    v554 = US24_PPeOps(v553)
    var v555: US34 = US34_NAdapter
    v555 = US34_NSKeybody
    var v556: US24 = US24_PInfra(US25_NHashcons)
    v556 = US24_PSeams(v555)
    var v557: US47 = US47_Flow
    v557 = US47_Call
    val v558: String = "4 'closure'"
    val v559: US32 = US32_NClosure
    val v560: US24 = US24_PPeOps(v559)
    val v561: US34 = US34_NSDefer
    val v562: US24 = US24_PSeams(v561)
    val v563: US47 = US47_Call
    val v564: String = "5 Some ()"
    val v565: US32 = US32_NClosure
    val v566: US24 = US24_PPeOps(v565)
    val v567: US34 = US34_NSFill
    val v568: US24 = US24_PSeams(v567)
    val v569: US47 = US47_Call
    val v570: String = "6"
    val v571: US32 = US32_NClosure
    val v572: US24 = US24_PPeOps(v571)
    val v573: US34 = US34_NSKeyhit
    val v574: US24 = US24_PSeams(v573)
    val v575: US47 = US47_Call
    val v576: String = "Found"
    val v577: US32 = US32_NTjp
    val v578: US24 = US24_PPeOps(v577)
    val v579: US34 = US34_NSFamily
    val v580: US24 = US24_PSeams(v579)
    val v581: US47 = US47_Call
    val v582: US32 = US32_NTjp
    val v583: US24 = US24_PPeOps(v582)
    val v584: US34 = US34_NSClaim
    val v585: US24 = US24_PSeams(v584)
    val v586: US47 = US47_Call
    val v587: US32 = US32_NTjp
    val v588: US24 = US24_PPeOps(v587)
    val v589: US34 = US34_NSKeybody
    val v590: US24 = US24_PSeams(v589)
    val v591: US47 = US47_Call
    val v592: String = "3 'type'"
    val v593: US32 = US32_NTjp
    val v594: US24 = US24_PPeOps(v593)
    val v595: US34 = US34_NSFill
    val v596: US24 = US24_PSeams(v595)
    val v597: US47 = US47_Call
    val v598: String = "4"
    val v599: US32 = US32_NTjp
    val v600: US24 = US24_PPeOps(v599)
    val v601: US34 = US34_NSKeyhit
    val v602: US24 = US24_PSeams(v601)
    val v603: US47 = US47_Call
    val v604: US32 = US32_NMjp
    val v605: US24 = US24_PPeOps(v604)
    val v606: US34 = US34_NSFamily
    val v607: US24 = US24_PSeams(v606)
    val v608: US47 = US47_Call
    val v609: US32 = US32_NMjp
    val v610: US24 = US24_PPeOps(v609)
    val v611: US34 = US34_NSClaim
    val v612: US24 = US24_PSeams(v611)
    val v613: US47 = US47_Call
    val v614: US32 = US32_NMjp
    val v615: US24 = US24_PPeOps(v614)
    val v616: US34 = US34_NSAnnounce
    val v617: US24 = US24_PSeams(v616)
    val v618: US47 = US47_Call
    val v619: String = "3 if annotated"
    val v620: US32 = US32_NMjp
    val v621: US24 = US24_PPeOps(v620)
    val v622: US34 = US34_NSKeybody
    val v623: US24 = US24_PSeams(v622)
    val v624: US47 = US47_Call
    val v625: String = "4 'method'"
    val v626: US32 = US32_NMjp
    val v627: US24 = US24_PPeOps(v626)
    val v628: US34 = US34_NSDefer
    val v629: US24 = US24_PSeams(v628)
    val v630: US47 = US47_Call
    val v631: String = "5 annot"
    val v632: US32 = US32_NMjp
    val v633: US24 = US24_PPeOps(v632)
    val v634: US34 = US34_NSFill
    val v635: US24 = US24_PSeams(v634)
    val v636: US47 = US47_Call
    val v637: US32 = US32_NMjp
    val v638: US24 = US24_PPeOps(v637)
    val v639: US34 = US34_NSKeyhit
    val v640: US24 = US24_PSeams(v639)
    val v641: US47 = US47_Call
    val v642: US32 = US32_NBswitch
    val v643: US24 = US24_PPeOps(v642)
    val v644: US34 = US34_NSValidated
    val v645: US24 = US24_PSeams(v644)
    val v646: US47 = US47_Call
    val v647: US32 = US32_NBswitch
    val v648: US24 = US24_PPeOps(v647)
    val v649: US34 = US34_NSRead
    val v650: US24 = US24_PSeams(v649)
    val v651: US47 = US47_Call
    val v652: String = "2 if validated"
    val v653: US32 = US32_NBswitch
    val v654: US24 = US24_PPeOps(v653)
    val v655: US34 = US34_NSKeybody
    val v656: US24 = US24_PSeams(v655)
    val v657: US47 = US47_Call
    val v658: String = "else 'bsv'"
    val v659: US32 = US32_NBswitch
    val v660: US24 = US24_PPeOps(v659)
    val v661: US34 = US34_NSFill
    val v662: US24 = US24_PSeams(v661)
    val v663: US47 = US47_Call
    val v664: US32 = US32_NBswitch
    val v665: US24 = US24_PPeOps(v664)
    val v666: US34 = US34_NSActive
    val v667: US24 = US24_PSeams(v666)
    val v668: US47 = US47_Call
    val v669: String = "globals choice"
    val v670: US32 = US32_NBswitch
    val v671: US24 = US24_PPeOps(v670)
    val v672: US34 = US34_NSKeyhit
    val v673: US24 = US24_PSeams(v672)
    val v674: US47 = US47_Call
    val v675: US32 = US32_NUninh
    val v676: US24 = US24_PPeOps(v675)
    val v677: US34 = US34_NSNesting
    val v678: US24 = US24_PSeams(v677)
    val v679: US47 = US47_Call
    val v680: US32 = US32_NPrintst
    val v681: US24 = US24_PPeOps(v680)
    val v682: US34 = US34_NSPrint
    val v683: US24 = US24_PSeams(v682)
    val v684: US47 = US47_Call
    val v685: US34 = US34_NSClaim
    val v686: US24 = US24_PSeams(v685)
    val v687: US34 = US34_NSClaimty
    val v688: US24 = US24_PSeams(v687)
    val v689: US47 = US47_DataFlow
    val v690: US34 = US34_NSStart
    val v691: US24 = US24_PSeams(v690)
    val v692: US34 = US34_NSSession
    val v693: US24 = US24_PSeams(v692)
    val v694: US47 = US47_DataFlow
    val v695: US34 = US34_NSStart
    val v696: US24 = US24_PSeams(v695)
    val v697: US34 = US34_NAdapter
    val v698: US24 = US24_PSeams(v697)
    val v699: US47 = US47_DataFlow
    val v700: US32 = US32_NClosure
    val v701: US24 = US24_PPeOps(v700)
    val v702: US31 = US31_NJpc
    val v703: US24 = US24_PPeTables(v702)
    val v704: US47 = US47_DataFlow
    val v705: US32 = US32_NTjp
    val v706: US24 = US24_PPeOps(v705)
    val v707: US31 = US31_NJpt
    val v708: US24 = US24_PPeTables(v707)
    val v709: US47 = US47_DataFlow
    val v710: US32 = US32_NMjp
    val v711: US24 = US24_PPeOps(v710)
    val v712: US31 = US31_NJpm
    val v713: US24 = US24_PPeTables(v712)
    val v714: US47 = US47_DataFlow
    val v715: US32 = US32_NBswitch
    val v716: US24 = US24_PPeOps(v715)
    val v717: US31 = US31_NBsv
    val v718: US24 = US24_PPeTables(v717)
    val v719: US47 = US47_DataFlow
    val v720: US34 = US34_NSStart
    val v721: US24 = US24_PSeams(v720)
    val v722: US35 = US35_NSfStart
    val v723: US24 = US24_PSf(v722)
    val v724: US47 = US47_ToSingleFlight
    val v725: US34 = US34_NSSession
    val v726: US24 = US24_PSeams(v725)
    val v727: US35 = US35_NSfSession
    val v728: US24 = US24_PSf(v727)
    val v729: US47 = US47_ToSingleFlight
    val v730: US34 = US34_NSRestart
    val v731: US24 = US24_PSeams(v730)
    val v732: US35 = US35_NSfRestart
    val v733: US24 = US24_PSf(v732)
    val v734: US47 = US47_ToSingleFlight
    val v735: US34 = US34_NSRoot
    val v736: US24 = US24_PSeams(v735)
    val v737: US35 = US35_NSfRoot
    val v738: US24 = US24_PSf(v737)
    val v739: US47 = US47_ToSingleFlight
    val v740: US34 = US34_NSDefer
    val v741: US24 = US24_PSeams(v740)
    val v742: US35 = US35_NSfDefer
    val v743: US24 = US24_PSf(v742)
    val v744: US47 = US47_ToSingleFlight
    val v745: US34 = US34_NSClaim
    val v746: US24 = US24_PSeams(v745)
    val v747: US35 = US35_NSfClaim
    val v748: US24 = US24_PSf(v747)
    val v749: US47 = US47_ToSingleFlight
    val v750: US34 = US34_NSAnnounce
    val v751: US24 = US24_PSeams(v750)
    val v752: US35 = US35_NSfAnnounce
    val v753: US24 = US24_PSf(v752)
    val v754: US47 = US47_ToSingleFlight
    val v755: US34 = US34_NSFill
    val v756: US24 = US24_PSeams(v755)
    val v757: US35 = US35_NSfFill
    val v758: US24 = US24_PSf(v757)
    val v759: US47 = US47_ToSingleFlight
    val v760: US34 = US34_NSFamily
    val v761: US24 = US24_PSeams(v760)
    val v762: US35 = US35_NSfFamily
    val v763: US24 = US24_PSf(v762)
    val v764: US47 = US47_ToSingleFlight
    val v765: US34 = US34_NSKeyhit
    val v766: US24 = US24_PSeams(v765)
    val v767: US35 = US35_NSfKeyhit
    val v768: US24 = US24_PSf(v767)
    val v769: US47 = US47_ToSingleFlight
    val v770: US34 = US34_NSKeybody
    val v771: US24 = US24_PSeams(v770)
    val v772: US35 = US35_NSfKeybody
    val v773: US24 = US24_PSf(v772)
    val v774: US47 = US47_ToSingleFlight
    val v775: US34 = US34_NSPrint
    val v776: US24 = US24_PSeams(v775)
    val v777: US35 = US35_NSfPrint
    val v778: US24 = US24_PSf(v777)
    val v779: US47 = US47_ToSingleFlight
    val v780: US34 = US34_NSNesting
    val v781: US24 = US24_PSeams(v780)
    val v782: US35 = US35_NSfNesting
    val v783: US24 = US24_PSf(v782)
    val v784: US47 = US47_ToSingleFlight
    val v785: US34 = US34_NSValidated
    val v786: US24 = US24_PSeams(v785)
    val v787: US35 = US35_NSfValidated
    val v788: US24 = US24_PSf(v787)
    val v789: US47 = US47_ToSingleFlight
    val v790: US34 = US34_NSRead
    val v791: US24 = US24_PSeams(v790)
    val v792: US35 = US35_NSfRead
    val v793: US24 = US24_PSf(v792)
    val v794: US47 = US47_ToSingleFlight
    val v795: US34 = US34_NSActive
    val v796: US24 = US24_PSeams(v795)
    val v797: US35 = US35_NSfActive
    val v798: US24 = US24_PSf(v797)
    val v799: US47 = US47_ToSingleFlight
    val v800: US35 = US35_NSfFamily
    val v801: US24 = US24_PSf(v800)
    val v802: US25 = US25_NMemoize
    val v803: US24 = US24_PInfra(v802)
    val v804: US47 = US47_Reuse
    val v805: US35 = US35_NSfDefer
    val v806: US24 = US24_PSf(v805)
    val v807: US30 = US30_NTermscope
    val v808: US24 = US24_PPe(v807)
    val v809: US47 = US47_Call
    val v810: String = "evaluates now"
    val v811: US34 = US34_NSStart
    val v812: US24 = US24_PSeams(v811)
    val v813: US36 = US36_NHsStart
    val v814: US24 = US24_PHsSeams(v813)
    val v815: US47 = US47_ToHopacsota
    val v816: US34 = US34_NSSession
    val v817: US24 = US24_PSeams(v816)
    val v818: US36 = US36_NHsSession
    val v819: US24 = US24_PHsSeams(v818)
    val v820: US47 = US47_ToHopacsota
    val v821: US34 = US34_NSRestart
    val v822: US24 = US24_PSeams(v821)
    val v823: US36 = US36_NHsRestart
    val v824: US24 = US24_PHsSeams(v823)
    val v825: US47 = US47_ToHopacsota
    val v826: US34 = US34_NSRoot
    val v827: US24 = US24_PSeams(v826)
    val v828: US36 = US36_NHsRoot
    val v829: US24 = US24_PHsSeams(v828)
    val v830: US47 = US47_ToHopacsota
    val v831: US34 = US34_NSDefer
    val v832: US24 = US24_PSeams(v831)
    val v833: US36 = US36_NHsDefer
    val v834: US24 = US24_PHsSeams(v833)
    val v835: US47 = US47_ToHopacsota
    val v836: US34 = US34_NSKeyhit
    val v837: US24 = US24_PSeams(v836)
    val v838: US36 = US36_NHsKeyhit
    val v839: US24 = US24_PHsSeams(v838)
    val v840: US47 = US47_ToHopacsota
    val v841: US34 = US34_NSKeybody
    val v842: US24 = US24_PSeams(v841)
    val v843: US36 = US36_NHsKeybody
    val v844: US24 = US24_PHsSeams(v843)
    val v845: US47 = US47_ToHopacsota
    val v846: US34 = US34_NSPrint
    val v847: US24 = US24_PSeams(v846)
    val v848: US36 = US36_NHsPrint
    val v849: US24 = US24_PHsSeams(v848)
    val v850: US47 = US47_ToHopacsota
    val v851: US34 = US34_NSClaim
    val v852: US24 = US24_PSeams(v851)
    val v853: US36 = US36_NHsFallback
    val v854: US24 = US24_PHsSeams(v853)
    val v855: US47 = US47_ToHopacsota
    val v856: US34 = US34_NSAnnounce
    val v857: US24 = US24_PSeams(v856)
    val v858: US36 = US36_NHsFallback
    val v859: US24 = US24_PHsSeams(v858)
    val v860: US47 = US47_ToHopacsota
    val v861: US34 = US34_NSFill
    val v862: US24 = US24_PSeams(v861)
    val v863: US36 = US36_NHsFallback
    val v864: US24 = US24_PHsSeams(v863)
    val v865: US47 = US47_ToHopacsota
    val v866: US34 = US34_NSFamily
    val v867: US24 = US24_PSeams(v866)
    val v868: US36 = US36_NHsFallback
    val v869: US24 = US24_PHsSeams(v868)
    val v870: US47 = US47_ToHopacsota
    val v871: US34 = US34_NSNesting
    val v872: US24 = US24_PSeams(v871)
    val v873: US36 = US36_NHsFallback
    val v874: US24 = US24_PHsSeams(v873)
    val v875: US47 = US47_ToHopacsota
    val v876: US34 = US34_NSValidated
    val v877: US24 = US24_PSeams(v876)
    val v878: US36 = US36_NHsFallback
    val v879: US24 = US24_PHsSeams(v878)
    val v880: US47 = US47_ToHopacsota
    val v881: US34 = US34_NSRead
    val v882: US24 = US24_PSeams(v881)
    val v883: US36 = US36_NHsFallback
    val v884: US24 = US24_PHsSeams(v883)
    val v885: US47 = US47_ToHopacsota
    val v886: US34 = US34_NSActive
    val v887: US24 = US24_PSeams(v886)
    val v888: US36 = US36_NHsSession
    val v889: US24 = US24_PHsSeams(v888)
    val v890: US47 = US47_ToHopacsota
    val v891: String = "not null"
    val v892: US46 = US46_NEnvPolicy
    val v893: US24 = US24_PEnv(v892)
    val v894: US36 = US36_NHsPolicy
    val v895: US24 = US24_PHsSeams(v894)
    val v896: US47 = US47_DataFlow
    val v897: US36 = US36_NHsPolicy
    val v898: US24 = US24_PHsSeams(v897)
    val v899: US36 = US36_NHsSession
    val v900: US24 = US24_PHsSeams(v899)
    val v901: US47 = US47_DataFlow
    val v902: US36 = US36_NHsStart
    val v903: US24 = US24_PHsSeams(v902)
    val v904: US36 = US36_NHsSession
    val v905: US24 = US24_PHsSeams(v904)
    val v906: US47 = US47_DataFlow
    val v907: String = "policy Some -> Session"
    val v908: US36 = US36_NHsSession
    val v909: US24 = US24_PHsSeams(v908)
    val v910: US36 = US36_NHsMarks
    val v911: US24 = US24_PHsSeams(v910)
    val v912: US47 = US47_DataFlow
    val v913: String = "own"
    val v914: US36 = US36_NHsSession
    val v915: US24 = US24_PHsSeams(v914)
    val v916: US36 = US36_NHsCensus
    val v917: US24 = US24_PHsSeams(v916)
    val v918: US47 = US47_DataFlow
    val v919: US46 = US46_NEnvCensus
    val v920: US24 = US24_PEnv(v919)
    val v921: US36 = US36_NHsCensus
    val v922: US24 = US24_PHsSeams(v921)
    val v923: US47 = US47_DataFlow
    val v924: US36 = US36_NHsDefer
    val v925: US24 = US24_PHsSeams(v924)
    val v926: US36 = US36_NHsBody
    val v927: US24 = US24_PHsSeams(v926)
    val v928: US47 = US47_DataFlow
    val v929: String = "creates"
    val v930: US36 = US36_NHsDefer
    val v931: US24 = US24_PHsSeams(v930)
    val v932: US36 = US36_NHsMarks
    val v933: US24 = US24_PHsSeams(v932)
    val v934: US47 = US47_DataFlow
    val v935: String = "'globals' marker"
    val v936: US36 = US36_NHsDefer
    val v937: US24 = US24_PHsSeams(v936)
    val v938: US37 = US37_NESpawn
    val v939: US24 = US24_PHsEngine(v938)
    val v940: US47 = US47_Engine
    val v941: String = "Parallel"
    val v942: US36 = US36_NHsDefer
    val v943: US24 = US24_PHsSeams(v942)
    val v944: US36 = US36_NHsDrain
    val v945: US24 = US24_PHsSeams(v944)
    val v946: US47 = US47_DataFlow
    val v947: String = "queued (BFS / Seeded)"
    val v948: US36 = US36_NHsBody
    val v949: US24 = US24_PHsSeams(v948)
    val v950: US34 = US34_NAdapter
    val v951: US24 = US24_PSeams(v950)
    val v952: US47 = US47_DataFlow
    val v953: String = "save / zero / restore"
    val v954: US36 = US36_NHsBody
    val v955: US24 = US24_PHsSeams(v954)
    val v956: US30 = US30_NTermscope
    val v957: US24 = US24_PPe(v956)
    val v958: US47 = US47_Call
    val v959: String = "evaluate body_env"
    val v960: US36 = US36_NHsKeyhit
    val v961: US24 = US24_PHsSeams(v960)
    val v962: US36 = US36_NHsMarks
    val v963: US24 = US24_PHsSeams(v962)
    val v964: US47 = US47_DataFlow
    val v965: US36 = US36_NHsKeybody
    val v966: US24 = US24_PHsSeams(v965)
    val v967: US36 = US36_NHsMarks
    val v968: US24 = US24_PHsSeams(v967)
    val v969: US47 = US47_DataFlow
    val v970: String = "'key-<label>'"
    val v971: US36 = US36_NHsPrint
    val v972: US24 = US24_PHsSeams(v971)
    val v973: US36 = US36_NHsMarks
    val v974: US24 = US24_PHsSeams(v973)
    val v975: US47 = US47_DataFlow
    val v976: String = "'print'"
    val v977: US36 = US36_NHsRoot
    val v978: US24 = US24_PHsSeams(v977)
    val v979: US37 = US37_NEEngine
    val v980: US24 = US24_PHsEngine(v979)
    val v981: US47 = US47_Engine
    val v982: String = "Parallel -> new"
    val v983: US36 = US36_NHsRoot
    val v984: US24 = US24_PHsSeams(v983)
    val v985: US36 = US36_NHsDrain
    val v986: US24 = US24_PHsSeams(v985)
    val v987: US47 = US47_Call
    val v988: US36 = US36_NHsRoot
    val v989: US24 = US24_PHsSeams(v988)
    val v990: US37 = US37_NECheck
    val v991: US24 = US24_PHsEngine(v990)
    val v992: US47 = US47_Engine
    val v993: US36 = US36_NHsRoot
    val v994: US24 = US24_PHsSeams(v993)
    val v995: US36 = US36_NHsFinish
    val v996: US24 = US24_PHsSeams(v995)
    val v997: US47 = US47_Call
    val v998: US36 = US36_NHsRoot
    val v999: US24 = US24_PHsSeams(v998)
    val v1000: US37 = US37_NEClose
    val v1001: US24 = US24_PHsEngine(v1000)
    val v1002: US47 = US47_Engine
    val v1003: US36 = US36_NHsDrain
    val v1004: US24 = US24_PHsSeams(v1003)
    val v1005: US37 = US37_NEDrain
    val v1006: US24 = US24_PHsEngine(v1005)
    val v1007: US47 = US47_Engine
    val v1008: US36 = US36_NHsFinish
    val v1009: US24 = US24_PHsSeams(v1008)
    val v1010: US36 = US36_NHsMarks
    val v1011: US24 = US24_PHsSeams(v1010)
    val v1012: US47 = US47_DataFlow
    val v1013: String = "merge all contexts"
    val v1014: US36 = US36_NHsFinish
    val v1015: US24 = US24_PHsSeams(v1014)
    val v1016: US33 = US33_NGindex
    val v1017: US24 = US24_PPeGuards(v1016)
    val v1018: US47 = US47_DataFlow
    val v1019: US36 = US36_NHsFinish
    val v1020: US24 = US24_PHsSeams(v1019)
    val v1021: US31 = US31_NGlobals
    val v1022: US24 = US24_PPeTables(v1021)
    val v1023: US47 = US47_DataFlow
    val v1024: String = "rewrites in canonical order"
    val v1025: US36 = US36_NHsRestart
    val v1026: US24 = US24_PHsSeams(v1025)
    val v1027: US37 = US37_NEConflict
    val v1028: US24 = US24_PHsEngine(v1027)
    val v1029: US47 = US47_DataFlow
    val v1030: String = "catches"
    val v1031: US36 = US36_NHsRestart
    val v1032: US24 = US24_PHsSeams(v1031)
    val v1033: US30 = US30_NPeval
    val v1034: US24 = US24_PPe(v1033)
    val v1035: US47 = US47_Call
    val v1036: String = "rerun with inline_restart = true"
    val v1037: US36 = US36_NHsStart
    val v1038: US24 = US24_PHsSeams(v1037)
    val v1039: US35 = US35_NSfStart
    val v1040: US24 = US24_PSf(v1039)
    val v1041: US47 = US47_Reuse
    val v1042: String = "inline_restart -> null session = single-flight semantics"
    val v1043: US36 = US36_NHsFallback
    val v1044: US24 = US24_PHsSeams(v1043)
    val v1045: US35 = US35_NSfClaim
    val v1046: US24 = US24_PSf(v1045)
    val v1047: US47 = US47_Reuse
    val v1048: String = "same code when engine = null"
    val v1049: US36 = US36_NHsFallback
    val v1050: US24 = US24_PHsSeams(v1049)
    val v1051: US35 = US35_NSfFamily
    val v1052: US24 = US24_PSf(v1051)
    val v1053: US47 = US47_Reuse
    val v1054: String = "Utils.memoize"
    val v1055: US36 = US36_NHsFallback
    val v1056: US24 = US24_PHsSeams(v1055)
    val v1057: US35 = US35_NSfFill
    val v1058: US24 = US24_PSf(v1057)
    val v1059: US47 = US47_Reuse
    val v1060: US36 = US36_NHsFallback
    val v1061: US24 = US24_PHsSeams(v1060)
    val v1062: US35 = US35_NSfValidated
    val v1063: US24 = US24_PSf(v1062)
    val v1064: US47 = US47_Reuse
    val v1065: US36 = US36_NHsFallback
    val v1066: US24 = US24_PHsSeams(v1065)
    val v1067: US37 = US37_NEClaim
    val v1068: US24 = US24_PHsEngine(v1067)
    val v1069: US47 = US47_Engine
    val v1070: String = "engine.Claim"
    val v1071: US36 = US36_NHsFallback
    val v1072: US24 = US24_PHsSeams(v1071)
    val v1073: US37 = US37_NEFill
    val v1074: US24 = US24_PHsEngine(v1073)
    val v1075: US47 = US47_Engine
    val v1076: String = "engine.Fill"
    val v1077: US36 = US36_NHsFallback
    val v1078: US24 = US24_PHsSeams(v1077)
    val v1079: US37 = US37_NEAnnounce
    val v1080: US24 = US24_PHsEngine(v1079)
    val v1081: US47 = US47_Engine
    val v1082: String = "engine.Announce"
    val v1083: US36 = US36_NHsFallback
    val v1084: US24 = US24_PHsSeams(v1083)
    val v1085: US37 = US37_NEFamily
    val v1086: US24 = US24_PHsEngine(v1085)
    val v1087: US47 = US47_Engine
    val v1088: String = "engine.Family"
    val v1089: US36 = US36_NHsFallback
    val v1090: US24 = US24_PHsSeams(v1089)
    val v1091: US37 = US37_NEValidated
    val v1092: US24 = US24_PHsEngine(v1091)
    val v1093: US47 = US47_Engine
    val v1094: String = "engine.Validated"
    val v1095: US36 = US36_NHsFallback
    val v1096: US24 = US24_PHsSeams(v1095)
    val v1097: US37 = US37_NERead
    val v1098: US24 = US24_PHsEngine(v1097)
    val v1099: US47 = US47_Engine
    val v1100: String = "engine.Read"
    val v1101: US36 = US36_NHsFallback
    val v1102: US24 = US24_PHsSeams(v1101)
    val v1103: US41 = US41_NCCtx
    val v1104: US24 = US24_PHsCtx(v1103)
    val v1105: US47 = US47_Engine
    val v1106: String = "Context.Nesting"
    val v1107: US46 = US46_NEnvWorkers
    val v1108: US24 = US24_PEnv(v1107)
    val v1109: US37 = US37_NEEngine
    val v1110: US24 = US24_PHsEngine(v1109)
    val v1111: US47 = US47_DataFlow
    val v1112: US46 = US46_NEnvGap
    val v1113: US24 = US24_PEnv(v1112)
    val v1114: US37 = US37_NEEngine
    val v1115: US24 = US24_PHsEngine(v1114)
    val v1116: US47 = US47_DataFlow
    val v1117: String = "1 thread"
    val v1118: US46 = US46_NEnvStats
    val v1119: US24 = US24_PEnv(v1118)
    val v1120: US37 = US37_NEClose
    val v1121: US24 = US24_PHsEngine(v1120)
    val v1122: US47 = US47_DataFlow
    val v1123: US46 = US46_NEnvForce
    val v1124: US24 = US24_PEnv(v1123)
    val v1125: US37 = US37_NECheck
    val v1126: US24 = US24_PHsEngine(v1125)
    val v1127: US47 = US47_DataFlow
    val v1128: US37 = US37_NEEngine
    val v1129: US24 = US24_PHsEngine(v1128)
    val v1130: US37 = US37_NEControl
    val v1131: US24 = US24_PHsEngine(v1130)
    val v1132: US47 = US47_DataFlow
    val v1133: US37 = US37_NEEngine
    val v1134: US24 = US24_PHsEngine(v1133)
    val v1135: US37 = US37_NEThreads
    val v1136: US24 = US24_PHsEngine(v1135)
    val v1137: US47 = US47_DataFlow
    val v1138: US37 = US37_NEEngine
    val v1139: US24 = US24_PHsEngine(v1138)
    val v1140: US38 = US38_NTTable
    val v1141: US24 = US24_PHsTable(v1140)
    val v1142: US47 = US47_DataFlow
    val v1143: US37 = US37_NEEngine
    val v1144: US24 = US24_PHsEngine(v1143)
    val v1145: US39 = US39_NGGraph
    val v1146: US24 = US24_PHsGraph(v1145)
    val v1147: US47 = US47_DataFlow
    val v1148: US37 = US37_NEEngine
    val v1149: US24 = US24_PHsEngine(v1148)
    val v1150: US40 = US40_NWList
    val v1151: US24 = US24_PHsWork(v1150)
    val v1152: US47 = US47_DataFlow
    val v1153: US37 = US37_NEEngine
    val v1154: US24 = US24_PHsEngine(v1153)
    val v1155: US41 = US41_NCTl
    val v1156: US24 = US24_PHsCtx(v1155)
    val v1157: US47 = US47_DataFlow
    val v1158: US37 = US37_NEEngine
    val v1159: US24 = US24_PHsEngine(v1158)
    val v1160: US37 = US37_NEStack
    val v1161: US24 = US24_PHsEngine(v1160)
    val v1162: US47 = US47_Call
    val v1163: US37 = US37_NEThreads
    val v1164: US24 = US24_PHsEngine(v1163)
    val v1165: US37 = US37_NELoop
    val v1166: US24 = US24_PHsEngine(v1165)
    val v1167: US47 = US47_Call
    val v1168: US37 = US37_NEThreads
    val v1169: US24 = US24_PHsEngine(v1168)
    val v1170: US37 = US37_NEStack
    val v1171: US24 = US24_PHsEngine(v1170)
    val v1172: US47 = US47_Call
    val v1173: US37 = US37_NELoop
    val v1174: US24 = US24_PHsEngine(v1173)
    val v1175: US40 = US40_NWCh
    val v1176: US24 = US24_PHsWork(v1175)
    val v1177: US47 = US47_Engine
    val v1178: String = "Take"
    val v1179: US37 = US37_NELoop
    val v1180: US24 = US24_PHsEngine(v1179)
    val v1181: US36 = US36_NHsBody
    val v1182: US24 = US24_PHsSeams(v1181)
    val v1183: US47 = US47_Call
    val v1184: String = "runs"
    val v1185: US37 = US37_NELoop
    val v1186: US24 = US24_PHsEngine(v1185)
    val v1187: US37 = US37_NEAbandon
    val v1188: US24 = US24_PHsEngine(v1187)
    val v1189: US47 = US47_Call
    val v1190: String = "finally"
    val v1191: US37 = US37_NELoop
    val v1192: US24 = US24_PHsEngine(v1191)
    val v1193: US41 = US41_NCCtx
    val v1194: US24 = US24_PHsCtx(v1193)
    val v1195: US47 = US47_DataFlow
    val v1196: String = "sets"
    val v1197: US37 = US37_NEClaim
    val v1198: US24 = US24_PHsEngine(v1197)
    val v1199: US41 = US41_NCCtx
    val v1200: US24 = US24_PHsCtx(v1199)
    val v1201: US47 = US47_DataFlow
    val v1202: String = "Ready / Chain"
    val v1203: US37 = US37_NEClaim
    val v1204: US24 = US24_PHsEngine(v1203)
    val v1205: US38 = US38_NTPublished
    val v1206: US24 = US24_PHsTable(v1205)
    val v1207: US47 = US47_DataFlow
    val v1208: String = "snapshot read"
    val v1209: US37 = US37_NEClaim
    val v1210: US24 = US24_PHsEngine(v1209)
    val v1211: US37 = US37_NESettle
    val v1212: US24 = US24_PHsEngine(v1211)
    val v1213: US47 = US47_Call
    val v1214: US37 = US37_NEValidated
    val v1215: US24 = US24_PHsEngine(v1214)
    val v1216: US37 = US37_NESettle
    val v1217: US24 = US24_PHsEngine(v1216)
    val v1218: US47 = US47_Call
    val v1219: US37 = US37_NERead
    val v1220: US24 = US24_PHsEngine(v1219)
    val v1221: US41 = US41_NCCtx
    val v1222: US24 = US24_PHsCtx(v1221)
    val v1223: US47 = US47_DataFlow
    val v1224: String = "Ready only"
    val v1225: US37 = US37_NESettle
    val v1226: US24 = US24_PHsEngine(v1225)
    val v1227: US37 = US37_NEAsk
    val v1228: US24 = US24_PHsEngine(v1227)
    val v1229: US47 = US47_Call
    val v1230: String = "ClaimOp"
    val v1231: US37 = US37_NESettle
    val v1232: US24 = US24_PHsEngine(v1231)
    val v1233: US37 = US37_NEAwait
    val v1234: US24 = US24_PHsEngine(v1233)
    val v1235: US47 = US47_Call
    val v1236: String = "on Wait"
    val v1237: US37 = US37_NEAwait
    val v1238: US24 = US24_PHsEngine(v1237)
    val v1239: US39 = US39_NGCh
    val v1240: US24 = US24_PHsGraph(v1239)
    val v1241: US47 = US47_Engine
    val v1242: String = "AddEdge / RemoveEdge"
    val v1243: US37 = US37_NEAwait
    val v1244: US24 = US24_PHsEngine(v1243)
    val v1245: US37 = US37_NEConflict
    val v1246: US24 = US24_PHsEngine(v1245)
    val v1247: US47 = US47_DataFlow
    val v1248: String = "cycle"
    val v1249: US37 = US37_NEFill
    val v1250: US24 = US24_PHsEngine(v1249)
    val v1251: US37 = US37_NEAsk
    val v1252: US24 = US24_PHsEngine(v1251)
    val v1253: US47 = US47_Call
    val v1254: String = "FillOp"
    val v1255: US37 = US37_NEAnnounce
    val v1256: US24 = US24_PHsEngine(v1255)
    val v1257: US37 = US37_NEAsk
    val v1258: US24 = US24_PHsEngine(v1257)
    val v1259: US47 = US47_Call
    val v1260: String = "AnnounceOp"
    val v1261: US37 = US37_NEFamily
    val v1262: US24 = US24_PHsEngine(v1261)
    val v1263: US37 = US37_NEAsk
    val v1264: US24 = US24_PHsEngine(v1263)
    val v1265: US47 = US47_Call
    val v1266: String = "FamilyOp"
    val v1267: US37 = US37_NEFamily
    val v1268: US24 = US24_PHsEngine(v1267)
    val v1269: US41 = US41_NCCtx
    val v1270: US24 = US24_PHsCtx(v1269)
    val v1271: US47 = US47_DataFlow
    val v1272: String = "local table"
    val v1273: US37 = US37_NEAbandon
    val v1274: US24 = US24_PHsEngine(v1273)
    val v1275: US37 = US37_NEAsk
    val v1276: US24 = US24_PHsEngine(v1275)
    val v1277: US47 = US47_Call
    val v1278: String = "AbandonOp"
    val v1279: US37 = US37_NESpawn
    val v1280: US24 = US24_PHsEngine(v1279)
    val v1281: US40 = US40_NWCh
    val v1282: US24 = US24_PHsWork(v1281)
    val v1283: US47 = US47_Engine
    val v1284: String = "Spawn"
    val v1285: US37 = US37_NEDrain
    val v1286: US24 = US24_PHsEngine(v1285)
    val v1287: US40 = US40_NWCh
    val v1288: US24 = US24_PHsWork(v1287)
    val v1289: US47 = US47_Engine
    val v1290: String = "Drain"
    val v1291: US37 = US37_NEClose
    val v1292: US24 = US24_PHsEngine(v1291)
    val v1293: US40 = US40_NWCh
    val v1294: US24 = US24_PHsWork(v1293)
    val v1295: US47 = US47_Engine
    val v1296: String = "Stop"
    val v1297: US37 = US37_NECheck
    val v1298: US24 = US24_PHsEngine(v1297)
    val v1299: US39 = US39_NGCh
    val v1300: US24 = US24_PHsGraph(v1299)
    val v1301: US47 = US47_Engine
    val v1302: String = "CountCycles"
    val v1303: US37 = US37_NECheck
    val v1304: US24 = US24_PHsEngine(v1303)
    val v1305: US38 = US38_NTGuard
    val v1306: US24 = US24_PHsTable(v1305)
    val v1307: US47 = US47_Engine
    val v1308: String = "Conflicts"
    val v1309: US37 = US37_NECheck
    val v1310: US24 = US24_PHsEngine(v1309)
    val v1311: US37 = US37_NEConflict
    val v1312: US24 = US24_PHsEngine(v1311)
    val v1313: US47 = US47_DataFlow
    val v1314: US37 = US37_NEAsk
    val v1315: US24 = US24_PHsEngine(v1314)
    val v1316: US37 = US37_NEBridge
    val v1317: US24 = US24_PHsEngine(v1316)
    val v1318: US47 = US47_Call
    val v1319: US37 = US37_NEAsk
    val v1320: US24 = US24_PHsEngine(v1319)
    val v1321: US38 = US38_NTGuard
    val v1322: US24 = US24_PHsTable(v1321)
    val v1323: US47 = US47_Engine
    val v1324: String = "modifyFun"
    val v1325: US37 = US37_NEBridge
    val v1326: US24 = US24_PHsEngine(v1325)
    val v1327: US37 = US37_NEControl
    val v1328: US24 = US24_PHsEngine(v1327)
    val v1329: US47 = US47_Engine
    val v1330: String = "Scheduler.run"
    val v1331: US38 = US38_NTGuard
    val v1332: US24 = US24_PHsTable(v1331)
    val v1333: US38 = US38_NTDecide
    val v1334: US24 = US24_PHsTable(v1333)
    val v1335: US47 = US47_Engine
    val v1336: US38 = US38_NTDecide
    val v1337: US24 = US24_PHsTable(v1336)
    val v1338: US38 = US38_NTEntry
    val v1339: US24 = US24_PHsTable(v1338)
    val v1340: US47 = US47_DataFlow
    val v1341: US38 = US38_NTDecide
    val v1342: US24 = US24_PHsTable(v1341)
    val v1343: US38 = US38_NTOps
    val v1344: US24 = US24_PHsTable(v1343)
    val v1345: US47 = US47_DataFlow
    val v1346: US38 = US38_NTDecide
    val v1347: US24 = US24_PHsTable(v1346)
    val v1348: US38 = US38_NTReplies
    val v1349: US24 = US24_PHsTable(v1348)
    val v1350: US47 = US47_DataFlow
    val v1351: US38 = US38_NTDecide
    val v1352: US24 = US24_PHsTable(v1351)
    val v1353: US38 = US38_NTPublished
    val v1354: US24 = US24_PHsTable(v1353)
    val v1355: US47 = US47_DataFlow
    val v1356: String = "publish"
    val v1357: US38 = US38_NTEntry
    val v1358: US24 = US24_PHsTable(v1357)
    val v1359: US38 = US38_NTState
    val v1360: US24 = US24_PHsTable(v1359)
    val v1361: US47 = US47_DataFlow
    val v1362: US38 = US38_NTTable
    val v1363: US24 = US24_PHsTable(v1362)
    val v1364: US38 = US38_NTDecide
    val v1365: US24 = US24_PHsTable(v1364)
    val v1366: US47 = US47_DataFlow
    val v1367: US39 = US39_NGCh
    val v1368: US24 = US24_PHsGraph(v1367)
    val v1369: US39 = US39_NGGraph
    val v1370: US24 = US24_PHsGraph(v1369)
    val v1371: US47 = US47_Engine
    val v1372: String = "Handle"
    val v1373: US39 = US39_NGGraph
    val v1374: US24 = US24_PHsGraph(v1373)
    val v1375: US39 = US39_NGReq
    val v1376: US24 = US24_PHsGraph(v1375)
    val v1377: US47 = US47_DataFlow
    val v1378: US40 = US40_NWCh
    val v1379: US24 = US24_PHsWork(v1378)
    val v1380: US40 = US40_NWList
    val v1381: US24 = US24_PHsWork(v1380)
    val v1382: US47 = US47_Engine
    val v1383: US40 = US40_NWList
    val v1384: US24 = US24_PHsWork(v1383)
    val v1385: US40 = US40_NWReq
    val v1386: US24 = US24_PHsWork(v1385)
    val v1387: US47 = US47_DataFlow
    val v1388: US40 = US40_NWList
    val v1389: US24 = US24_PHsWork(v1388)
    val v1390: US40 = US40_NWSettle
    val v1391: US24 = US24_PHsWork(v1390)
    val v1392: US47 = US47_Call
    val v1393: US41 = US41_NCTl
    val v1394: US24 = US24_PHsCtx(v1393)
    val v1395: US41 = US41_NCCtx
    val v1396: US24 = US24_PHsCtx(v1395)
    val v1397: US47 = US47_DataFlow
    val v1398: US41 = US41_NCCtx
    val v1399: US24 = US24_PHsCtx(v1398)
    val v1400: US36 = US36_NHsMarks
    val v1401: US24 = US24_PHsSeams(v1400)
    val v1402: US47 = US47_Reuse
    val v1403: String = "same Marks type as the session"
    val v1404: US38 = US38_NTDecide
    val v1405: US24 = US24_PHsTable(v1404)
    val v1406: US25 = US25_NHashcons
    val v1407: US24 = US24_PInfra(v1406)
    val v1408: US47 = US47_DataFlow
    val v1409: String = "keys are (table, ConsedNode)"
    val v1410: US42 = US42_NDOrder
    val v1411: US24 = US24_PDiverge(v1410)
    val v1412: US35 = US35_NSfDefer
    val v1413: US24 = US24_PSf(v1412)
    val v1414: US47 = US47_NoteLink
    val v1415: US42 = US42_NDOrder
    val v1416: US24 = US24_PDiverge(v1415)
    val v1417: US36 = US36_NHsDefer
    val v1418: US24 = US24_PHsSeams(v1417)
    val v1419: US47 = US47_NoteLink
    val v1420: US42 = US42_NDOrder
    val v1421: US24 = US24_PDiverge(v1420)
    val v1422: US36 = US36_NHsFinish
    val v1423: US24 = US24_PHsSeams(v1422)
    val v1424: US47 = US47_NoteLink
    val v1425: US42 = US42_NDErr
    val v1426: US24 = US24_PDiverge(v1425)
    val v1427: US35 = US35_NSfRoot
    val v1428: US24 = US24_PSf(v1427)
    val v1429: US47 = US47_NoteLink
    val v1430: US42 = US42_NDErr
    val v1431: US24 = US24_PDiverge(v1430)
    val v1432: US36 = US36_NHsBody
    val v1433: US24 = US24_PHsSeams(v1432)
    val v1434: US47 = US47_NoteLink
    val v1435: US42 = US42_NDPrint
    val v1436: US24 = US24_PDiverge(v1435)
    val v1437: US35 = US35_NSfPrint
    val v1438: US24 = US24_PSf(v1437)
    val v1439: US47 = US47_NoteLink
    val v1440: US42 = US42_NDPrint
    val v1441: US24 = US24_PDiverge(v1440)
    val v1442: US36 = US36_NHsPrint
    val v1443: US24 = US24_PHsSeams(v1442)
    val v1444: US47 = US47_NoteLink
    val v1445: US42 = US42_NDRec
    val v1446: US24 = US24_PDiverge(v1445)
    val v1447: US35 = US35_NSfClaim
    val v1448: US24 = US24_PSf(v1447)
    val v1449: US47 = US47_NoteLink
    val v1450: US42 = US42_NDRec
    val v1451: US24 = US24_PDiverge(v1450)
    val v1452: US37 = US37_NEAnnounce
    val v1453: US24 = US24_PHsEngine(v1452)
    val v1454: US47 = US47_NoteLink
    val v1455: US42 = US42_NDConf
    val v1456: US24 = US24_PDiverge(v1455)
    val v1457: US35 = US35_NSfRestart
    val v1458: US24 = US24_PSf(v1457)
    val v1459: US47 = US47_NoteLink
    val v1460: US42 = US42_NDConf
    val v1461: US24 = US24_PDiverge(v1460)
    val v1462: US36 = US36_NHsRestart
    val v1463: US24 = US24_PHsSeams(v1462)
    val v1464: US47 = US47_NoteLink
    val v1465: US42 = US42_NDNest
    val v1466: US24 = US24_PDiverge(v1465)
    val v1467: US35 = US35_NSfNesting
    val v1468: US24 = US24_PSf(v1467)
    val v1469: US47 = US47_NoteLink
    val v1470: US42 = US42_NDNest
    val v1471: US24 = US24_PDiverge(v1470)
    val v1472: US41 = US41_NCCtx
    val v1473: US24 = US24_PHsCtx(v1472)
    val v1474: US47 = US47_NoteLink
    val v1475: US42 = US42_NDGlobals
    val v1476: US24 = US24_PDiverge(v1475)
    val v1477: US35 = US35_NSfActive
    val v1478: US24 = US24_PSf(v1477)
    val v1479: US47 = US47_NoteLink
    val v1480: US42 = US42_NDGlobals
    val v1481: US24 = US24_PDiverge(v1480)
    val v1482: US32 = US32_NBswitch
    val v1483: US24 = US24_PPeOps(v1482)
    val v1484: US47 = US47_NoteLink
    val v1485: US42 = US42_NDSame
    val v1486: US24 = US24_PDiverge(v1485)
    val v1487: US30 = US30_NResult
    val v1488: US24 = US24_PPe(v1487)
    val v1489: US47 = US47_NoteLink
    val v1490: US46 = US46_NEnvDefine
    val v1491: US24 = US24_PEnv(v1490)
    val v1492: US35 = US35_NSfSession
    val v1493: US24 = US24_PSf(v1492)
    val v1494: US47 = US47_NoteLink
    val v1495: String = "-Mode single-flight"
    val v1496: US46 = US46_NEnvDefine
    val v1497: US24 = US24_PEnv(v1496)
    val v1498: US36 = US36_NHsSession
    val v1499: US24 = US24_PHsSeams(v1498)
    val v1500: US47 = US47_NoteLink
    val v1501: String = "-Mode hopacsota"
    val v1502: US46 = US46_NEnvIf
    val v1503: US24 = US24_PEnv(v1502)
    val v1504: US33 = US33_NDynif
    val v1505: US24 = US24_PPeGuards(v1504)
    val v1506: US47 = US47_DataFlow
    val v1507: US46 = US46_NEnvUnion
    val v1508: US24 = US24_PEnv(v1507)
    val v1509: US33 = US33_NUnionnest
    val v1510: US24 = US24_PPeGuards(v1509)
    val v1511: US47 = US47_DataFlow
    val v1512: UH23 = UH23_Nil
    val v1513: UH23 = UH23_Cons(v1508, v1510, v1511, v11, v1512)
    val v1514: UH23 = UH23_Cons(v1503, v1505, v1506, v11, v1513)
    val v1515: UH23 = UH23_Cons(v1497, v1499, v1500, v1501, v1514)
    val v1516: UH23 = UH23_Cons(v1491, v1493, v1494, v1495, v1515)
    val v1517: UH23 = UH23_Cons(v1486, v1488, v1489, v11, v1516)
    val v1518: UH23 = UH23_Cons(v1481, v1483, v1484, v11, v1517)
    val v1519: UH23 = UH23_Cons(v1476, v1478, v1479, v11, v1518)
    val v1520: UH23 = UH23_Cons(v1471, v1473, v1474, v11, v1519)
    val v1521: UH23 = UH23_Cons(v1466, v1468, v1469, v11, v1520)
    val v1522: UH23 = UH23_Cons(v1461, v1463, v1464, v11, v1521)
    val v1523: UH23 = UH23_Cons(v1456, v1458, v1459, v11, v1522)
    val v1524: UH23 = UH23_Cons(v1451, v1453, v1454, v11, v1523)
    val v1525: UH23 = UH23_Cons(v1446, v1448, v1449, v11, v1524)
    val v1526: UH23 = UH23_Cons(v1441, v1443, v1444, v11, v1525)
    val v1527: UH23 = UH23_Cons(v1436, v1438, v1439, v11, v1526)
    val v1528: UH23 = UH23_Cons(v1431, v1433, v1434, v11, v1527)
    val v1529: UH23 = UH23_Cons(v1426, v1428, v1429, v11, v1528)
    val v1530: UH23 = UH23_Cons(v1421, v1423, v1424, v11, v1529)
    val v1531: UH23 = UH23_Cons(v1416, v1418, v1419, v11, v1530)
    val v1532: UH23 = UH23_Cons(v1411, v1413, v1414, v11, v1531)
    val v1533: UH23 = UH23_Cons(v1405, v1407, v1408, v1409, v1532)
    val v1534: UH23 = UH23_Cons(v1399, v1401, v1402, v1403, v1533)
    val v1535: UH23 = UH23_Cons(v1394, v1396, v1397, v11, v1534)
    val v1536: UH23 = UH23_Cons(v1389, v1391, v1392, v11, v1535)
    val v1537: UH23 = UH23_Cons(v1384, v1386, v1387, v11, v1536)
    val v1538: UH23 = UH23_Cons(v1379, v1381, v1382, v1372, v1537)
    val v1539: UH23 = UH23_Cons(v1374, v1376, v1377, v11, v1538)
    val v1540: UH23 = UH23_Cons(v1368, v1370, v1371, v1372, v1539)
    val v1541: UH23 = UH23_Cons(v1363, v1365, v1366, v11, v1540)
    val v1542: UH23 = UH23_Cons(v1358, v1360, v1361, v11, v1541)
    val v1543: UH23 = UH23_Cons(v1352, v1354, v1355, v1356, v1542)
    val v1544: UH23 = UH23_Cons(v1347, v1349, v1350, v11, v1543)
    val v1545: UH23 = UH23_Cons(v1342, v1344, v1345, v11, v1544)
    val v1546: UH23 = UH23_Cons(v1337, v1339, v1340, v11, v1545)
    val v1547: UH23 = UH23_Cons(v1332, v1334, v1335, v11, v1546)
    val v1548: UH23 = UH23_Cons(v1326, v1328, v1329, v1330, v1547)
    val v1549: UH23 = UH23_Cons(v1320, v1322, v1323, v1324, v1548)
    val v1550: UH23 = UH23_Cons(v1315, v1317, v1318, v11, v1549)
    val v1551: UH23 = UH23_Cons(v1310, v1312, v1313, v11, v1550)
    val v1552: UH23 = UH23_Cons(v1304, v1306, v1307, v1308, v1551)
    val v1553: UH23 = UH23_Cons(v1298, v1300, v1301, v1302, v1552)
    val v1554: UH23 = UH23_Cons(v1292, v1294, v1295, v1296, v1553)
    val v1555: UH23 = UH23_Cons(v1286, v1288, v1289, v1290, v1554)
    val v1556: UH23 = UH23_Cons(v1280, v1282, v1283, v1284, v1555)
    val v1557: UH23 = UH23_Cons(v1274, v1276, v1277, v1278, v1556)
    val v1558: UH23 = UH23_Cons(v1268, v1270, v1271, v1272, v1557)
    val v1559: UH23 = UH23_Cons(v1262, v1264, v1265, v1266, v1558)
    val v1560: UH23 = UH23_Cons(v1256, v1258, v1259, v1260, v1559)
    val v1561: UH23 = UH23_Cons(v1250, v1252, v1253, v1254, v1560)
    val v1562: UH23 = UH23_Cons(v1244, v1246, v1247, v1248, v1561)
    val v1563: UH23 = UH23_Cons(v1238, v1240, v1241, v1242, v1562)
    val v1564: UH23 = UH23_Cons(v1232, v1234, v1235, v1236, v1563)
    val v1565: UH23 = UH23_Cons(v1226, v1228, v1229, v1230, v1564)
    val v1566: UH23 = UH23_Cons(v1220, v1222, v1223, v1224, v1565)
    val v1567: UH23 = UH23_Cons(v1215, v1217, v1218, v11, v1566)
    val v1568: UH23 = UH23_Cons(v1210, v1212, v1213, v11, v1567)
    val v1569: UH23 = UH23_Cons(v1204, v1206, v1207, v1208, v1568)
    val v1570: UH23 = UH23_Cons(v1198, v1200, v1201, v1202, v1569)
    val v1571: UH23 = UH23_Cons(v1192, v1194, v1195, v1196, v1570)
    val v1572: UH23 = UH23_Cons(v1186, v1188, v1189, v1190, v1571)
    val v1573: UH23 = UH23_Cons(v1180, v1182, v1183, v1184, v1572)
    val v1574: UH23 = UH23_Cons(v1174, v1176, v1177, v1178, v1573)
    val v1575: UH23 = UH23_Cons(v1169, v1171, v1172, v11, v1574)
    val v1576: UH23 = UH23_Cons(v1164, v1166, v1167, v11, v1575)
    val v1577: UH23 = UH23_Cons(v1159, v1161, v1162, v11, v1576)
    val v1578: UH23 = UH23_Cons(v1154, v1156, v1157, v11, v1577)
    val v1579: UH23 = UH23_Cons(v1149, v1151, v1152, v11, v1578)
    val v1580: UH23 = UH23_Cons(v1144, v1146, v1147, v11, v1579)
    val v1581: UH23 = UH23_Cons(v1139, v1141, v1142, v11, v1580)
    val v1582: UH23 = UH23_Cons(v1134, v1136, v1137, v11, v1581)
    val v1583: UH23 = UH23_Cons(v1129, v1131, v1132, v11, v1582)
    val v1584: UH23 = UH23_Cons(v1124, v1126, v1127, v11, v1583)
    val v1585: UH23 = UH23_Cons(v1119, v1121, v1122, v11, v1584)
    val v1586: UH23 = UH23_Cons(v1113, v1115, v1116, v1117, v1585)
    val v1587: UH23 = UH23_Cons(v1108, v1110, v1111, v11, v1586)
    val v1588: UH23 = UH23_Cons(v1102, v1104, v1105, v1106, v1587)
    val v1589: UH23 = UH23_Cons(v1096, v1098, v1099, v1100, v1588)
    val v1590: UH23 = UH23_Cons(v1090, v1092, v1093, v1094, v1589)
    val v1591: UH23 = UH23_Cons(v1084, v1086, v1087, v1088, v1590)
    val v1592: UH23 = UH23_Cons(v1078, v1080, v1081, v1082, v1591)
    val v1593: UH23 = UH23_Cons(v1072, v1074, v1075, v1076, v1592)
    val v1594: UH23 = UH23_Cons(v1066, v1068, v1069, v1070, v1593)
    val v1595: UH23 = UH23_Cons(v1061, v1063, v1064, v11, v1594)
    val v1596: UH23 = UH23_Cons(v1056, v1058, v1059, v11, v1595)
    val v1597: UH23 = UH23_Cons(v1050, v1052, v1053, v1054, v1596)
    val v1598: UH23 = UH23_Cons(v1044, v1046, v1047, v1048, v1597)
    val v1599: UH23 = UH23_Cons(v1038, v1040, v1041, v1042, v1598)
    val v1600: UH23 = UH23_Cons(v1032, v1034, v1035, v1036, v1599)
    val v1601: UH23 = UH23_Cons(v1026, v1028, v1029, v1030, v1600)
    val v1602: UH23 = UH23_Cons(v1020, v1022, v1023, v1024, v1601)
    val v1603: UH23 = UH23_Cons(v1015, v1017, v1018, v403, v1602)
    val v1604: UH23 = UH23_Cons(v1009, v1011, v1012, v1013, v1603)
    val v1605: UH23 = UH23_Cons(v1004, v1006, v1007, v11, v1604)
    val v1606: UH23 = UH23_Cons(v999, v1001, v1002, v11, v1605)
    val v1607: UH23 = UH23_Cons(v994, v996, v997, v11, v1606)
    val v1608: UH23 = UH23_Cons(v989, v991, v992, v11, v1607)
    val v1609: UH23 = UH23_Cons(v984, v986, v987, v11, v1608)
    val v1610: UH23 = UH23_Cons(v978, v980, v981, v982, v1609)
    val v1611: UH23 = UH23_Cons(v972, v974, v975, v976, v1610)
    val v1612: UH23 = UH23_Cons(v966, v968, v969, v970, v1611)
    val v1613: UH23 = UH23_Cons(v961, v963, v964, v11, v1612)
    val v1614: UH23 = UH23_Cons(v955, v957, v958, v959, v1613)
    val v1615: UH23 = UH23_Cons(v949, v951, v952, v953, v1614)
    val v1616: UH23 = UH23_Cons(v943, v945, v946, v947, v1615)
    val v1617: UH23 = UH23_Cons(v937, v939, v940, v941, v1616)
    val v1618: UH23 = UH23_Cons(v931, v933, v934, v935, v1617)
    val v1619: UH23 = UH23_Cons(v925, v927, v928, v929, v1618)
    val v1620: UH23 = UH23_Cons(v920, v922, v923, v11, v1619)
    val v1621: UH23 = UH23_Cons(v915, v917, v918, v11, v1620)
    val v1622: UH23 = UH23_Cons(v909, v911, v912, v913, v1621)
    val v1623: UH23 = UH23_Cons(v903, v905, v906, v907, v1622)
    val v1624: UH23 = UH23_Cons(v898, v900, v901, v11, v1623)
    val v1625: UH23 = UH23_Cons(v893, v895, v896, v11, v1624)
    val v1626: UH23 = UH23_Cons(v887, v889, v890, v891, v1625)
    val v1627: UH23 = UH23_Cons(v882, v884, v885, v11, v1626)
    val v1628: UH23 = UH23_Cons(v877, v879, v880, v11, v1627)
    val v1629: UH23 = UH23_Cons(v872, v874, v875, v11, v1628)
    val v1630: UH23 = UH23_Cons(v867, v869, v870, v11, v1629)
    val v1631: UH23 = UH23_Cons(v862, v864, v865, v11, v1630)
    val v1632: UH23 = UH23_Cons(v857, v859, v860, v11, v1631)
    val v1633: UH23 = UH23_Cons(v852, v854, v855, v11, v1632)
    val v1634: UH23 = UH23_Cons(v847, v849, v850, v11, v1633)
    val v1635: UH23 = UH23_Cons(v842, v844, v845, v11, v1634)
    val v1636: UH23 = UH23_Cons(v837, v839, v840, v11, v1635)
    val v1637: UH23 = UH23_Cons(v832, v834, v835, v11, v1636)
    val v1638: UH23 = UH23_Cons(v827, v829, v830, v11, v1637)
    val v1639: UH23 = UH23_Cons(v822, v824, v825, v11, v1638)
    val v1640: UH23 = UH23_Cons(v817, v819, v820, v11, v1639)
    val v1641: UH23 = UH23_Cons(v812, v814, v815, v11, v1640)
    val v1642: UH23 = UH23_Cons(v806, v808, v809, v810, v1641)
    val v1643: UH23 = UH23_Cons(v801, v803, v804, v11, v1642)
    val v1644: UH23 = UH23_Cons(v796, v798, v799, v11, v1643)
    val v1645: UH23 = UH23_Cons(v791, v793, v794, v11, v1644)
    val v1646: UH23 = UH23_Cons(v786, v788, v789, v11, v1645)
    val v1647: UH23 = UH23_Cons(v781, v783, v784, v11, v1646)
    val v1648: UH23 = UH23_Cons(v776, v778, v779, v11, v1647)
    val v1649: UH23 = UH23_Cons(v771, v773, v774, v11, v1648)
    val v1650: UH23 = UH23_Cons(v766, v768, v769, v11, v1649)
    val v1651: UH23 = UH23_Cons(v761, v763, v764, v11, v1650)
    val v1652: UH23 = UH23_Cons(v756, v758, v759, v11, v1651)
    val v1653: UH23 = UH23_Cons(v751, v753, v754, v11, v1652)
    val v1654: UH23 = UH23_Cons(v746, v748, v749, v11, v1653)
    val v1655: UH23 = UH23_Cons(v741, v743, v744, v11, v1654)
    val v1656: UH23 = UH23_Cons(v736, v738, v739, v11, v1655)
    val v1657: UH23 = UH23_Cons(v731, v733, v734, v11, v1656)
    val v1658: UH23 = UH23_Cons(v726, v728, v729, v11, v1657)
    val v1659: UH23 = UH23_Cons(v721, v723, v724, v11, v1658)
    val v1660: UH23 = UH23_Cons(v716, v718, v719, v11, v1659)
    val v1661: UH23 = UH23_Cons(v711, v713, v714, v11, v1660)
    val v1662: UH23 = UH23_Cons(v706, v708, v709, v11, v1661)
    val v1663: UH23 = UH23_Cons(v701, v703, v704, v11, v1662)
    val v1664: UH23 = UH23_Cons(v696, v698, v699, v11, v1663)
    val v1665: UH23 = UH23_Cons(v691, v693, v694, v11, v1664)
    val v1666: UH23 = UH23_Cons(v686, v688, v689, v11, v1665)
    val v1667: UH23 = UH23_Cons(v681, v683, v684, v11, v1666)
    val v1668: UH23 = UH23_Cons(v676, v678, v679, v11, v1667)
    val v1669: UH23 = UH23_Cons(v671, v673, v674, v11, v1668)
    val v1670: UH23 = UH23_Cons(v665, v667, v668, v669, v1669)
    val v1671: UH23 = UH23_Cons(v660, v662, v663, v11, v1670)
    val v1672: UH23 = UH23_Cons(v654, v656, v657, v658, v1671)
    val v1673: UH23 = UH23_Cons(v648, v650, v651, v652, v1672)
    val v1674: UH23 = UH23_Cons(v643, v645, v646, v540, v1673)
    val v1675: UH23 = UH23_Cons(v638, v640, v641, v576, v1674)
    val v1676: UH23 = UH23_Cons(v633, v635, v636, v570, v1675)
    val v1677: UH23 = UH23_Cons(v627, v629, v630, v631, v1676)
    val v1678: UH23 = UH23_Cons(v621, v623, v624, v625, v1677)
    val v1679: UH23 = UH23_Cons(v615, v617, v618, v619, v1678)
    val v1680: UH23 = UH23_Cons(v610, v612, v613, v546, v1679)
    val v1681: UH23 = UH23_Cons(v605, v607, v608, v540, v1680)
    val v1682: UH23 = UH23_Cons(v600, v602, v603, v576, v1681)
    val v1683: UH23 = UH23_Cons(v594, v596, v597, v598, v1682)
    val v1684: UH23 = UH23_Cons(v588, v590, v591, v592, v1683)
    val v1685: UH23 = UH23_Cons(v583, v585, v586, v546, v1684)
    val v1686: UH23 = UH23_Cons(v578, v580, v581, v540, v1685)
    val v1687: UH23 = UH23_Cons(v572, v574, v575, v576, v1686)
    val v1688: UH23 = UH23_Cons(v566, v568, v569, v570, v1687)
    val v1689: UH23 = UH23_Cons(v560, v562, v563, v564, v1688)
    val v1690: UH23 = UH23_Cons(v554, v556, v557, v558, v1689)
    val v1691: UH23 = UH23_Cons(v548, v550, v551, v552, v1690)
    val v1692: UH23 = UH23_Cons(v542, v544, v545, v546, v1691)
    val v1693: UH23 = UH23_Cons(v536, v538, v539, v540, v1692)
    val v1694: UH23 = UH23_Cons(v530, v532, v533, v534, v1693)
    val v1695: UH23 = UH23_Cons(v524, v526, v527, v528, v1694)
    val v1696: UH23 = UH23_Cons(v519, v521, v522, v11, v1695)
    val v1697: UH23 = UH23_Cons(v514, v516, v517, v11, v1696)
    val v1698: UH23 = UH23_Cons(v509, v511, v512, v11, v1697)
    val v1699: UH23 = UH23_Cons(v504, v506, v507, v11, v1698)
    val v1700: UH23 = UH23_Cons(v499, v501, v502, v11, v1699)
    val v1701: UH23 = UH23_Cons(v494, v496, v497, v11, v1700)
    val v1702: UH23 = UH23_Cons(v488, v490, v491, v492, v1701)
    val v1703: UH23 = UH23_Cons(v483, v485, v486, v11, v1702)
    val v1704: UH23 = UH23_Cons(v478, v480, v481, v11, v1703)
    val v1705: UH23 = UH23_Cons(v473, v475, v476, v11, v1704)
    val v1706: UH23 = UH23_Cons(v468, v470, v471, v11, v1705)
    val v1707: UH23 = UH23_Cons(v462, v464, v465, v466, v1706)
    val v1708: UH23 = UH23_Cons(v457, v459, v460, v11, v1707)
    val v1709: UH23 = UH23_Cons(v452, v454, v455, v11, v1708)
    val v1710: UH23 = UH23_Cons(v447, v449, v450, v11, v1709)
    val v1711: UH23 = UH23_Cons(v442, v444, v445, v11, v1710)
    val v1712: UH23 = UH23_Cons(v437, v439, v440, v11, v1711)
    val v1713: UH23 = UH23_Cons(v432, v434, v435, v11, v1712)
    val v1714: UH23 = UH23_Cons(v427, v429, v430, v11, v1713)
    val v1715: UH23 = UH23_Cons(v421, v423, v424, v425, v1714)
    val v1716: UH23 = UH23_Cons(v416, v418, v419, v11, v1715)
    val v1717: UH23 = UH23_Cons(v411, v413, v414, v11, v1716)
    val v1718: UH23 = UH23_Cons(v405, v407, v408, v409, v1717)
    val v1719: UH23 = UH23_Cons(v399, v401, v402, v403, v1718)
    val v1720: UH23 = UH23_Cons(v393, v395, v396, v397, v1719)
    val v1721: UH23 = UH23_Cons(v387, v389, v390, v391, v1720)
    val v1722: UH23 = UH23_Cons(v381, v383, v384, v385, v1721)
    val v1723: UH23 = UH23_Cons(v375, v377, v378, v379, v1722)
    val v1724: UH23 = UH23_Cons(v370, v372, v373, v11, v1723)
    val v1725: UH23 = UH23_Cons(v365, v367, v368, v11, v1724)
    val v1726: UH23 = UH23_Cons(v360, v362, v363, v11, v1725)
    val v1727: UH23 = UH23_Cons(v355, v357, v358, v11, v1726)
    val v1728: UH23 = UH23_Cons(v350, v352, v353, v11, v1727)
    val v1729: UH23 = UH23_Cons(v345, v347, v348, v11, v1728)
    val v1730: UH23 = UH23_Cons(v339, v341, v342, v343, v1729)
    val v1731: UH23 = UH23_Cons(v334, v336, v337, v11, v1730)
    val v1732: UH23 = UH23_Cons(v329, v331, v332, v11, v1731)
    val v1733: UH23 = UH23_Cons(v324, v326, v327, v11, v1732)
    val v1734: UH23 = UH23_Cons(v319, v321, v322, v11, v1733)
    val v1735: UH23 = UH23_Cons(v314, v316, v317, v11, v1734)
    val v1736: UH23 = UH23_Cons(v309, v311, v312, v11, v1735)
    val v1737: UH23 = UH23_Cons(v304, v306, v307, v11, v1736)
    val v1738: UH23 = UH23_Cons(v298, v300, v301, v302, v1737)
    val v1739: UH23 = UH23_Cons(v292, v294, v295, v296, v1738)
    val v1740: UH23 = UH23_Cons(v286, v288, v289, v290, v1739)
    val v1741: UH23 = UH23_Cons(v281, v283, v284, v11, v1740)
    val v1742: UH23 = UH23_Cons(v275, v277, v278, v279, v1741)
    val v1743: UH23 = UH23_Cons(v269, v271, v272, v273, v1742)
    val v1744: UH23 = UH23_Cons(v264, v266, v267, v11, v1743)
    val v1745: UH23 = UH23_Cons(v259, v261, v262, v11, v1744)
    val v1746: UH23 = UH23_Cons(v253, v255, v256, v257, v1745)
    val v1747: UH23 = UH23_Cons(v247, v249, v250, v251, v1746)
    val v1748: UH23 = UH23_Cons(v241, v243, v244, v245, v1747)
    val v1749: UH23 = UH23_Cons(v235, v237, v238, v239, v1748)
    val v1750: UH23 = UH23_Cons(v230, v232, v233, v11, v1749)
    val v1751: UH23 = UH23_Cons(v225, v227, v228, v11, v1750)
    val v1752: UH23 = UH23_Cons(v220, v222, v223, v11, v1751)
    val v1753: UH23 = UH23_Cons(v215, v217, v218, v11, v1752)
    val v1754: UH23 = UH23_Cons(v210, v212, v213, v11, v1753)
    val v1755: UH23 = UH23_Cons(v205, v207, v208, v11, v1754)
    val v1756: UH23 = UH23_Cons(v200, v202, v203, v11, v1755)
    val v1757: UH23 = UH23_Cons(v195, v197, v198, v11, v1756)
    val v1758: UH23 = UH23_Cons(v190, v192, v193, v11, v1757)
    val v1759: UH23 = UH23_Cons(v185, v187, v188, v11, v1758)
    val v1760: UH23 = UH23_Cons(v180, v182, v183, v11, v1759)
    val v1761: UH23 = UH23_Cons(v175, v177, v178, v11, v1760)
    val v1762: UH23 = UH23_Cons(v170, v172, v173, v11, v1761)
    val v1763: UH23 = UH23_Cons(v165, v167, v168, v11, v1762)
    val v1764: UH23 = UH23_Cons(v160, v162, v163, v11, v1763)
    val v1765: UH23 = UH23_Cons(v155, v157, v158, v11, v1764)
    val v1766: UH23 = UH23_Cons(v150, v152, v153, v11, v1765)
    val v1767: UH23 = UH23_Cons(v145, v147, v148, v11, v1766)
    val v1768: UH23 = UH23_Cons(v140, v142, v143, v11, v1767)
    val v1769: UH23 = UH23_Cons(v135, v137, v138, v11, v1768)
    val v1770: UH23 = UH23_Cons(v130, v132, v133, v11, v1769)
    val v1771: UH23 = UH23_Cons(v125, v127, v128, v11, v1770)
    val v1772: UH23 = UH23_Cons(v119, v121, v122, v123, v1771)
    val v1773: UH23 = UH23_Cons(v113, v115, v116, v117, v1772)
    val v1774: UH23 = UH23_Cons(v107, v109, v110, v111, v1773)
    val v1775: UH23 = UH23_Cons(v101, v103, v104, v105, v1774)
    val v1776: UH23 = UH23_Cons(v96, v98, v99, v11, v1775)
    val v1777: UH23 = UH23_Cons(v90, v92, v93, v94, v1776)
    val v1778: UH23 = UH23_Cons(v85, v87, v88, v11, v1777)
    val v1779: UH23 = UH23_Cons(v79, v81, v82, v83, v1778)
    val v1780: UH23 = UH23_Cons(v74, v76, v77, v11, v1779)
    val v1781: UH23 = UH23_Cons(v69, v71, v72, v11, v1780)
    val v1782: UH23 = UH23_Cons(v63, v65, v66, v67, v1781)
    val v1783: UH23 = UH23_Cons(v58, v60, v61, v11, v1782)
    val v1784: UH23 = UH23_Cons(v53, v55, v56, v11, v1783)
    val v1785: UH23 = UH23_Cons(v48, v50, v51, v11, v1784)
    val v1786: UH23 = UH23_Cons(v43, v45, v46, v11, v1785)
    val v1787: UH23 = UH23_Cons(v37, v39, v40, v41, v1786)
    val v1788: UH23 = UH23_Cons(v31, v33, v34, v35, v1787)
    val v1789: UH23 = UH23_Cons(v25, v27, v28, v29, v1788)
    val v1790: UH23 = UH23_Cons(v19, v21, v22, v23, v1789)
    val v1791: UH23 = UH23_Cons(v13, v15, v16, v17, v1790)
    val v1792: UH23 = UH23_Cons(v7, v9, v10, v11, v1791)
    return UH23_Cons(v1, v3, v4, v5, v1792)
}
fun sp_info_of_infra_78(p0: US25): SpiralTuple6 {
    var v0: US25 = p0
    var v86: String = ""
    var v87: US48 = US48_RWhole
    var v88: US49 = US49_Entry
    var v89: String = ""
    var v90: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "hashcons"
            var v2: US48 = US48_RWhole
            v2 = US48_RInfra
            var v3: US49 = US49_Entry
            v3 = US49_Data
            var v4: String = ""
            v4 = "HashConsTable (L153)"
            var v5: String = ""
            v5 = "weak-ref buckets; Add under lock sync_root -> thread-safe for hopacsota"
            v86 = v1
            v87 = v2
            v88 = v3
            v89 = v4
            v90 = v5
        }
        else -> {
            var v81: String = ""
            var v82: US48 = US48_RWhole
            var v83: US49 = US49_Entry
            var v84: String = ""
            var v85: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "consed"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RInfra
                    var v8: US49 = US49_Entry
                    v8 = US49_Data
                    var v9: String = ""
                    v9 = "ConsedNode<'a> (L131)"
                    var v10: String = ""
                    v10 = "equality by tag; join point keys are ConsedNodes"
                    v81 = v6
                    v82 = v7
                    v83 = v8
                    v84 = v9
                    v85 = v10
                }
                else -> {
                    var v76: String = ""
                    var v77: US48 = US48_RWhole
                    var v78: US49 = US49_Entry
                    var v79: String = ""
                    var v80: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "striped"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RInfra
                            var v13: US49 = US49_Entry
                            v13 = US49_Data
                            var v14: String = ""
                            v14 = "StripedHashConsTable (L221)"
                            var v15: String = ""
                            v15 = "32 shards, Fibonacci hashing (MIGRATION, not wired into peval)"
                            v76 = v11
                            v77 = v12
                            v78 = v13
                            v79 = v14
                            v80 = v15
                        }
                        else -> {
                            var v71: String = ""
                            var v72: US48 = US48_RWhole
                            var v73: US49 = US49_Entry
                            var v74: String = ""
                            var v75: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "memoize"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RInfra
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Fn
                                    var v19: String = ""
                                    v19 = "Utils.memoize (L351)"
                                    var v20: String = ""
                                    v20 = "Dictionary memo; single-flight family = memoize"
                                    v71 = v16
                                    v72 = v17
                                    v73 = v18
                                    v74 = v19
                                    v75 = v20
                                }
                                else -> {
                                    var v66: String = ""
                                    var v67: US48 = US48_RWhole
                                    var v68: US49 = US49_Entry
                                    var v69: String = ""
                                    var v70: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "hopacext"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RInfra
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Fn
                                            var v24: String = ""
                                            v24 = "HopacExtensions (L3794)"
                                            var v25: String = ""
                                            v25 = "Hopac Job/Promise/Stream helpers (both builds)"
                                            v66 = v21
                                            v67 = v22
                                            v68 = v23
                                            v69 = v24
                                            v70 = v25
                                        }
                                        else -> {
                                            var v61: String = ""
                                            var v62: US48 = US48_RWhole
                                            var v63: US49 = US49_Entry
                                            var v64: String = ""
                                            var v65: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "pvec"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RInfra
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Fn
                                                    var v29: String = ""
                                                    v29 = "PersistentVector ext. (L46)"
                                                    var v30: String = ""
                                                    v30 = "immutable vectors for WDiff"
                                                    v61 = v26
                                                    v62 = v27
                                                    v63 = v28
                                                    v64 = v29
                                                    v65 = v30
                                                }
                                                else -> {
                                                    var v56: String = ""
                                                    var v57: US48 = US48_RWhole
                                                    var v58: US49 = US49_Entry
                                                    var v59: String = ""
                                                    var v60: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "parsec"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RInfra
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Fn
                                                            var v34: String = ""
                                                            v34 = "ParserCombinators (L381)"
                                                            var v35: String = ""
                                                            v35 = "tuple/pipe/many/choice combinators"
                                                            v56 = v31
                                                            v57 = v32
                                                            v58 = v33
                                                            v59 = v34
                                                            v60 = v35
                                                        }
                                                        else -> {
                                                            var v51: String = ""
                                                            var v52: US48 = US48_RWhole
                                                            var v53: US49 = US49_Entry
                                                            var v54: String = ""
                                                            var v55: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "idadapter"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RInfra
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Fn
                                                                    var v39: String = ""
                                                                    v39 = "IdentityAdapter (L11)"
                                                                    var v40: String = ""
                                                                    v40 = "name ids are strings in this core"
                                                                    v51 = v36
                                                                    v52 = v37
                                                                    v53 = v38
                                                                    v54 = v39
                                                                    v55 = v40
                                                                }
                                                                else -> {
                                                                    var v46: String = ""
                                                                    var v47: US48 = US48_RWhole
                                                                    var v48: US49 = US49_Entry
                                                                    var v49: String = ""
                                                                    var v50: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v41: String = ""
                                                                            v41 = "hopaclib"
                                                                            var v42: US48 = US48_RWhole
                                                                            v42 = US48_RInfra
                                                                            var v43: US49 = US49_Entry
                                                                            v43 = US49_Ext
                                                                            var v44: String = ""
                                                                            v44 = "Hopac library"
                                                                            var v45: String = ""
                                                                            v45 = "Job, Ch, IVar, MVar, Promise, Scheduler"
                                                                            v46 = v41
                                                                            v47 = v42
                                                                            v48 = v43
                                                                            v49 = v44
                                                                            v50 = v45
                                                                        }
                                                                        else -> throw RuntimeException()
                                                                    }
                                                                    v51 = v46
                                                                    v52 = v47
                                                                    v53 = v48
                                                                    v54 = v49
                                                                    v55 = v50
                                                                }
                                                            }
                                                            v56 = v51
                                                            v57 = v52
                                                            v58 = v53
                                                            v59 = v54
                                                            v60 = v55
                                                        }
                                                    }
                                                    v61 = v56
                                                    v62 = v57
                                                    v63 = v58
                                                    v64 = v59
                                                    v65 = v60
                                                }
                                            }
                                            v66 = v61
                                            v67 = v62
                                            v68 = v63
                                            v69 = v64
                                            v70 = v65
                                        }
                                    }
                                    v71 = v66
                                    v72 = v67
                                    v73 = v68
                                    v74 = v69
                                    v75 = v70
                                }
                            }
                            v76 = v71
                            v77 = v72
                            v78 = v73
                            v79 = v74
                            v80 = v75
                        }
                    }
                    v81 = v76
                    v82 = v77
                    v83 = v78
                    v84 = v79
                    v85 = v80
                }
            }
            v86 = v81
            v87 = v82
            v88 = v83
            v89 = v84
            v90 = v85
        }
    }
    return SpiralTuple6(v86, v87, v88, v89, v90)
}
fun sp_info_of_front_79(p0: US26): SpiralTuple6 {
    var v0: US26 = p0
    var v56: String = ""
    var v57: US48 = US48_RWhole
    var v58: US49 = US49_Entry
    var v59: String = ""
    var v60: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "startup"
            var v2: US48 = US48_RWhole
            v2 = US48_RFront
            var v3: US49 = US49_Entry
            v3 = US49_Fn
            var v4: String = ""
            v4 = "Startup / CliArguments (L250)"
            var v5: String = ""
            v5 = "Argu parse, DefaultEnv, PrimitiveType"
            v56 = v1
            v57 = v2
            v58 = v3
            v59 = v4
            v60 = v5
        }
        else -> {
            var v51: String = ""
            var v52: US48 = US48_RWhole
            var v53: US49 = US49_Entry
            var v54: String = ""
            var v55: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "tokenize"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RFront
                    var v8: US49 = US49_Entry
                    v8 = US49_Fn
                    var v9: String = ""
                    v9 = "Tokenize (L870)"
                    var v10: String = ""
                    v10 = "LexState, token, vscode_tokens, semantic legend"
                    v51 = v6
                    v52 = v7
                    v53 = v8
                    v54 = v9
                    v55 = v10
                }
                else -> {
                    var v46: String = ""
                    var v47: US48 = US48_RWhole
                    var v48: US49 = US49_Entry
                    var v49: String = ""
                    var v50: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "blocksplit"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RFront
                            var v13: US49 = US49_Entry
                            v13 = US49_Fn
                            var v14: String = ""
                            v14 = "BlockSplitting (L1629)"
                            var v15: String = ""
                            v15 = "block_at / block_all / wdiff_block_all"
                            v46 = v11
                            v47 = v12
                            v48 = v13
                            v49 = v14
                            v50 = v15
                        }
                        else -> {
                            var v41: String = ""
                            var v42: US48 = US48_RWhole
                            var v43: US49 = US49_Entry
                            var v44: String = ""
                            var v45: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "blockparse"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RFront
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Fn
                                    var v19: String = ""
                                    v19 = "BlockParsing (L1703)"
                                    var v20: String = ""
                                    v20 = "top_statement, patterns, OrPatternJoin, operators"
                                    v41 = v16
                                    v42 = v17
                                    v43 = v18
                                    v44 = v19
                                    v45 = v20
                                }
                                else -> {
                                    var v36: String = ""
                                    var v37: US48 = US48_RWhole
                                    var v38: US49 = US49_Entry
                                    var v39: String = ""
                                    var v40: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "bundle"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RFront
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Fn
                                            var v24: String = ""
                                            v24 = "BlockBundling (L3855)"
                                            var v25: String = ""
                                            v25 = "bundle_blocks, wdiff_block_bundle"
                                            v36 = v21
                                            v37 = v22
                                            v38 = v23
                                            v39 = v24
                                            v40 = v25
                                        }
                                        else -> {
                                            var v31: String = ""
                                            var v32: US48 = US48_RWhole
                                            var v33: US49 = US49_Entry
                                            var v34: String = ""
                                            var v35: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "formatter"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RFront
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Fn
                                                    var v29: String = ""
                                                    v29 = "Formatter (L29325)"
                                                    var v30: String = ""
                                                    v30 = "4,180 lines, standalone"
                                                    v31 = v26
                                                    v32 = v27
                                                    v33 = v28
                                                    v34 = v29
                                                    v35 = v30
                                                }
                                                else -> throw RuntimeException()
                                            }
                                            v36 = v31
                                            v37 = v32
                                            v38 = v33
                                            v39 = v34
                                            v40 = v35
                                        }
                                    }
                                    v41 = v36
                                    v42 = v37
                                    v43 = v38
                                    v44 = v39
                                    v45 = v40
                                }
                            }
                            v46 = v41
                            v47 = v42
                            v48 = v43
                            v49 = v44
                            v50 = v45
                        }
                    }
                    v51 = v46
                    v52 = v47
                    v53 = v48
                    v54 = v49
                    v55 = v50
                }
            }
            v56 = v51
            v57 = v52
            v58 = v53
            v59 = v54
            v60 = v55
        }
    }
    return SpiralTuple6(v56, v57, v58, v59, v60)
}
fun sp_info_of_proj_80(p0: US27): SpiralTuple6 {
    var v0: US27 = p0
    var v46: String = ""
    var v47: US48 = US48_RWhole
    var v48: US49 = US49_Entry
    var v49: String = ""
    var v50: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "spiproj"
            var v2: US48 = US48_RWhole
            v2 = US48_RProj
            var v3: US49 = US49_Entry
            v3 = US49_Fn
            var v4: String = ""
            v4 = "SpiProj (L28785)"
            var v5: String = ""
            v5 = "package.spiproj / TOML schema, derived modules"
            v46 = v1
            v47 = v2
            v48 = v3
            v49 = v4
            v50 = v5
        }
        else -> {
            var v41: String = ""
            var v42: US48 = US48_RWhole
            var v43: US49 = US49_Entry
            var v44: String = ""
            var v45: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "wdiff"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RProj
                    var v8: US49 = US49_Entry
                    v8 = US49_Fn
                    var v9: String = ""
                    v9 = "WDiff (L27901)"
                    var v10: String = ""
                    v10 = "tokenizer/parse/module/file/proj diffs; typecheck funs"
                    v41 = v6
                    v42 = v7
                    v43 = v8
                    v44 = v9
                    v45 = v10
                }
                else -> {
                    var v36: String = ""
                    var v37: US48 = US48_RWhole
                    var v38: US49 = US49_Entry
                    var v39: String = ""
                    var v40: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "wdiffprep"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RProj
                            var v13: US49 = US49_Entry
                            v13 = US49_Fn
                            var v14: String = ""
                            v14 = "WDiffPrepass (L28620)"
                            var v15: String = ""
                            v15 = "incremental prepass per file/package"
                            v36 = v11
                            v37 = v12
                            v38 = v13
                            v39 = v14
                            v40 = v15
                        }
                        else -> {
                            var v31: String = ""
                            var v32: US48 = US48_RWhole
                            var v33: US49 = US49_Entry
                            var v34: String = ""
                            var v35: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "graph"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RProj
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Fn
                                    var v19: String = ""
                                    v19 = "Graph (L33525)"
                                    var v20: String = ""
                                    v20 = "MirroredGraph, topological_sort, circular_nodes"
                                    v31 = v16
                                    v32 = v17
                                    v33 = v18
                                    v34 = v19
                                    v35 = v20
                                }
                                else -> {
                                    var v26: String = ""
                                    var v27: US48 = US48_RWhole
                                    var v28: US49 = US49_Entry
                                    var v29: String = ""
                                    var v30: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "loader"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RProj
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Fn
                                            var v24: String = ""
                                            v24 = "ServerUtils loader (L33645)"
                                            var v25: String = ""
                                            v25 = "loader_package, dirty_nodes_tc/prepass, schema state"
                                            v26 = v21
                                            v27 = v22
                                            v28 = v23
                                            v29 = v24
                                            v30 = v25
                                        }
                                        else -> throw RuntimeException()
                                    }
                                    v31 = v26
                                    v32 = v27
                                    v33 = v28
                                    v34 = v29
                                    v35 = v30
                                }
                            }
                            v36 = v31
                            v37 = v32
                            v38 = v33
                            v39 = v34
                            v40 = v35
                        }
                    }
                    v41 = v36
                    v42 = v37
                    v43 = v38
                    v44 = v39
                    v45 = v40
                }
            }
            v46 = v41
            v47 = v42
            v48 = v43
            v49 = v44
            v50 = v45
        }
    }
    return SpiralTuple6(v46, v47, v48, v49, v50)
}
fun sp_info_of_typing_81(p0: US28): SpiralTuple6 {
    var v0: US28 = p0
    var v36: String = ""
    var v37: US48 = US48_RWhole
    var v38: US49 = US49_Entry
    var v39: String = ""
    var v40: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "infer"
            var v2: US48 = US48_RWhole
            v2 = US48_RTyping
            var v3: US49 = US49_Entry
            v3 = US49_Fn
            var v4: String = ""
            v4 = "Infer (L4193)"
            var v5: String = ""
            v5 = "infer, TopEnv, FilledTop Hopac.Promise"
            v36 = v1
            v37 = v2
            v38 = v3
            v39 = v4
            v40 = v5
        }
        else -> {
            var v31: String = ""
            var v32: US48 = US48_RWhole
            var v33: US49 = US49_Entry
            var v34: String = ""
            var v35: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "inferadapter"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RTyping
                    var v8: US49 = US49_Entry
                    v8 = US49_Seam
                    var v9: String = ""
                    v9 = "InferAdapter (L4187)"
                    var v10: String = ""
                    v10 = "identity seam: failedDeclaration, module reports"
                    v31 = v6
                    v32 = v7
                    v33 = v8
                    v34 = v9
                    v35 = v10
                }
                else -> {
                    var v26: String = ""
                    var v27: US48 = US48_RWhole
                    var v28: US49 = US49_Entry
                    var v29: String = ""
                    var v30: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "inlinedepth"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RTyping
                            var v13: US49 = US49_Entry
                            v13 = US49_Data
                            var v14: String = ""
                            v14 = "InlineDepth (L4147)"
                            var v15: String = ""
                            v15 = "InlineStackPolicy, InlineRecursionStop"
                            v26 = v11
                            v27 = v12
                            v28 = v13
                            v29 = v14
                            v30 = v15
                        }
                        else -> {
                            var v21: String = ""
                            var v22: US48 = US48_RWhole
                            var v23: US49 = US49_Entry
                            var v24: String = ""
                            var v25: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "typeerr"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RTyping
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Fn
                                    var v19: String = ""
                                    v19 = "TypeErrorCodes (L4180)"
                                    var v20: String = ""
                                    v20 = "type_error_report"
                                    v21 = v16
                                    v22 = v17
                                    v23 = v18
                                    v24 = v19
                                    v25 = v20
                                }
                                else -> throw RuntimeException()
                            }
                            v26 = v21
                            v27 = v22
                            v28 = v23
                            v29 = v24
                            v30 = v25
                        }
                    }
                    v31 = v26
                    v32 = v27
                    v33 = v28
                    v34 = v29
                    v35 = v30
                }
            }
            v36 = v31
            v37 = v32
            v38 = v33
            v39 = v34
            v40 = v35
        }
    }
    return SpiralTuple6(v36, v37, v38, v39, v40)
}
fun sp_info_of_prep_82(p0: US29): SpiralTuple6 {
    var v0: US29 = p0
    var v26: String = ""
    var v27: US48 = US48_RWhole
    var v28: US49 = US49_Entry
    var v29: String = ""
    var v30: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "prepass"
            var v2: US48 = US48_RWhole
            v2 = US48_RPrep
            var v3: US49 = US49_Entry
            v3 = US49_Fn
            var v4: String = ""
            v4 = "prepassPrepass (L7783)"
            var v5: String = ""
            v5 = "propagate (L7163) -> resolve (L7301) -> lower (L7424)"
            v26 = v1
            v27 = v2
            v28 = v3
            v29 = v4
            v30 = v5
        }
        else -> {
            var v21: String = ""
            var v22: US48 = US48_RWhole
            var v23: US49 = US49_Entry
            var v24: String = ""
            var v25: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "prepassadapter"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RPrep
                    var v8: US49 = US49_Entry
                    v8 = US49_Seam
                    var v9: String = ""
                    v9 = "PrepassAdapter (L6742)"
                    var v10: String = ""
                    v10 = "identity seam: memoPatternRef, visitPatternRefOnce"
                    v21 = v6
                    v22 = v7
                    v23 = v8
                    v24 = v9
                    v25 = v10
                }
                else -> {
                    var v16: String = ""
                    var v17: US48 = US48_RWhole
                    var v18: US49 = US49_Entry
                    var v19: String = ""
                    var v20: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "prepenv"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RPrep
                            var v13: US49 = US49_Entry
                            v13 = US49_Data
                            var v14: String = ""
                            v14 = "PartEvalPrepassEnv (L7673)"
                            var v15: String = ""
                            v15 = "add_term / fresh vars / process_term"
                            v16 = v11
                            v17 = v12
                            v18 = v13
                            v19 = v14
                            v20 = v15
                        }
                        else -> throw RuntimeException()
                    }
                    v21 = v16
                    v22 = v17
                    v23 = v18
                    v24 = v19
                    v25 = v20
                }
            }
            v26 = v21
            v27 = v22
            v28 = v23
            v29 = v24
            v30 = v25
        }
    }
    return SpiralTuple6(v26, v27, v28, v29, v30)
}
fun sp_info_of_pe_83(p0: US30): SpiralTuple6 {
    var v0: US30 = p0
    var v56: String = ""
    var v57: US48 = US48_RWhole
    var v58: US49 = US49_Entry
    var v59: String = ""
    var v60: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "peval"
            var v2: US48 = US48_RWhole
            v2 = US48_RPe
            var v3: US49 = US49_Entry
            v3 = US49_Entry
            var v4: String = ""
            v4 = "peval env main (L9705)"
            var v5: String = ""
            v5 = "builds the session, the tables and LangEnv; evaluates main"
            v56 = v1
            v57 = v2
            v58 = v3
            v59 = v4
            v60 = v5
        }
        else -> {
            var v51: String = ""
            var v52: US48 = US48_RWhole
            var v53: US49 = US49_Entry
            var v54: String = ""
            var v55: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "topenv"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RPe
                    var v8: US49 = US49_Entry
                    v8 = US49_Data
                    var v9: String = ""
                    v9 = "PartEvalTopEnv"
                    var v10: String = ""
                    v10 = "prototypes_instances, nominals, backend, inline_restart"
                    v51 = v6
                    v52 = v7
                    v53 = v8
                    v54 = v9
                    v55 = v10
                }
                else -> {
                    var v46: String = ""
                    var v47: US48 = US48_RWhole
                    var v48: US49 = US49_Entry
                    var v49: String = ""
                    var v50: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "langenv"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RPe
                            var v13: US49 = US49_Entry
                            v13 = US49_Data
                            var v14: String = ""
                            v14 = "LangEnv"
                            var v15: String = ""
                            v15 = "trace, seq, cse, i, env stacks, backend, globals (ResizeArray<string>)"
                            v46 = v11
                            v47 = v12
                            v48 = v13
                            v49 = v14
                            v50 = v15
                        }
                        else -> {
                            var v41: String = ""
                            var v42: US48 = US48_RWhole
                            var v43: US49 = US49_Entry
                            var v44: String = ""
                            var v45: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "result"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RPe
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Data
                                    var v19: String = ""
                                    v19 = "PartEvalResult (L9573)"
                                    var v20: String = ""
                                    v20 = "join_point_method, join_point_closure, ty_to_data, globals"
                                    v41 = v16
                                    v42 = v17
                                    v43 = v18
                                    v44 = v19
                                    v45 = v20
                                }
                                else -> {
                                    var v36: String = ""
                                    var v37: US48 = US48_RWhole
                                    var v38: US49 = US49_Entry
                                    var v39: String = ""
                                    var v40: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "termscope"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RPe
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Fn
                                            var v24: String = ""
                                            v24 = "term / term_scope''"
                                            var v25: String = ""
                                            v25 = "the evaluator: one big recursive match over E"
                                            v36 = v21
                                            v37 = v22
                                            v38 = v23
                                            v39 = v24
                                            v40 = v25
                                        }
                                        else -> {
                                            var v31: String = ""
                                            var v32: US48 = US48_RWhole
                                            var v33: US49 = US49_Entry
                                            var v34: String = ""
                                            var v35: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "cse"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RPe
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Fn
                                                    var v29: String = ""
                                                    v29 = "push_typedop + CSE"
                                                    var v30: String = ""
                                                    v30 = "cse_tryfind / cse_add on s.cse"
                                                    v31 = v26
                                                    v32 = v27
                                                    v33 = v28
                                                    v34 = v29
                                                    v35 = v30
                                                }
                                                else -> throw RuntimeException()
                                            }
                                            v36 = v31
                                            v37 = v32
                                            v38 = v33
                                            v39 = v34
                                            v40 = v35
                                        }
                                    }
                                    v41 = v36
                                    v42 = v37
                                    v43 = v38
                                    v44 = v39
                                    v45 = v40
                                }
                            }
                            v46 = v41
                            v47 = v42
                            v48 = v43
                            v49 = v44
                            v50 = v45
                        }
                    }
                    v51 = v46
                    v52 = v47
                    v53 = v48
                    v54 = v49
                    v55 = v50
                }
            }
            v56 = v51
            v57 = v52
            v58 = v53
            v59 = v54
            v60 = v55
        }
    }
    return SpiralTuple6(v56, v57, v58, v59, v60)
}
fun sp_info_of_pe_tables_84(p0: US31): SpiralTuple6 {
    var v0: US31 = p0
    var v46: String = ""
    var v47: US48 = US48_RWhole
    var v48: US49 = US49_Entry
    var v49: String = ""
    var v50: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "jpm"
            var v2: US48 = US48_RWhole
            v2 = US48_RPeTables
            var v3: US49 = US49_Entry
            v3 = US49_Data
            var v4: String = ""
            v4 = "join_point_method"
            var v5: String = ""
            v5 = "Dictionary<(backend, body), Dictionary<key, (seq option, ty option, name)> * HashConsTable>"
            v46 = v1
            v47 = v2
            v48 = v3
            v49 = v4
            v50 = v5
        }
        else -> {
            var v41: String = ""
            var v42: US48 = US48_RWhole
            var v43: US49 = US49_Entry
            var v44: String = ""
            var v45: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "jpc"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RPeTables
                    var v8: US49 = US49_Entry
                    v8 = US49_Data
                    var v9: String = ""
                    v9 = "join_point_closure"
                    var v10: String = ""
                    v10 = "Dictionary<(backend, body), Dictionary<key, (domain, seq) option> * HashConsTable>"
                    v41 = v6
                    v42 = v7
                    v43 = v8
                    v44 = v9
                    v45 = v10
                }
                else -> {
                    var v36: String = ""
                    var v37: US48 = US48_RWhole
                    var v38: US49 = US49_Entry
                    var v39: String = ""
                    var v40: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "jpt"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RPeTables
                            var v13: US49 = US49_Entry
                            v13 = US49_Data
                            var v14: String = ""
                            v14 = "join_point_type"
                            var v15: String = ""
                            v15 = "Dictionary<body, Dictionary<key, Ty option> * HashConsTable>"
                            v36 = v11
                            v37 = v12
                            v38 = v13
                            v39 = v14
                            v40 = v15
                        }
                        else -> {
                            var v31: String = ""
                            var v32: US48 = US48_RWhole
                            var v33: US49 = US49_Entry
                            var v34: String = ""
                            var v35: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "bsv"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RPeTables
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Data
                                    var v19: String = ""
                                    v19 = "backend_switch_validated"
                                    var v20: String = ""
                                    v20 = "Dictionary<(backend, RData[]), Ty> + its HashConsTable"
                                    v31 = v16
                                    v32 = v17
                                    v33 = v18
                                    v34 = v19
                                    v35 = v20
                                }
                                else -> {
                                    var v26: String = ""
                                    var v27: US48 = US48_RWhole
                                    var v28: US49 = US49_Entry
                                    var v29: String = ""
                                    var v30: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "globals"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RPeTables
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Data
                                            var v24: String = ""
                                            v24 = "s.globals"
                                            var v25: String = ""
                                            v25 = "ordered list of emitted global text; GlobalsIndex keeps a HashSet per list"
                                            v26 = v21
                                            v27 = v22
                                            v28 = v23
                                            v29 = v24
                                            v30 = v25
                                        }
                                        else -> throw RuntimeException()
                                    }
                                    v31 = v26
                                    v32 = v27
                                    v33 = v28
                                    v34 = v29
                                    v35 = v30
                                }
                            }
                            v36 = v31
                            v37 = v32
                            v38 = v33
                            v39 = v34
                            v40 = v35
                        }
                    }
                    v41 = v36
                    v42 = v37
                    v43 = v38
                    v44 = v39
                    v45 = v40
                }
            }
            v46 = v41
            v47 = v42
            v48 = v43
            v49 = v44
            v50 = v45
        }
    }
    return SpiralTuple6(v46, v47, v48, v49, v50)
}
fun sp_info_of_pe_ops_85(p0: US32): SpiralTuple6 {
    var v0: US32 = p0
    var v66: String = ""
    var v67: US48 = US48_RWhole
    var v68: US49 = US49_Entry
    var v69: String = ""
    var v70: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "closure"
            var v2: US48 = US48_RWhole
            v2 = US48_RPeOps
            var v3: US49 = US49_Entry
            v3 = US49_Op
            var v4: String = ""
            v4 = "closure_convert (L9770)"
            var v5: String = ""
            v5 = "family -> claim -> announce(true) -> key_body \"closure\" -> defer -> fill"
            v66 = v1
            v67 = v2
            v68 = v3
            v69 = v4
            v70 = v5
        }
        else -> {
            var v61: String = ""
            var v62: US48 = US48_RWhole
            var v63: US49 = US49_Entry
            var v64: String = ""
            var v65: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "tjp"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RPeOps
                    var v8: US49 = US49_Entry
                    v8 = US49_Op
                    var v9: String = ""
                    v9 = "type join point TJoinPoint' (L9955)"
                    var v10: String = ""
                    v10 = "family -> claim -> key_body \"type\" -> ty -> fill (never deferred)"
                    v61 = v6
                    v62 = v7
                    v63 = v8
                    v64 = v9
                    v65 = v10
                }
                else -> {
                    var v56: String = ""
                    var v57: US48 = US48_RWhole
                    var v58: US49 = US49_Entry
                    var v59: String = ""
                    var v60: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "mjp"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RPeOps
                            var v13: US49 = US49_Entry
                            v13 = US49_Op
                            var v14: String = ""
                            v14 = "method join point EJoinPoint' (L10380)"
                            var v15: String = ""
                            v15 = "family -> claim -> announce(annotated?) -> key_body \"method\" -> defer -> fill"
                            v56 = v11
                            v57 = v12
                            v58 = v13
                            v59 = v14
                            v60 = v15
                        }
                        else -> {
                            var v51: String = ""
                            var v52: US48 = US48_RWhole
                            var v53: US49 = US49_Entry
                            var v54: String = ""
                            var v55: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "bswitch"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RPeOps
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Op
                                    var v19: String = ""
                                    v19 = "BackendSwitch validation (L10844)"
                                    var v20: String = ""
                                    v20 = "validated -> read | key_body \"bsv\" -> apply -> fill"
                                    v51 = v16
                                    v52 = v17
                                    v53 = v18
                                    v54 = v19
                                    v55 = v20
                                }
                                else -> {
                                    var v46: String = ""
                                    var v47: US48 = US48_RWhole
                                    var v48: US49 = US49_Entry
                                    var v49: String = ""
                                    var v50: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "uninh"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RPeOps
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Op
                                            var v24: String = ""
                                            v24 = "is_uninhabited (L9939)"
                                            var v25: String = ""
                                            v25 = "nesting counter, limit 32"
                                            v46 = v21
                                            v47 = v22
                                            v48 = v23
                                            v49 = v24
                                            v50 = v25
                                        }
                                        else -> {
                                            var v41: String = ""
                                            var v42: US48 = US48_RWhole
                                            var v43: US49 = US49_Entry
                                            var v44: String = ""
                                            var v45: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "printst"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RPeOps
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Op
                                                    var v29: String = ""
                                                    v29 = "PrintStatic / PrintRaw (L11775)"
                                                    var v30: String = ""
                                                    v30 = "print seam"
                                                    v41 = v26
                                                    v42 = v27
                                                    v43 = v28
                                                    v44 = v29
                                                    v45 = v30
                                                }
                                                else -> {
                                                    var v36: String = ""
                                                    var v37: US48 = US48_RWhole
                                                    var v38: US49 = US49_Entry
                                                    var v39: String = ""
                                                    var v40: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "mainroot"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RPeOps
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Op
                                                            var v34: String = ""
                                                            v34 = "main entry (L12001)"
                                                            var v35: String = ""
                                                            v35 = "root globals (term_scope main ())"
                                                            v36 = v31
                                                            v37 = v32
                                                            v38 = v33
                                                            v39 = v34
                                                            v40 = v35
                                                        }
                                                        else -> throw RuntimeException()
                                                    }
                                                    v41 = v36
                                                    v42 = v37
                                                    v43 = v38
                                                    v44 = v39
                                                    v45 = v40
                                                }
                                            }
                                            v46 = v41
                                            v47 = v42
                                            v48 = v43
                                            v49 = v44
                                            v50 = v45
                                        }
                                    }
                                    v51 = v46
                                    v52 = v47
                                    v53 = v48
                                    v54 = v49
                                    v55 = v50
                                }
                            }
                            v56 = v51
                            v57 = v52
                            v58 = v53
                            v59 = v54
                            v60 = v55
                        }
                    }
                    v61 = v56
                    v62 = v57
                    v63 = v58
                    v64 = v59
                    v65 = v60
                }
            }
            v66 = v61
            v67 = v62
            v68 = v63
            v69 = v64
            v70 = v65
        }
    }
    return SpiralTuple6(v66, v67, v68, v69, v70)
}
fun sp_info_of_pe_guards_86(p0: US33): SpiralTuple6 {
    var v0: US33 = p0
    var v46: String = ""
    var v47: US48 = US48_RWhole
    var v48: US49 = US49_Entry
    var v49: String = ""
    var v50: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "runaway"
            var v2: US48 = US48_RWhole
            v2 = US48_RPeGuards
            var v3: US49 = US49_Entry
            v3 = US49_Fn
            var v4: String = ""
            v4 = "RunawayUnrollDiagnostic (L9585)"
            var v5: String = ""
            v5 = "Rust-style report; ConcurrentDictionary of source lines"
            v46 = v1
            v47 = v2
            v48 = v3
            v49 = v4
            v50 = v5
        }
        else -> {
            var v41: String = ""
            var v42: US48 = US48_RWhole
            var v43: US49 = US49_Entry
            var v44: String = ""
            var v45: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "dynif"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RPeGuards
                    var v8: US49 = US49_Entry
                    v8 = US49_Data
                    var v9: String = ""
                    v9 = "DynamicIfNesting (L9646)"
                    var v10: String = ""
                    v10 = "ThreadLocal depth, SPIRAL_IF_NESTING_LIMIT=1000"
                    v41 = v6
                    v42 = v7
                    v43 = v8
                    v44 = v9
                    v45 = v10
                }
                else -> {
                    var v36: String = ""
                    var v37: US48 = US48_RWhole
                    var v38: US49 = US49_Entry
                    var v39: String = ""
                    var v40: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "unionnest"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RPeGuards
                            var v13: US49 = US49_Entry
                            v13 = US49_Data
                            var v14: String = ""
                            v14 = "UnionNesting (L9653)"
                            var v15: String = ""
                            v15 = "ThreadLocal depth, limit 256, EJP0041"
                            v36 = v11
                            v37 = v12
                            v38 = v13
                            v39 = v14
                            v40 = v15
                        }
                        else -> {
                            var v31: String = ""
                            var v32: US48 = US48_RWhole
                            var v33: US49 = US49_Entry
                            var v34: String = ""
                            var v35: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "gindex"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RPeGuards
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Data
                                    var v19: String = ""
                                    v19 = "GlobalsIndex (L9666)"
                                    var v20: String = ""
                                    v20 = "ConditionalWeakTable<list, HashSet>"
                                    v31 = v16
                                    v32 = v17
                                    v33 = v18
                                    v34 = v19
                                    v35 = v20
                                }
                                else -> {
                                    var v26: String = ""
                                    var v27: US48 = US48_RWhole
                                    var v28: US49 = US49_Entry
                                    var v29: String = ""
                                    var v30: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "rustgap"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RPeGuards
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Data
                                            var v24: String = ""
                                            v24 = "RustGapTrace (L9674)"
                                            var v25: String = ""
                                            v25 = "ThreadLocal validating; ConcurrentDictionary of missing arms"
                                            v26 = v21
                                            v27 = v22
                                            v28 = v23
                                            v29 = v24
                                            v30 = v25
                                        }
                                        else -> throw RuntimeException()
                                    }
                                    v31 = v26
                                    v32 = v27
                                    v33 = v28
                                    v34 = v29
                                    v35 = v30
                                }
                            }
                            v36 = v31
                            v37 = v32
                            v38 = v33
                            v39 = v34
                            v40 = v35
                        }
                    }
                    v41 = v36
                    v42 = v37
                    v43 = v38
                    v44 = v39
                    v45 = v40
                }
            }
            v46 = v41
            v47 = v42
            v48 = v43
            v49 = v44
            v50 = v45
        }
    }
    return SpiralTuple6(v46, v47, v48, v49, v50)
}
fun sp_info_of_seams_87(p0: US34): SpiralTuple6 {
    var v0: US34 = p0
    var v176: String = ""
    var v177: US48 = US48_RWhole
    var v178: US49 = US49_Entry
    var v179: String = ""
    var v180: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "adapter"
            var v2: US48 = US48_RWhole
            v2 = US48_RSeams
            var v3: US49 = US49_Entry
            v3 = US49_Type
            var v4: String = ""
            v4 = "Adapter<'env>"
            var v5: String = ""
            v5 = "globals, with_globals, dynamic_if/set, validating/set, rebuild_index"
            v176 = v1
            v177 = v2
            v178 = v3
            v179 = v4
            v180 = v5
        }
        else -> {
            var v171: String = ""
            var v172: US48 = US48_RWhole
            var v173: US49 = US49_Entry
            var v174: String = ""
            var v175: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "s_session"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RSeams
                    var v8: US49 = US49_Entry
                    v8 = US49_Type
                    var v9: String = ""
                    v9 = "Session<'env>"
                    var v10: String = ""
                    v10 = "single-flight: Inline   |   hopacsota: class or null"
                    v171 = v6
                    v172 = v7
                    v173 = v8
                    v174 = v9
                    v175 = v10
                }
                else -> {
                    var v166: String = ""
                    var v167: US48 = US48_RWhole
                    var v168: US49 = US49_Entry
                    var v169: String = ""
                    var v170: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "s_claimty"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RSeams
                            var v13: US49 = US49_Entry
                            v13 = US49_Type
                            var v14: String = ""
                            v14 = "Claim<'v> = Found 'v"
                            var v15: String = ""
                            v15 = "Owner | who evaluates a key"
                            v166 = v11
                            v167 = v12
                            v168 = v13
                            v169 = v14
                            v170 = v15
                        }
                        else -> {
                            var v161: String = ""
                            var v162: US48 = US48_RWhole
                            var v163: US49 = US49_Entry
                            var v164: String = ""
                            var v165: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "s_start"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RSeams
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Api
                                    var v19: String = ""
                                    v19 = "start_for inline_restart adapter"
                                    var v20: String = ""
                                    v20 = "open a session for this peval"
                                    v161 = v16
                                    v162 = v17
                                    v163 = v18
                                    v164 = v19
                                    v165 = v20
                                }
                                else -> {
                                    var v156: String = ""
                                    var v157: US48 = US48_RWhole
                                    var v158: US49 = US49_Entry
                                    var v159: String = ""
                                    var v160: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "s_restart"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RSeams
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Api
                                            var v24: String = ""
                                            v24 = "with_conflict_restart f"
                                            var v25: String = ""
                                            v25 = "f false, or f true after a conflict"
                                            v156 = v21
                                            v157 = v22
                                            v158 = v23
                                            v159 = v24
                                            v160 = v25
                                        }
                                        else -> {
                                            var v151: String = ""
                                            var v152: US48 = US48_RWhole
                                            var v153: US49 = US49_Entry
                                            var v154: String = ""
                                            var v155: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "s_root"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RSeams
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Api
                                                    var v29: String = ""
                                                    v29 = "root session globals f"
                                                    var v30: String = ""
                                                    v30 = "run main, then settle"
                                                    v151 = v26
                                                    v152 = v27
                                                    v153 = v28
                                                    v154 = v29
                                                    v155 = v30
                                                }
                                                else -> {
                                                    var v146: String = ""
                                                    var v147: US48 = US48_RWhole
                                                    var v148: US49 = US49_Entry
                                                    var v149: String = ""
                                                    var v150: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "s_defer"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RSeams
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Api
                                                            var v34: String = ""
                                                            v34 = "defer session s relative deferred evaluate"
                                                            var v35: String = ""
                                                            v35 = "evaluate a join point body now or later"
                                                            v146 = v31
                                                            v147 = v32
                                                            v148 = v33
                                                            v149 = v34
                                                            v150 = v35
                                                        }
                                                        else -> {
                                                            var v141: String = ""
                                                            var v142: US48 = US48_RWhole
                                                            var v143: US49 = US49_Entry
                                                            var v144: String = ""
                                                            var v145: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "s_claim"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RSeams
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Api
                                                                    var v39: String = ""
                                                                    v39 = "claim session table key pending"
                                                                    var v40: String = ""
                                                                    v40 = "Found value or Owner"
                                                                    v141 = v36
                                                                    v142 = v37
                                                                    v143 = v38
                                                                    v144 = v39
                                                                    v145 = v40
                                                                }
                                                                else -> {
                                                                    var v136: String = ""
                                                                    var v137: US48 = US48_RWhole
                                                                    var v138: US49 = US49_Entry
                                                                    var v139: String = ""
                                                                    var v140: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v41: String = ""
                                                                            v41 = "s_announce"
                                                                            var v42: US48 = US48_RWhole
                                                                            v42 = US48_RSeams
                                                                            var v43: US49 = US49_Entry
                                                                            v43 = US49_Api
                                                                            var v44: String = ""
                                                                            v44 = "announce session usable table key value"
                                                                            var v45: String = ""
                                                                            v45 = "publish a usable signature early"
                                                                            v136 = v41
                                                                            v137 = v42
                                                                            v138 = v43
                                                                            v139 = v44
                                                                            v140 = v45
                                                                        }
                                                                        else -> {
                                                                            var v131: String = ""
                                                                            var v132: US48 = US48_RWhole
                                                                            var v133: US49 = US49_Entry
                                                                            var v134: String = ""
                                                                            var v135: String = ""
                                                                            when (v0.tag) {
                                                                                9 -> {
                                                                                    var v46: String = ""
                                                                                    v46 = "s_fill"
                                                                                    var v47: US48 = US48_RWhole
                                                                                    v47 = US48_RSeams
                                                                                    var v48: US49 = US49_Entry
                                                                                    v48 = US49_Api
                                                                                    var v49: String = ""
                                                                                    v49 = "fill session table key value"
                                                                                    var v50: String = ""
                                                                                    v50 = "publish the finished value"
                                                                                    v131 = v46
                                                                                    v132 = v47
                                                                                    v133 = v48
                                                                                    v134 = v49
                                                                                    v135 = v50
                                                                                }
                                                                                else -> {
                                                                                    var v126: String = ""
                                                                                    var v127: US48 = US48_RWhole
                                                                                    var v128: US49 = US49_Entry
                                                                                    var v129: String = ""
                                                                                    var v130: String = ""
                                                                                    when (v0.tag) {
                                                                                        10 -> {
                                                                                            var v51: String = ""
                                                                                            v51 = "s_family"
                                                                                            var v52: US48 = US48_RWhole
                                                                                            v52 = US48_RSeams
                                                                                            var v53: US49 = US49_Entry
                                                                                            v53 = US49_Api
                                                                                            var v54: String = ""
                                                                                            v54 = "family session outer create key"
                                                                                            var v55: String = ""
                                                                                            v55 = "per-(backend, body) inner table"
                                                                                            v126 = v51
                                                                                            v127 = v52
                                                                                            v128 = v53
                                                                                            v129 = v54
                                                                                            v130 = v55
                                                                                        }
                                                                                        else -> {
                                                                                            var v121: String = ""
                                                                                            var v122: US48 = US48_RWhole
                                                                                            var v123: US49 = US49_Entry
                                                                                            var v124: String = ""
                                                                                            var v125: String = ""
                                                                                            when (v0.tag) {
                                                                                                11 -> {
                                                                                                    var v56: String = ""
                                                                                                    v56 = "s_keyhit"
                                                                                                    var v57: US48 = US48_RWhole
                                                                                                    v57 = US48_RSeams
                                                                                                    var v58: US49 = US49_Entry
                                                                                                    v58 = US49_Api
                                                                                                    var v59: String = ""
                                                                                                    v59 = "key_hit session key s"
                                                                                                    var v60: String = ""
                                                                                                    v60 = "a reuse of a finished key"
                                                                                                    v121 = v56
                                                                                                    v122 = v57
                                                                                                    v123 = v58
                                                                                                    v124 = v59
                                                                                                    v125 = v60
                                                                                                }
                                                                                                else -> {
                                                                                                    var v116: String = ""
                                                                                                    var v117: US48 = US48_RWhole
                                                                                                    var v118: US49 = US49_Entry
                                                                                                    var v119: String = ""
                                                                                                    var v120: String = ""
                                                                                                    when (v0.tag) {
                                                                                                        12 -> {
                                                                                                            var v61: String = ""
                                                                                                            v61 = "s_keybody"
                                                                                                            var v62: US48 = US48_RWhole
                                                                                                            v62 = US48_RSeams
                                                                                                            var v63: US49 = US49_Entry
                                                                                                            v63 = US49_Api
                                                                                                            var v64: String = ""
                                                                                                            v64 = "key_body session key label s"
                                                                                                            var v65: String = ""
                                                                                                            v65 = "the env that evaluates a key's body"
                                                                                                            v116 = v61
                                                                                                            v117 = v62
                                                                                                            v118 = v63
                                                                                                            v119 = v64
                                                                                                            v120 = v65
                                                                                                        }
                                                                                                        else -> {
                                                                                                            var v111: String = ""
                                                                                                            var v112: US48 = US48_RWhole
                                                                                                            var v113: US49 = US49_Entry
                                                                                                            var v114: String = ""
                                                                                                            var v115: String = ""
                                                                                                            when (v0.tag) {
                                                                                                                13 -> {
                                                                                                                    var v66: String = ""
                                                                                                                    v66 = "s_print"
                                                                                                                    var v67: US48 = US48_RWhole
                                                                                                                    v67 = US48_RSeams
                                                                                                                    var v68: US49 = US49_Entry
                                                                                                                    v68 = US49_Api
                                                                                                                    var v69: String = ""
                                                                                                                    v69 = "print session s text"
                                                                                                                    var v70: String = ""
                                                                                                                    v70 = "compile-time printing"
                                                                                                                    v111 = v66
                                                                                                                    v112 = v67
                                                                                                                    v113 = v68
                                                                                                                    v114 = v69
                                                                                                                    v115 = v70
                                                                                                                }
                                                                                                                else -> {
                                                                                                                    var v106: String = ""
                                                                                                                    var v107: US48 = US48_RWhole
                                                                                                                    var v108: US49 = US49_Entry
                                                                                                                    var v109: String = ""
                                                                                                                    var v110: String = ""
                                                                                                                    when (v0.tag) {
                                                                                                                        14 -> {
                                                                                                                            var v71: String = ""
                                                                                                                            v71 = "s_nesting"
                                                                                                                            var v72: US48 = US48_RWhole
                                                                                                                            v72 = US48_RSeams
                                                                                                                            var v73: US49 = US49_Entry
                                                                                                                            v73 = US49_Api
                                                                                                                            var v74: String = ""
                                                                                                                            v74 = "nesting session shared"
                                                                                                                            var v75: String = ""
                                                                                                                            v75 = "which counter is_uninhabited uses"
                                                                                                                            v106 = v71
                                                                                                                            v107 = v72
                                                                                                                            v108 = v73
                                                                                                                            v109 = v74
                                                                                                                            v110 = v75
                                                                                                                        }
                                                                                                                        else -> {
                                                                                                                            var v101: String = ""
                                                                                                                            var v102: US48 = US48_RWhole
                                                                                                                            var v103: US49 = US49_Entry
                                                                                                                            var v104: String = ""
                                                                                                                            var v105: String = ""
                                                                                                                            when (v0.tag) {
                                                                                                                                15 -> {
                                                                                                                                    var v76: String = ""
                                                                                                                                    v76 = "s_validated"
                                                                                                                                    var v77: US48 = US48_RWhole
                                                                                                                                    v77 = US48_RSeams
                                                                                                                                    var v78: US49 = US49_Entry
                                                                                                                                    v78 = US49_Api
                                                                                                                                    var v79: String = ""
                                                                                                                                    v79 = "validated session table key"
                                                                                                                                    var v80: String = ""
                                                                                                                                    v80 = "backend switch type already known?"
                                                                                                                                    v101 = v76
                                                                                                                                    v102 = v77
                                                                                                                                    v103 = v78
                                                                                                                                    v104 = v79
                                                                                                                                    v105 = v80
                                                                                                                                }
                                                                                                                                else -> {
                                                                                                                                    var v96: String = ""
                                                                                                                                    var v97: US48 = US48_RWhole
                                                                                                                                    var v98: US49 = US49_Entry
                                                                                                                                    var v99: String = ""
                                                                                                                                    var v100: String = ""
                                                                                                                                    when (v0.tag) {
                                                                                                                                        16 -> {
                                                                                                                                            var v81: String = ""
                                                                                                                                            v81 = "s_read"
                                                                                                                                            var v82: US48 = US48_RWhole
                                                                                                                                            v82 = US48_RSeams
                                                                                                                                            var v83: US49 = US49_Entry
                                                                                                                                            v83 = US49_Api
                                                                                                                                            var v84: String = ""
                                                                                                                                            v84 = "read session table key"
                                                                                                                                            var v85: String = ""
                                                                                                                                            v85 = "read a validated type"
                                                                                                                                            v96 = v81
                                                                                                                                            v97 = v82
                                                                                                                                            v98 = v83
                                                                                                                                            v99 = v84
                                                                                                                                            v100 = v85
                                                                                                                                        }
                                                                                                                                        else -> {
                                                                                                                                            var v91: String = ""
                                                                                                                                            var v92: US48 = US48_RWhole
                                                                                                                                            var v93: US49 = US49_Entry
                                                                                                                                            var v94: String = ""
                                                                                                                                            var v95: String = ""
                                                                                                                                            when (v0.tag) {
                                                                                                                                                17 -> {
                                                                                                                                                    var v86: String = ""
                                                                                                                                                    v86 = "s_active"
                                                                                                                                                    var v87: US48 = US48_RWhole
                                                                                                                                                    v87 = US48_RSeams
                                                                                                                                                    var v88: US49 = US49_Entry
                                                                                                                                                    v88 = US49_Api
                                                                                                                                                    var v89: String = ""
                                                                                                                                                    v89 = "active session"
                                                                                                                                                    var v90: String = ""
                                                                                                                                                    v90 = "is a parallel session running?"
                                                                                                                                                    v91 = v86
                                                                                                                                                    v92 = v87
                                                                                                                                                    v93 = v88
                                                                                                                                                    v94 = v89
                                                                                                                                                    v95 = v90
                                                                                                                                                }
                                                                                                                                                else -> throw RuntimeException()
                                                                                                                                            }
                                                                                                                                            v96 = v91
                                                                                                                                            v97 = v92
                                                                                                                                            v98 = v93
                                                                                                                                            v99 = v94
                                                                                                                                            v100 = v95
                                                                                                                                        }
                                                                                                                                    }
                                                                                                                                    v101 = v96
                                                                                                                                    v102 = v97
                                                                                                                                    v103 = v98
                                                                                                                                    v104 = v99
                                                                                                                                    v105 = v100
                                                                                                                                }
                                                                                                                            }
                                                                                                                            v106 = v101
                                                                                                                            v107 = v102
                                                                                                                            v108 = v103
                                                                                                                            v109 = v104
                                                                                                                            v110 = v105
                                                                                                                        }
                                                                                                                    }
                                                                                                                    v111 = v106
                                                                                                                    v112 = v107
                                                                                                                    v113 = v108
                                                                                                                    v114 = v109
                                                                                                                    v115 = v110
                                                                                                                }
                                                                                                            }
                                                                                                            v116 = v111
                                                                                                            v117 = v112
                                                                                                            v118 = v113
                                                                                                            v119 = v114
                                                                                                            v120 = v115
                                                                                                        }
                                                                                                    }
                                                                                                    v121 = v116
                                                                                                    v122 = v117
                                                                                                    v123 = v118
                                                                                                    v124 = v119
                                                                                                    v125 = v120
                                                                                                }
                                                                                            }
                                                                                            v126 = v121
                                                                                            v127 = v122
                                                                                            v128 = v123
                                                                                            v129 = v124
                                                                                            v130 = v125
                                                                                        }
                                                                                    }
                                                                                    v131 = v126
                                                                                    v132 = v127
                                                                                    v133 = v128
                                                                                    v134 = v129
                                                                                    v135 = v130
                                                                                }
                                                                            }
                                                                            v136 = v131
                                                                            v137 = v132
                                                                            v138 = v133
                                                                            v139 = v134
                                                                            v140 = v135
                                                                        }
                                                                    }
                                                                    v141 = v136
                                                                    v142 = v137
                                                                    v143 = v138
                                                                    v144 = v139
                                                                    v145 = v140
                                                                }
                                                            }
                                                            v146 = v141
                                                            v147 = v142
                                                            v148 = v143
                                                            v149 = v144
                                                            v150 = v145
                                                        }
                                                    }
                                                    v151 = v146
                                                    v152 = v147
                                                    v153 = v148
                                                    v154 = v149
                                                    v155 = v150
                                                }
                                            }
                                            v156 = v151
                                            v157 = v152
                                            v158 = v153
                                            v159 = v154
                                            v160 = v155
                                        }
                                    }
                                    v161 = v156
                                    v162 = v157
                                    v163 = v158
                                    v164 = v159
                                    v165 = v160
                                }
                            }
                            v166 = v161
                            v167 = v162
                            v168 = v163
                            v169 = v164
                            v170 = v165
                        }
                    }
                    v171 = v166
                    v172 = v167
                    v173 = v168
                    v174 = v169
                    v175 = v170
                }
            }
            v176 = v171
            v177 = v172
            v178 = v173
            v179 = v174
            v180 = v175
        }
    }
    return SpiralTuple6(v176, v177, v178, v179, v180)
}
fun sp_info_of_sf_88(p0: US35): SpiralTuple6 {
    var v0: US35 = p0
    var v156: String = ""
    var v157: US48 = US48_RWhole
    var v158: US49 = US49_Entry
    var v159: String = ""
    var v160: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "sf_session"
            var v2: US48 = US48_RWhole
            v2 = US48_RSf
            var v3: US49 = US49_Entry
            v3 = US49_Impl
            var v4: String = ""
            v4 = "Session = Inline"
            var v5: String = ""
            v5 = "a one-case union; no state at all"
            v156 = v1
            v157 = v2
            v158 = v3
            v159 = v4
            v160 = v5
        }
        else -> {
            var v151: String = ""
            var v152: US48 = US48_RWhole
            var v153: US49 = US49_Entry
            var v154: String = ""
            var v155: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "sf_start"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RSf
                    var v8: US49 = US49_Entry
                    v8 = US49_Impl
                    var v9: String = ""
                    v9 = "start_for = Inline"
                    var v10: String = ""
                    v10 = "inline_restart is ignored"
                    v151 = v6
                    v152 = v7
                    v153 = v8
                    v154 = v9
                    v155 = v10
                }
                else -> {
                    var v146: String = ""
                    var v147: US48 = US48_RWhole
                    var v148: US49 = US49_Entry
                    var v149: String = ""
                    var v150: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "sf_restart"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RSf
                            var v13: US49 = US49_Entry
                            v13 = US49_Impl
                            var v14: String = ""
                            v14 = "with_conflict_restart = f false"
                            var v15: String = ""
                            v15 = "a conflict cannot happen"
                            v146 = v11
                            v147 = v12
                            v148 = v13
                            v149 = v14
                            v150 = v15
                        }
                        else -> {
                            var v141: String = ""
                            var v142: US48 = US48_RWhole
                            var v143: US49 = US49_Entry
                            var v144: String = ""
                            var v145: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "sf_root"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RSf
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Impl
                                    var v19: String = ""
                                    v19 = "root = f ()"
                                    var v20: String = ""
                                    v20 = "main runs to completion on the build thread"
                                    v141 = v16
                                    v142 = v17
                                    v143 = v18
                                    v144 = v19
                                    v145 = v20
                                }
                                else -> {
                                    var v136: String = ""
                                    var v137: US48 = US48_RWhole
                                    var v138: US49 = US49_Entry
                                    var v139: String = ""
                                    var v140: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "sf_defer"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RSf
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Impl
                                            var v24: String = ""
                                            v24 = "defer = evaluate s"
                                            var v25: String = ""
                                            v25 = "the body is evaluated immediately, depth first, in the current env"
                                            v136 = v21
                                            v137 = v22
                                            v138 = v23
                                            v139 = v24
                                            v140 = v25
                                        }
                                        else -> {
                                            var v131: String = ""
                                            var v132: US48 = US48_RWhole
                                            var v133: US49 = US49_Entry
                                            var v134: String = ""
                                            var v135: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "sf_claim"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RSf
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Impl
                                                    var v29: String = ""
                                                    v29 = "claim = TryGetValue, else table.[key] <- pending; Owner"
                                                    var v30: String = ""
                                                    v30 = "pending marks 'in progress' for recursion"
                                                    v131 = v26
                                                    v132 = v27
                                                    v133 = v28
                                                    v134 = v29
                                                    v135 = v30
                                                }
                                                else -> {
                                                    var v126: String = ""
                                                    var v127: US48 = US48_RWhole
                                                    var v128: US49 = US49_Entry
                                                    var v129: String = ""
                                                    var v130: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "sf_announce"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RSf
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Impl
                                                            var v34: String = ""
                                                            v34 = "announce = table.[key] <- value"
                                                            var v35: String = ""
                                                            v35 = "just a write"
                                                            v126 = v31
                                                            v127 = v32
                                                            v128 = v33
                                                            v129 = v34
                                                            v130 = v35
                                                        }
                                                        else -> {
                                                            var v121: String = ""
                                                            var v122: US48 = US48_RWhole
                                                            var v123: US49 = US49_Entry
                                                            var v124: String = ""
                                                            var v125: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "sf_fill"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RSf
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Impl
                                                                    var v39: String = ""
                                                                    v39 = "fill = table.[key] <- value"
                                                                    var v40: String = ""
                                                                    v40 = "just a write"
                                                                    v121 = v36
                                                                    v122 = v37
                                                                    v123 = v38
                                                                    v124 = v39
                                                                    v125 = v40
                                                                }
                                                                else -> {
                                                                    var v116: String = ""
                                                                    var v117: US48 = US48_RWhole
                                                                    var v118: US49 = US49_Entry
                                                                    var v119: String = ""
                                                                    var v120: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v41: String = ""
                                                                            v41 = "sf_family"
                                                                            var v42: US48 = US48_RWhole
                                                                            v42 = US48_RSf
                                                                            var v43: US49 = US49_Entry
                                                                            v43 = US49_Impl
                                                                            var v44: String = ""
                                                                            v44 = "family = Utils.memoize outer create key"
                                                                            var v45: String = ""
                                                                            v45 = "one shared inner table"
                                                                            v116 = v41
                                                                            v117 = v42
                                                                            v118 = v43
                                                                            v119 = v44
                                                                            v120 = v45
                                                                        }
                                                                        else -> {
                                                                            var v111: String = ""
                                                                            var v112: US48 = US48_RWhole
                                                                            var v113: US49 = US49_Entry
                                                                            var v114: String = ""
                                                                            var v115: String = ""
                                                                            when (v0.tag) {
                                                                                9 -> {
                                                                                    var v46: String = ""
                                                                                    v46 = "sf_keyhit"
                                                                                    var v47: US48 = US48_RWhole
                                                                                    v47 = US48_RSf
                                                                                    var v48: US49 = US49_Entry
                                                                                    v48 = US49_Impl
                                                                                    var v49: String = ""
                                                                                    v49 = "key_hit = ()"
                                                                                    var v50: String = ""
                                                                                    v50 = "no markers"
                                                                                    v111 = v46
                                                                                    v112 = v47
                                                                                    v113 = v48
                                                                                    v114 = v49
                                                                                    v115 = v50
                                                                                }
                                                                                else -> {
                                                                                    var v106: String = ""
                                                                                    var v107: US48 = US48_RWhole
                                                                                    var v108: US49 = US49_Entry
                                                                                    var v109: String = ""
                                                                                    var v110: String = ""
                                                                                    when (v0.tag) {
                                                                                        10 -> {
                                                                                            var v51: String = ""
                                                                                            v51 = "sf_keybody"
                                                                                            var v52: US48 = US48_RWhole
                                                                                            v52 = US48_RSf
                                                                                            var v53: US49 = US49_Entry
                                                                                            v53 = US49_Impl
                                                                                            var v54: String = ""
                                                                                            v54 = "key_body = s"
                                                                                            var v55: String = ""
                                                                                            v55 = "globals untouched"
                                                                                            v106 = v51
                                                                                            v107 = v52
                                                                                            v108 = v53
                                                                                            v109 = v54
                                                                                            v110 = v55
                                                                                        }
                                                                                        else -> {
                                                                                            var v101: String = ""
                                                                                            var v102: US48 = US48_RWhole
                                                                                            var v103: US49 = US49_Entry
                                                                                            var v104: String = ""
                                                                                            var v105: String = ""
                                                                                            when (v0.tag) {
                                                                                                11 -> {
                                                                                                    var v56: String = ""
                                                                                                    v56 = "sf_print"
                                                                                                    var v57: US48 = US48_RWhole
                                                                                                    v57 = US48_RSf
                                                                                                    var v58: US49 = US49_Entry
                                                                                                    v58 = US49_Impl
                                                                                                    var v59: String = ""
                                                                                                    v59 = "print = printfn"
                                                                                                    var v60: String = ""
                                                                                                    v60 = "printed at the moment of evaluation"
                                                                                                    v101 = v56
                                                                                                    v102 = v57
                                                                                                    v103 = v58
                                                                                                    v104 = v59
                                                                                                    v105 = v60
                                                                                                }
                                                                                                else -> {
                                                                                                    var v96: String = ""
                                                                                                    var v97: US48 = US48_RWhole
                                                                                                    var v98: US49 = US49_Entry
                                                                                                    var v99: String = ""
                                                                                                    var v100: String = ""
                                                                                                    when (v0.tag) {
                                                                                                        12 -> {
                                                                                                            var v61: String = ""
                                                                                                            v61 = "sf_nesting"
                                                                                                            var v62: US48 = US48_RWhole
                                                                                                            v62 = US48_RSf
                                                                                                            var v63: US49 = US49_Entry
                                                                                                            v63 = US49_Impl
                                                                                                            var v64: String = ""
                                                                                                            v64 = "nesting = shared ref"
                                                                                                            var v65: String = ""
                                                                                                            v65 = "one counter per peval"
                                                                                                            v96 = v61
                                                                                                            v97 = v62
                                                                                                            v98 = v63
                                                                                                            v99 = v64
                                                                                                            v100 = v65
                                                                                                        }
                                                                                                        else -> {
                                                                                                            var v91: String = ""
                                                                                                            var v92: US48 = US48_RWhole
                                                                                                            var v93: US49 = US49_Entry
                                                                                                            var v94: String = ""
                                                                                                            var v95: String = ""
                                                                                                            when (v0.tag) {
                                                                                                                13 -> {
                                                                                                                    var v66: String = ""
                                                                                                                    v66 = "sf_validated"
                                                                                                                    var v67: US48 = US48_RWhole
                                                                                                                    v67 = US48_RSf
                                                                                                                    var v68: US49 = US49_Entry
                                                                                                                    v68 = US49_Impl
                                                                                                                    var v69: String = ""
                                                                                                                    v69 = "validated = ContainsKey |"
                                                                                                                    var v70: String = ""
                                                                                                                    v70 = ""
                                                                                                                    v91 = v66
                                                                                                                    v92 = v67
                                                                                                                    v93 = v68
                                                                                                                    v94 = v69
                                                                                                                    v95 = v70
                                                                                                                }
                                                                                                                else -> {
                                                                                                                    var v86: String = ""
                                                                                                                    var v87: US48 = US48_RWhole
                                                                                                                    var v88: US49 = US49_Entry
                                                                                                                    var v89: String = ""
                                                                                                                    var v90: String = ""
                                                                                                                    when (v0.tag) {
                                                                                                                        14 -> {
                                                                                                                            var v71: String = ""
                                                                                                                            v71 = "sf_read"
                                                                                                                            var v72: US48 = US48_RWhole
                                                                                                                            v72 = US48_RSf
                                                                                                                            var v73: US49 = US49_Entry
                                                                                                                            v73 = US49_Impl
                                                                                                                            var v74: String = ""
                                                                                                                            v74 = "read = table.[key] |"
                                                                                                                            var v75: String = ""
                                                                                                                            v75 = ""
                                                                                                                            v86 = v71
                                                                                                                            v87 = v72
                                                                                                                            v88 = v73
                                                                                                                            v89 = v74
                                                                                                                            v90 = v75
                                                                                                                        }
                                                                                                                        else -> {
                                                                                                                            var v81: String = ""
                                                                                                                            var v82: US48 = US48_RWhole
                                                                                                                            var v83: US49 = US49_Entry
                                                                                                                            var v84: String = ""
                                                                                                                            var v85: String = ""
                                                                                                                            when (v0.tag) {
                                                                                                                                15 -> {
                                                                                                                                    var v76: String = ""
                                                                                                                                    v76 = "sf_active"
                                                                                                                                    var v77: US48 = US48_RWhole
                                                                                                                                    v77 = US48_RSf
                                                                                                                                    var v78: US49 = US49_Entry
                                                                                                                                    v78 = US49_Impl
                                                                                                                                    var v79: String = ""
                                                                                                                                    v79 = "active = false"
                                                                                                                                    var v80: String = ""
                                                                                                                                    v80 = "validation globals come from validation_root_globals"
                                                                                                                                    v81 = v76
                                                                                                                                    v82 = v77
                                                                                                                                    v83 = v78
                                                                                                                                    v84 = v79
                                                                                                                                    v85 = v80
                                                                                                                                }
                                                                                                                                else -> throw RuntimeException()
                                                                                                                            }
                                                                                                                            v86 = v81
                                                                                                                            v87 = v82
                                                                                                                            v88 = v83
                                                                                                                            v89 = v84
                                                                                                                            v90 = v85
                                                                                                                        }
                                                                                                                    }
                                                                                                                    v91 = v86
                                                                                                                    v92 = v87
                                                                                                                    v93 = v88
                                                                                                                    v94 = v89
                                                                                                                    v95 = v90
                                                                                                                }
                                                                                                            }
                                                                                                            v96 = v91
                                                                                                            v97 = v92
                                                                                                            v98 = v93
                                                                                                            v99 = v94
                                                                                                            v100 = v95
                                                                                                        }
                                                                                                    }
                                                                                                    v101 = v96
                                                                                                    v102 = v97
                                                                                                    v103 = v98
                                                                                                    v104 = v99
                                                                                                    v105 = v100
                                                                                                }
                                                                                            }
                                                                                            v106 = v101
                                                                                            v107 = v102
                                                                                            v108 = v103
                                                                                            v109 = v104
                                                                                            v110 = v105
                                                                                        }
                                                                                    }
                                                                                    v111 = v106
                                                                                    v112 = v107
                                                                                    v113 = v108
                                                                                    v114 = v109
                                                                                    v115 = v110
                                                                                }
                                                                            }
                                                                            v116 = v111
                                                                            v117 = v112
                                                                            v118 = v113
                                                                            v119 = v114
                                                                            v120 = v115
                                                                        }
                                                                    }
                                                                    v121 = v116
                                                                    v122 = v117
                                                                    v123 = v118
                                                                    v124 = v119
                                                                    v125 = v120
                                                                }
                                                            }
                                                            v126 = v121
                                                            v127 = v122
                                                            v128 = v123
                                                            v129 = v124
                                                            v130 = v125
                                                        }
                                                    }
                                                    v131 = v126
                                                    v132 = v127
                                                    v133 = v128
                                                    v134 = v129
                                                    v135 = v130
                                                }
                                            }
                                            v136 = v131
                                            v137 = v132
                                            v138 = v133
                                            v139 = v134
                                            v140 = v135
                                        }
                                    }
                                    v141 = v136
                                    v142 = v137
                                    v143 = v138
                                    v144 = v139
                                    v145 = v140
                                }
                            }
                            v146 = v141
                            v147 = v142
                            v148 = v143
                            v149 = v144
                            v150 = v145
                        }
                    }
                    v151 = v146
                    v152 = v147
                    v153 = v148
                    v154 = v149
                    v155 = v150
                }
            }
            v156 = v151
            v157 = v152
            v158 = v153
            v159 = v154
            v160 = v155
        }
    }
    return SpiralTuple6(v156, v157, v158, v159, v160)
}
fun sp_info_of_hs_seams_89(p0: US36): SpiralTuple6 {
    var v0: US36 = p0
    var v146: String = ""
    var v147: US48 = US48_RWhole
    var v148: US49 = US49_Entry
    var v149: String = ""
    var v150: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "hs_policy"
            var v2: US48 = US48_RWhole
            v2 = US48_RHsSeams
            var v3: US49 = US49_Entry
            v3 = US49_Cfg
            var v4: String = ""
            v4 = "Policy"
            var v5: String = ""
            v5 = "DepthFirst | BreadthFirst | Seeded seed | Parallel; None = inline (no session)"
            v146 = v1
            v147 = v2
            v148 = v3
            v149 = v4
            v150 = v5
        }
        else -> {
            var v141: String = ""
            var v142: US48 = US48_RWhole
            var v143: US49 = US49_Entry
            var v144: String = ""
            var v145: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "hs_session"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RHsSeams
                    var v8: US49 = US49_Entry
                    v8 = US49_Class
                    var v9: String = ""
                    v9 = "Session<'env>(policy, adapter)"
                    var v10: String = ""
                    v10 = "queue, own Marks, seeded Random, engine (null unless Parallel), census"
                    v141 = v6
                    v142 = v7
                    v143 = v8
                    v144 = v9
                    v145 = v10
                }
                else -> {
                    var v136: String = ""
                    var v137: US48 = US48_RWhole
                    var v138: US49 = US49_Entry
                    var v139: String = ""
                    var v140: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "hs_start"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RHsSeams
                            var v13: US49 = US49_Entry
                            v13 = US49_Impl
                            var v14: String = ""
                            v14 = "start / start_for"
                            var v15: String = ""
                            v15 = "inline_restart -> null session; policy None -> null"
                            v136 = v11
                            v137 = v12
                            v138 = v13
                            v139 = v14
                            v140 = v15
                        }
                        else -> {
                            var v131: String = ""
                            var v132: US48 = US48_RWhole
                            var v133: US49 = US49_Entry
                            var v134: String = ""
                            var v135: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "hs_restart"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RHsSeams
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Impl
                                    var v19: String = ""
                                    v19 = "with_conflict_restart"
                                    var v20: String = ""
                                    v20 = "try f false; on HopacsotaConflict: eprintfn ... restart in canonical single-flight order (D4); f true"
                                    v131 = v16
                                    v132 = v17
                                    v133 = v18
                                    v134 = v19
                                    v135 = v20
                                }
                                else -> {
                                    var v126: String = ""
                                    var v127: US48 = US48_RWhole
                                    var v128: US49 = US49_Entry
                                    var v129: String = ""
                                    var v130: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "hs_root"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RHsSeams
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Impl
                                            var v24: String = ""
                                            v24 = "Session.Root"
                                            var v25: String = ""
                                            v25 = "Parallel -> new Engine(evaluating_threads); f(); Finished; Drain; census; Check; Finish; Close"
                                            v126 = v21
                                            v127 = v22
                                            v128 = v23
                                            v129 = v24
                                            v130 = v25
                                        }
                                        else -> {
                                            var v121: String = ""
                                            var v122: US48 = US48_RWhole
                                            var v123: US49 = US49_Entry
                                            var v124: String = ""
                                            var v125: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "hs_defer"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RHsSeams
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Impl
                                                    var v29: String = ""
                                                    v29 = "Session.Defer"
                                                    var v30: String = ""
                                                    v30 = "BFS/Seeded/Parallel with an annotation: new 'globals' marker + child list, body closure; returns the annotated result at once"
                                                    v121 = v26
                                                    v122 = v27
                                                    v123 = v28
                                                    v124 = v29
                                                    v125 = v30
                                                }
                                                else -> {
                                                    var v116: String = ""
                                                    var v117: US48 = US48_RWhole
                                                    var v118: US49 = US49_Entry
                                                    var v119: String = ""
                                                    var v120: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "hs_body"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RHsSeams
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Impl
                                                            var v34: String = ""
                                                            v34 = "deferred body closure"
                                                            var v35: String = ""
                                                            v35 = "saves/zeroes dynamic_if + validating, evaluate body_env, failure -> 'fail' marker, restores, census row"
                                                            v116 = v31
                                                            v117 = v32
                                                            v118 = v33
                                                            v119 = v34
                                                            v120 = v35
                                                        }
                                                        else -> {
                                                            var v111: String = ""
                                                            var v112: US48 = US48_RWhole
                                                            var v113: US49 = US49_Entry
                                                            var v114: String = ""
                                                            var v115: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "hs_drain"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RHsSeams
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Impl
                                                                    var v39: String = ""
                                                                    v39 = "Session.Drain"
                                                                    var v40: String = ""
                                                                    v40 = "queue loop (Seeded: random pick) or engine.Drain"
                                                                    v111 = v36
                                                                    v112 = v37
                                                                    v113 = v38
                                                                    v114 = v39
                                                                    v115 = v40
                                                                }
                                                                else -> {
                                                                    var v106: String = ""
                                                                    var v107: US48 = US48_RWhole
                                                                    var v108: US49 = US49_Entry
                                                                    var v109: String = ""
                                                                    var v110: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v41: String = ""
                                                                            v41 = "hs_keyhit"
                                                                            var v42: US48 = US48_RWhole
                                                                            v42 = US48_RHsSeams
                                                                            var v43: US49 = US49_Entry
                                                                            v43 = US49_Impl
                                                                            var v44: String = ""
                                                                            v44 = "Session.KeyHit"
                                                                            var v45: String = ""
                                                                            v45 = "appends the key's marker to the caller's globals"
                                                                            v106 = v41
                                                                            v107 = v42
                                                                            v108 = v43
                                                                            v109 = v44
                                                                            v110 = v45
                                                                        }
                                                                        else -> {
                                                                            var v101: String = ""
                                                                            var v102: US48 = US48_RWhole
                                                                            var v103: US49 = US49_Entry
                                                                            var v104: String = ""
                                                                            var v105: String = ""
                                                                            when (v0.tag) {
                                                                                9 -> {
                                                                                    var v46: String = ""
                                                                                    v46 = "hs_keybody"
                                                                                    var v47: US48 = US48_RWhole
                                                                                    v47 = US48_RHsSeams
                                                                                    var v48: US49 = US49_Entry
                                                                                    v48 = US49_Impl
                                                                                    var v49: String = ""
                                                                                    v49 = "Session.KeyBody"
                                                                                    var v50: String = ""
                                                                                    v50 = "names marker 'key-<label>', new child list, env with that list as globals"
                                                                                    v101 = v46
                                                                                    v102 = v47
                                                                                    v103 = v48
                                                                                    v104 = v49
                                                                                    v105 = v50
                                                                                }
                                                                                else -> {
                                                                                    var v96: String = ""
                                                                                    var v97: US48 = US48_RWhole
                                                                                    var v98: US49 = US49_Entry
                                                                                    var v99: String = ""
                                                                                    var v100: String = ""
                                                                                    when (v0.tag) {
                                                                                        10 -> {
                                                                                            var v51: String = ""
                                                                                            v51 = "hs_print"
                                                                                            var v52: US48 = US48_RWhole
                                                                                            v52 = US48_RHsSeams
                                                                                            var v53: US49 = US49_Entry
                                                                                            v53 = US49_Impl
                                                                                            var v54: String = ""
                                                                                            v54 = "Session.Print"
                                                                                            var v55: String = ""
                                                                                            v55 = "'print' marker; text kept until Finish"
                                                                                            v96 = v51
                                                                                            v97 = v52
                                                                                            v98 = v53
                                                                                            v99 = v54
                                                                                            v100 = v55
                                                                                        }
                                                                                        else -> {
                                                                                            var v91: String = ""
                                                                                            var v92: US48 = US48_RWhole
                                                                                            var v93: US49 = US49_Entry
                                                                                            var v94: String = ""
                                                                                            var v95: String = ""
                                                                                            when (v0.tag) {
                                                                                                11 -> {
                                                                                                    var v56: String = ""
                                                                                                    v56 = "hs_finish"
                                                                                                    var v57: US48 = US48_RWhole
                                                                                                    v57 = US48_RHsSeams
                                                                                                    var v58: US49 = US49_Entry
                                                                                                    v58 = US49_Impl
                                                                                                    var v59: String = ""
                                                                                                    v59 = "Session.Finish"
                                                                                                    var v60: String = ""
                                                                                                    v60 = "merge Marks of every context; walk markers from root; replay prints; first failure wins; dedupe globals; rebuild_index"
                                                                                                    v91 = v56
                                                                                                    v92 = v57
                                                                                                    v93 = v58
                                                                                                    v94 = v59
                                                                                                    v95 = v60
                                                                                                }
                                                                                                else -> {
                                                                                                    var v86: String = ""
                                                                                                    var v87: US48 = US48_RWhole
                                                                                                    var v88: US49 = US49_Entry
                                                                                                    var v89: String = ""
                                                                                                    var v90: String = ""
                                                                                                    when (v0.tag) {
                                                                                                        12 -> {
                                                                                                            var v61: String = ""
                                                                                                            v61 = "hs_census"
                                                                                                            var v62: US48 = US48_RWhole
                                                                                                            v62 = US48_RHsSeams
                                                                                                            var v63: US49 = US49_Entry
                                                                                                            v63 = US49_Data
                                                                                                            var v64: String = ""
                                                                                                            v64 = "census"
                                                                                                            var v65: String = ""
                                                                                                            v65 = "SPIRAL_HOPACSOTA_CENSUS_OUT: body, parent, spawn_at_us, self_us"
                                                                                                            v86 = v61
                                                                                                            v87 = v62
                                                                                                            v88 = v63
                                                                                                            v89 = v64
                                                                                                            v90 = v65
                                                                                                        }
                                                                                                        else -> {
                                                                                                            var v81: String = ""
                                                                                                            var v82: US48 = US48_RWhole
                                                                                                            var v83: US49 = US49_Entry
                                                                                                            var v84: String = ""
                                                                                                            var v85: String = ""
                                                                                                            when (v0.tag) {
                                                                                                                13 -> {
                                                                                                                    var v66: String = ""
                                                                                                                    v66 = "hs_fallback"
                                                                                                                    var v67: US48 = US48_RWhole
                                                                                                                    v67 = US48_RHsSeams
                                                                                                                    var v68: US49 = US49_Entry
                                                                                                                    v68 = US49_Impl
                                                                                                                    var v69: String = ""
                                                                                                                    v69 = "seam ops with engine = null"
                                                                                                                    var v70: String = ""
                                                                                                                    v70 = "claim/fill/announce/family/nesting/validated/read fall back to the single-flight code"
                                                                                                                    v81 = v66
                                                                                                                    v82 = v67
                                                                                                                    v83 = v68
                                                                                                                    v84 = v69
                                                                                                                    v85 = v70
                                                                                                                }
                                                                                                                else -> {
                                                                                                                    var v76: String = ""
                                                                                                                    var v77: US48 = US48_RWhole
                                                                                                                    var v78: US49 = US49_Entry
                                                                                                                    var v79: String = ""
                                                                                                                    var v80: String = ""
                                                                                                                    when (v0.tag) {
                                                                                                                        14 -> {
                                                                                                                            var v71: String = ""
                                                                                                                            v71 = "hs_marks"
                                                                                                                            var v72: US48 = US48_RWhole
                                                                                                                            v72 = US48_RHsSeams
                                                                                                                            var v73: US49 = US49_Entry
                                                                                                                            v73 = US49_Data
                                                                                                                            var v74: String = ""
                                                                                                                            v74 = "Marks(prefix)"
                                                                                                                            var v75: String = ""
                                                                                                                            v75 = "Children, Prints, Failures, Keys; names '\\u0001hopacsota-<kind>-<prefix><n>'"
                                                                                                                            v76 = v71
                                                                                                                            v77 = v72
                                                                                                                            v78 = v73
                                                                                                                            v79 = v74
                                                                                                                            v80 = v75
                                                                                                                        }
                                                                                                                        else -> throw RuntimeException()
                                                                                                                    }
                                                                                                                    v81 = v76
                                                                                                                    v82 = v77
                                                                                                                    v83 = v78
                                                                                                                    v84 = v79
                                                                                                                    v85 = v80
                                                                                                                }
                                                                                                            }
                                                                                                            v86 = v81
                                                                                                            v87 = v82
                                                                                                            v88 = v83
                                                                                                            v89 = v84
                                                                                                            v90 = v85
                                                                                                        }
                                                                                                    }
                                                                                                    v91 = v86
                                                                                                    v92 = v87
                                                                                                    v93 = v88
                                                                                                    v94 = v89
                                                                                                    v95 = v90
                                                                                                }
                                                                                            }
                                                                                            v96 = v91
                                                                                            v97 = v92
                                                                                            v98 = v93
                                                                                            v99 = v94
                                                                                            v100 = v95
                                                                                        }
                                                                                    }
                                                                                    v101 = v96
                                                                                    v102 = v97
                                                                                    v103 = v98
                                                                                    v104 = v99
                                                                                    v105 = v100
                                                                                }
                                                                            }
                                                                            v106 = v101
                                                                            v107 = v102
                                                                            v108 = v103
                                                                            v109 = v104
                                                                            v110 = v105
                                                                        }
                                                                    }
                                                                    v111 = v106
                                                                    v112 = v107
                                                                    v113 = v108
                                                                    v114 = v109
                                                                    v115 = v110
                                                                }
                                                            }
                                                            v116 = v111
                                                            v117 = v112
                                                            v118 = v113
                                                            v119 = v114
                                                            v120 = v115
                                                        }
                                                    }
                                                    v121 = v116
                                                    v122 = v117
                                                    v123 = v118
                                                    v124 = v119
                                                    v125 = v120
                                                }
                                            }
                                            v126 = v121
                                            v127 = v122
                                            v128 = v123
                                            v129 = v124
                                            v130 = v125
                                        }
                                    }
                                    v131 = v126
                                    v132 = v127
                                    v133 = v128
                                    v134 = v129
                                    v135 = v130
                                }
                            }
                            v136 = v131
                            v137 = v132
                            v138 = v133
                            v139 = v134
                            v140 = v135
                        }
                    }
                    v141 = v136
                    v142 = v137
                    v143 = v138
                    v144 = v139
                    v145 = v140
                }
            }
            v146 = v141
            v147 = v142
            v148 = v143
            v149 = v144
            v150 = v145
        }
    }
    return SpiralTuple6(v146, v147, v148, v149, v150)
}
fun sp_info_of_hs_engine_90(p0: US37): SpiralTuple6 {
    var v0: US37 = p0
    var v206: String = ""
    var v207: US48 = US48_RWhole
    var v208: US49 = US49_Entry
    var v209: String = ""
    var v210: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "e_engine"
            var v2: US48 = US48_RWhole
            v2 = US48_RHsEngine
            var v3: US49 = US49_Entry
            v3 = US49_Class
            var v4: String = ""
            v4 = "Engine(workers)"
            var v5: String = ""
            v5 = "owns control scheduler, contexts, table, guard, graph, worklist, threads"
            v206 = v1
            v207 = v2
            v208 = v3
            v209 = v4
            v210 = v5
        }
        else -> {
            var v201: String = ""
            var v202: US48 = US48_RWhole
            var v203: US49 = US49_Entry
            var v204: String = ""
            var v205: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "e_control"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RHsEngine
                    var v8: US49 = US49_Entry
                    v8 = US49_Impl
                    var v9: String = ""
                    v9 = "control Scheduler"
                    var v10: String = ""
                    v10 = "Hopac scheduler with NumWorkers = 1; all coordination runs here"
                    v201 = v6
                    v202 = v7
                    v203 = v8
                    v204 = v9
                    v205 = v10
                }
                else -> {
                    var v196: String = ""
                    var v197: US48 = US48_RWhole
                    var v198: US49 = US49_Entry
                    var v199: String = ""
                    var v200: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "e_bridge"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RHsEngine
                            var v13: US49 = US49_Entry
                            v13 = US49_Impl
                            var v14: String = ""
                            v14 = "bridge op"
                            var v15: String = ""
                            v15 = "Scheduler.run control op from a body thread; counts Bridges"
                            v196 = v11
                            v197 = v12
                            v198 = v13
                            v199 = v14
                            v200 = v15
                        }
                        else -> {
                            var v191: String = ""
                            var v192: US48 = US48_RWhole
                            var v193: US49 = US49_Entry
                            var v194: String = ""
                            var v195: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "e_ask"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RHsEngine
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Impl
                                    var v19: String = ""
                                    v19 = "ask id op"
                                    var v20: String = ""
                                    v20 = "MVar.modifyFun guard (table.Decide), then wake the returned waiters"
                                    v191 = v16
                                    v192 = v17
                                    v193 = v18
                                    v194 = v19
                                    v195 = v20
                                }
                                else -> {
                                    var v186: String = ""
                                    var v187: US48 = US48_RWhole
                                    var v188: US49 = US49_Entry
                                    var v189: String = ""
                                    var v190: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "e_settle"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RHsEngine
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Impl
                                            var v24: String = ""
                                            v24 = "settle"
                                            var v25: String = ""
                                            v25 = "ClaimOp; on Wait -> await -> retry"
                                            v186 = v21
                                            v187 = v22
                                            v188 = v23
                                            v189 = v24
                                            v190 = v25
                                        }
                                        else -> {
                                            var v181: String = ""
                                            var v182: US48 = US48_RWhole
                                            var v183: US49 = US49_Entry
                                            var v184: String = ""
                                            var v185: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "e_await"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RHsEngine
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Impl
                                                    var v29: String = ""
                                                    v29 = "await"
                                                    var v30: String = ""
                                                    v30 = "AddEdge to Graph; if accepted, read the IVar and RemoveEdge; else HopacsotaConflict"
                                                    v181 = v26
                                                    v182 = v27
                                                    v183 = v28
                                                    v184 = v29
                                                    v185 = v30
                                                }
                                                else -> {
                                                    var v176: String = ""
                                                    var v177: US48 = US48_RWhole
                                                    var v178: US49 = US49_Entry
                                                    var v179: String = ""
                                                    var v180: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "e_abandon"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RHsEngine
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Impl
                                                            var v34: String = ""
                                                            v34 = "abandon_chain"
                                                            var v35: String = ""
                                                            v35 = "AbandonOp for every key still in the context chain"
                                                            v176 = v31
                                                            v177 = v32
                                                            v178 = v33
                                                            v179 = v34
                                                            v180 = v35
                                                        }
                                                        else -> {
                                                            var v171: String = ""
                                                            var v172: US48 = US48_RWhole
                                                            var v173: US49 = US49_Entry
                                                            var v174: String = ""
                                                            var v175: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "e_stack"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RHsEngine
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Impl
                                                                    var v39: String = ""
                                                                    v39 = "lower_stack_limit"
                                                                    var v40: String = ""
                                                                    v40 = "reflection: Hopac.Core.Worker.ThreadStackLimit lowered for 1.5 GB stacks"
                                                                    v171 = v36
                                                                    v172 = v37
                                                                    v173 = v38
                                                                    v174 = v39
                                                                    v175 = v40
                                                                }
                                                                else -> {
                                                                    var v166: String = ""
                                                                    var v167: US48 = US48_RWhole
                                                                    var v168: US49 = US49_Entry
                                                                    var v169: String = ""
                                                                    var v170: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v41: String = ""
                                                                            v41 = "e_threads"
                                                                            var v42: US48 = US48_RWhole
                                                                            v42 = US48_RHsEngine
                                                                            var v43: US49 = US49_Entry
                                                                            v43 = US49_Impl
                                                                            var v44: String = ""
                                                                            v44 = "body threads"
                                                                            var v45: String = ""
                                                                            v45 = "workers x Thread('hopacsota-body-N', 1.5 GB stack), background"
                                                                            v166 = v41
                                                                            v167 = v42
                                                                            v168 = v43
                                                                            v169 = v44
                                                                            v170 = v45
                                                                        }
                                                                        else -> {
                                                                            var v161: String = ""
                                                                            var v162: US48 = US48_RWhole
                                                                            var v163: US49 = US49_Entry
                                                                            var v164: String = ""
                                                                            var v165: String = ""
                                                                            when (v0.tag) {
                                                                                9 -> {
                                                                                    var v46: String = ""
                                                                                    v46 = "e_loop"
                                                                                    var v47: US48 = US48_RWhole
                                                                                    v47 = US48_RHsEngine
                                                                                    var v48: US49 = US49_Entry
                                                                                    v48 = US49_Impl
                                                                                    var v49: String = ""
                                                                                    v49 = "body_loop"
                                                                                    var v50: String = ""
                                                                                    v50 = "Take work -> run body -> abandon_chain -> report finished"
                                                                                    v161 = v46
                                                                                    v162 = v47
                                                                                    v163 = v48
                                                                                    v164 = v49
                                                                                    v165 = v50
                                                                                }
                                                                                else -> {
                                                                                    var v156: String = ""
                                                                                    var v157: US48 = US48_RWhole
                                                                                    var v158: US49 = US49_Entry
                                                                                    var v159: String = ""
                                                                                    var v160: String = ""
                                                                                    when (v0.tag) {
                                                                                        10 -> {
                                                                                            var v51: String = ""
                                                                                            v51 = "e_claim"
                                                                                            var v52: US48 = US48_RWhole
                                                                                            v52 = US48_RHsEngine
                                                                                            var v53: US49 = US49_Entry
                                                                                            v53 = US49_Impl
                                                                                            var v54: String = ""
                                                                                            v54 = "Engine.Claim"
                                                                                            var v55: String = ""
                                                                                            v55 = "ctx.Ready -> published snapshot -> settle; Taken -> push to Chain"
                                                                                            v156 = v51
                                                                                            v157 = v52
                                                                                            v158 = v53
                                                                                            v159 = v54
                                                                                            v160 = v55
                                                                                        }
                                                                                        else -> {
                                                                                            var v151: String = ""
                                                                                            var v152: US48 = US48_RWhole
                                                                                            var v153: US49 = US49_Entry
                                                                                            var v154: String = ""
                                                                                            var v155: String = ""
                                                                                            when (v0.tag) {
                                                                                                11 -> {
                                                                                                    var v56: String = ""
                                                                                                    v56 = "e_validated"
                                                                                                    var v57: US48 = US48_RWhole
                                                                                                    v57 = US48_RHsEngine
                                                                                                    var v58: US49 = US49_Entry
                                                                                                    v58 = US49_Impl
                                                                                                    var v59: String = ""
                                                                                                    v59 = "Engine.Validated"
                                                                                                    var v60: String = ""
                                                                                                    v60 = "like Claim without writing pending"
                                                                                                    v151 = v56
                                                                                                    v152 = v57
                                                                                                    v153 = v58
                                                                                                    v154 = v59
                                                                                                    v155 = v60
                                                                                                }
                                                                                                else -> {
                                                                                                    var v146: String = ""
                                                                                                    var v147: US48 = US48_RWhole
                                                                                                    var v148: US49 = US49_Entry
                                                                                                    var v149: String = ""
                                                                                                    var v150: String = ""
                                                                                                    when (v0.tag) {
                                                                                                        12 -> {
                                                                                                            var v61: String = ""
                                                                                                            v61 = "e_read"
                                                                                                            var v62: US48 = US48_RWhole
                                                                                                            v62 = US48_RHsEngine
                                                                                                            var v63: US49 = US49_Entry
                                                                                                            v63 = US49_Impl
                                                                                                            var v64: String = ""
                                                                                                            v64 = "Engine.Read"
                                                                                                            var v65: String = ""
                                                                                                            v65 = "ctx.Ready only; else compiler error"
                                                                                                            v146 = v61
                                                                                                            v147 = v62
                                                                                                            v148 = v63
                                                                                                            v149 = v64
                                                                                                            v150 = v65
                                                                                                        }
                                                                                                        else -> {
                                                                                                            var v141: String = ""
                                                                                                            var v142: US48 = US48_RWhole
                                                                                                            var v143: US49 = US49_Entry
                                                                                                            var v144: String = ""
                                                                                                            var v145: String = ""
                                                                                                            when (v0.tag) {
                                                                                                                13 -> {
                                                                                                                    var v66: String = ""
                                                                                                                    v66 = "e_fill"
                                                                                                                    var v67: US48 = US48_RWhole
                                                                                                                    v67 = US48_RHsEngine
                                                                                                                    var v68: US49 = US49_Entry
                                                                                                                    v68 = US49_Impl
                                                                                                                    var v69: String = ""
                                                                                                                    v69 = "Engine.Fill"
                                                                                                                    var v70: String = ""
                                                                                                                    v70 = "FillOp; cache in Ready; leave Chain"
                                                                                                                    v141 = v66
                                                                                                                    v142 = v67
                                                                                                                    v143 = v68
                                                                                                                    v144 = v69
                                                                                                                    v145 = v70
                                                                                                                }
                                                                                                                else -> {
                                                                                                                    var v136: String = ""
                                                                                                                    var v137: US48 = US48_RWhole
                                                                                                                    var v138: US49 = US49_Entry
                                                                                                                    var v139: String = ""
                                                                                                                    var v140: String = ""
                                                                                                                    when (v0.tag) {
                                                                                                                        14 -> {
                                                                                                                            var v71: String = ""
                                                                                                                            v71 = "e_announce"
                                                                                                                            var v72: US48 = US48_RWhole
                                                                                                                            v72 = US48_RHsEngine
                                                                                                                            var v73: US49 = US49_Entry
                                                                                                                            v73 = US49_Impl
                                                                                                                            var v74: String = ""
                                                                                                                            v74 = "Engine.Announce"
                                                                                                                            var v75: String = ""
                                                                                                                            v75 = "AnnounceOp(usable); leave Chain if usable"
                                                                                                                            v136 = v71
                                                                                                                            v137 = v72
                                                                                                                            v138 = v73
                                                                                                                            v139 = v74
                                                                                                                            v140 = v75
                                                                                                                        }
                                                                                                                        else -> {
                                                                                                                            var v131: String = ""
                                                                                                                            var v132: US48 = US48_RWhole
                                                                                                                            var v133: US49 = US49_Entry
                                                                                                                            var v134: String = ""
                                                                                                                            var v135: String = ""
                                                                                                                            when (v0.tag) {
                                                                                                                                15 -> {
                                                                                                                                    var v76: String = ""
                                                                                                                                    v76 = "e_family"
                                                                                                                                    var v77: US48 = US48_RWhole
                                                                                                                                    v77 = US48_RHsEngine
                                                                                                                                    var v78: US49 = US49_Entry
                                                                                                                                    v78 = US49_Impl
                                                                                                                                    var v79: String = ""
                                                                                                                                    v79 = "Engine.Family"
                                                                                                                                    var v80: String = ""
                                                                                                                                    v80 = "per-context local table; FamilyOp create under the guard"
                                                                                                                                    v131 = v76
                                                                                                                                    v132 = v77
                                                                                                                                    v133 = v78
                                                                                                                                    v134 = v79
                                                                                                                                    v135 = v80
                                                                                                                                }
                                                                                                                                else -> {
                                                                                                                                    var v126: String = ""
                                                                                                                                    var v127: US48 = US48_RWhole
                                                                                                                                    var v128: US49 = US49_Entry
                                                                                                                                    var v129: String = ""
                                                                                                                                    var v130: String = ""
                                                                                                                                    when (v0.tag) {
                                                                                                                                        16 -> {
                                                                                                                                            var v81: String = ""
                                                                                                                                            v81 = "e_spawn"
                                                                                                                                            var v82: US48 = US48_RWhole
                                                                                                                                            v82 = US48_RHsEngine
                                                                                                                                            var v83: US49 = US49_Entry
                                                                                                                                            v83 = US49_Impl
                                                                                                                                            var v84: String = ""
                                                                                                                                            v84 = "Engine.Spawn"
                                                                                                                                            var v85: String = ""
                                                                                                                                            v85 = "Ch.give work (Spawn(body, Chain @ Inherited))"
                                                                                                                                            v126 = v81
                                                                                                                                            v127 = v82
                                                                                                                                            v128 = v83
                                                                                                                                            v129 = v84
                                                                                                                                            v130 = v85
                                                                                                                                        }
                                                                                                                                        else -> {
                                                                                                                                            var v121: String = ""
                                                                                                                                            var v122: US48 = US48_RWhole
                                                                                                                                            var v123: US49 = US49_Entry
                                                                                                                                            var v124: String = ""
                                                                                                                                            var v125: String = ""
                                                                                                                                            when (v0.tag) {
                                                                                                                                                17 -> {
                                                                                                                                                    var v86: String = ""
                                                                                                                                                    v86 = "e_drain"
                                                                                                                                                    var v87: US48 = US48_RWhole
                                                                                                                                                    v87 = US48_RHsEngine
                                                                                                                                                    var v88: US49 = US49_Entry
                                                                                                                                                    v88 = US49_Impl
                                                                                                                                                    var v89: String = ""
                                                                                                                                                    v89 = "Engine.Drain"
                                                                                                                                                    var v90: String = ""
                                                                                                                                                    v90 = "Drain request; rethrow the first body crash"
                                                                                                                                                    v121 = v86
                                                                                                                                                    v122 = v87
                                                                                                                                                    v123 = v88
                                                                                                                                                    v124 = v89
                                                                                                                                                    v125 = v90
                                                                                                                                                }
                                                                                                                                                else -> {
                                                                                                                                                    var v116: String = ""
                                                                                                                                                    var v117: US48 = US48_RWhole
                                                                                                                                                    var v118: US49 = US49_Entry
                                                                                                                                                    var v119: String = ""
                                                                                                                                                    var v120: String = ""
                                                                                                                                                    when (v0.tag) {
                                                                                                                                                        18 -> {
                                                                                                                                                            var v91: String = ""
                                                                                                                                                            v91 = "e_check"
                                                                                                                                                            var v92: US48 = US48_RWhole
                                                                                                                                                            v92 = US48_RHsEngine
                                                                                                                                                            var v93: US49 = US49_Entry
                                                                                                                                                            v93 = US49_Impl
                                                                                                                                                            var v94: String = ""
                                                                                                                                                            v94 = "Engine.Check"
                                                                                                                                                            var v95: String = ""
                                                                                                                                                            v95 = "conflicts + cycles > 0 or FORCE_CONFLICT -> raise HopacsotaConflict"
                                                                                                                                                            v116 = v91
                                                                                                                                                            v117 = v92
                                                                                                                                                            v118 = v93
                                                                                                                                                            v119 = v94
                                                                                                                                                            v120 = v95
                                                                                                                                                        }
                                                                                                                                                        else -> {
                                                                                                                                                            var v111: String = ""
                                                                                                                                                            var v112: US48 = US48_RWhole
                                                                                                                                                            var v113: US49 = US49_Entry
                                                                                                                                                            var v114: String = ""
                                                                                                                                                            var v115: String = ""
                                                                                                                                                            when (v0.tag) {
                                                                                                                                                                19 -> {
                                                                                                                                                                    var v96: String = ""
                                                                                                                                                                    v96 = "e_close"
                                                                                                                                                                    var v97: US48 = US48_RWhole
                                                                                                                                                                    v97 = US48_RHsEngine
                                                                                                                                                                    var v98: US49 = US49_Entry
                                                                                                                                                                    v98 = US49_Impl
                                                                                                                                                                    var v99: String = ""
                                                                                                                                                                    v99 = "Engine.Close"
                                                                                                                                                                    var v100: String = ""
                                                                                                                                                                    v100 = "Stop, join threads, stats line, kill scheduler"
                                                                                                                                                                    v111 = v96
                                                                                                                                                                    v112 = v97
                                                                                                                                                                    v113 = v98
                                                                                                                                                                    v114 = v99
                                                                                                                                                                    v115 = v100
                                                                                                                                                                }
                                                                                                                                                                else -> {
                                                                                                                                                                    var v106: String = ""
                                                                                                                                                                    var v107: US48 = US48_RWhole
                                                                                                                                                                    var v108: US49 = US49_Entry
                                                                                                                                                                    var v109: String = ""
                                                                                                                                                                    var v110: String = ""
                                                                                                                                                                    when (v0.tag) {
                                                                                                                                                                        20 -> {
                                                                                                                                                                            var v101: String = ""
                                                                                                                                                                            v101 = "e_conflict"
                                                                                                                                                                            var v102: US48 = US48_RWhole
                                                                                                                                                                            v102 = US48_RHsEngine
                                                                                                                                                                            var v103: US49 = US49_Entry
                                                                                                                                                                            v103 = US49_Err
                                                                                                                                                                            var v104: String = ""
                                                                                                                                                                            v104 = "exception HopacsotaConflict"
                                                                                                                                                                            var v105: String = ""
                                                                                                                                                                            v105 = "raised by await (cycle) or Check"
                                                                                                                                                                            v106 = v101
                                                                                                                                                                            v107 = v102
                                                                                                                                                                            v108 = v103
                                                                                                                                                                            v109 = v104
                                                                                                                                                                            v110 = v105
                                                                                                                                                                        }
                                                                                                                                                                        else -> throw RuntimeException()
                                                                                                                                                                    }
                                                                                                                                                                    v111 = v106
                                                                                                                                                                    v112 = v107
                                                                                                                                                                    v113 = v108
                                                                                                                                                                    v114 = v109
                                                                                                                                                                    v115 = v110
                                                                                                                                                                }
                                                                                                                                                            }
                                                                                                                                                            v116 = v111
                                                                                                                                                            v117 = v112
                                                                                                                                                            v118 = v113
                                                                                                                                                            v119 = v114
                                                                                                                                                            v120 = v115
                                                                                                                                                        }
                                                                                                                                                    }
                                                                                                                                                    v121 = v116
                                                                                                                                                    v122 = v117
                                                                                                                                                    v123 = v118
                                                                                                                                                    v124 = v119
                                                                                                                                                    v125 = v120
                                                                                                                                                }
                                                                                                                                            }
                                                                                                                                            v126 = v121
                                                                                                                                            v127 = v122
                                                                                                                                            v128 = v123
                                                                                                                                            v129 = v124
                                                                                                                                            v130 = v125
                                                                                                                                        }
                                                                                                                                    }
                                                                                                                                    v131 = v126
                                                                                                                                    v132 = v127
                                                                                                                                    v133 = v128
                                                                                                                                    v134 = v129
                                                                                                                                    v135 = v130
                                                                                                                                }
                                                                                                                            }
                                                                                                                            v136 = v131
                                                                                                                            v137 = v132
                                                                                                                            v138 = v133
                                                                                                                            v139 = v134
                                                                                                                            v140 = v135
                                                                                                                        }
                                                                                                                    }
                                                                                                                    v141 = v136
                                                                                                                    v142 = v137
                                                                                                                    v143 = v138
                                                                                                                    v144 = v139
                                                                                                                    v145 = v140
                                                                                                                }
                                                                                                            }
                                                                                                            v146 = v141
                                                                                                            v147 = v142
                                                                                                            v148 = v143
                                                                                                            v149 = v144
                                                                                                            v150 = v145
                                                                                                        }
                                                                                                    }
                                                                                                    v151 = v146
                                                                                                    v152 = v147
                                                                                                    v153 = v148
                                                                                                    v154 = v149
                                                                                                    v155 = v150
                                                                                                }
                                                                                            }
                                                                                            v156 = v151
                                                                                            v157 = v152
                                                                                            v158 = v153
                                                                                            v159 = v154
                                                                                            v160 = v155
                                                                                        }
                                                                                    }
                                                                                    v161 = v156
                                                                                    v162 = v157
                                                                                    v163 = v158
                                                                                    v164 = v159
                                                                                    v165 = v160
                                                                                }
                                                                            }
                                                                            v166 = v161
                                                                            v167 = v162
                                                                            v168 = v163
                                                                            v169 = v164
                                                                            v170 = v165
                                                                        }
                                                                    }
                                                                    v171 = v166
                                                                    v172 = v167
                                                                    v173 = v168
                                                                    v174 = v169
                                                                    v175 = v170
                                                                }
                                                            }
                                                            v176 = v171
                                                            v177 = v172
                                                            v178 = v173
                                                            v179 = v174
                                                            v180 = v175
                                                        }
                                                    }
                                                    v181 = v176
                                                    v182 = v177
                                                    v183 = v178
                                                    v184 = v179
                                                    v185 = v180
                                                }
                                            }
                                            v186 = v181
                                            v187 = v182
                                            v188 = v183
                                            v189 = v184
                                            v190 = v185
                                        }
                                    }
                                    v191 = v186
                                    v192 = v187
                                    v193 = v188
                                    v194 = v189
                                    v195 = v190
                                }
                            }
                            v196 = v191
                            v197 = v192
                            v198 = v193
                            v199 = v194
                            v200 = v195
                        }
                    }
                    v201 = v196
                    v202 = v197
                    v203 = v198
                    v204 = v199
                    v205 = v200
                }
            }
            v206 = v201
            v207 = v202
            v208 = v203
            v209 = v204
            v210 = v205
        }
    }
    return SpiralTuple6(v206, v207, v208, v209, v210)
}
fun sp_info_of_hs_table_91(p0: US38): SpiralTuple6 {
    var v0: US38 = p0
    var v76: String = ""
    var v77: US48 = US48_RWhole
    var v78: US49 = US49_Entry
    var v79: String = ""
    var v80: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "t_table"
            var v2: US48 = US48_RWhole
            v2 = US48_RHsTable
            var v3: US49 = US49_Entry
            v3 = US49_Class
            var v4: String = ""
            v4 = "Table()"
            var v5: String = ""
            v5 = "entries: Dictionary<obj, Entry>; counters named/conflicts/abandoned"
            v76 = v1
            v77 = v2
            v78 = v3
            v79 = v4
            v80 = v5
        }
        else -> {
            var v71: String = ""
            var v72: US48 = US48_RWhole
            var v73: US49 = US49_Entry
            var v74: String = ""
            var v75: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "t_decide"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RHsTable
                    var v8: US49 = US49_Entry
                    v8 = US49_Impl
                    var v9: String = ""
                    v9 = "Table.Decide id op"
                    var v10: String = ""
                    v10 = "the only place keys change state"
                    v71 = v6
                    v72 = v7
                    v73 = v8
                    v74 = v9
                    v75 = v10
                }
                else -> {
                    var v66: String = ""
                    var v67: US48 = US48_RWhole
                    var v68: US49 = US49_Entry
                    var v69: String = ""
                    var v70: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "t_entry"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RHsTable
                            var v13: US49 = US49_Entry
                            v13 = US49_Data
                            var v14: String = ""
                            v14 = "Entry(owner, marker)"
                            var v15: String = ""
                            v15 = "State + Waiters: IVar<unit> list"
                            v66 = v11
                            v67 = v12
                            v68 = v13
                            v69 = v14
                            v70 = v15
                        }
                        else -> {
                            var v61: String = ""
                            var v62: US48 = US48_RWhole
                            var v63: US49 = US49_Entry
                            var v64: String = ""
                            var v65: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "t_state"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RHsTable
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Data
                                    var v19: String = ""
                                    v19 = "EntryState"
                                    var v20: String = ""
                                    v20 = "Evaluating -> Announced | Filled | Abandoned"
                                    v61 = v16
                                    v62 = v17
                                    v63 = v18
                                    v64 = v19
                                    v65 = v20
                                }
                                else -> {
                                    var v56: String = ""
                                    var v57: US48 = US48_RWhole
                                    var v58: US49 = US49_Entry
                                    var v59: String = ""
                                    var v60: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "t_ops"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RHsTable
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Data
                                            var v24: String = ""
                                            v24 = "TableOp"
                                            var v25: String = ""
                                            v25 = "ClaimOp | FillOp | AnnounceOp | AbandonOp | FamilyOp"
                                            v56 = v21
                                            v57 = v22
                                            v58 = v23
                                            v59 = v24
                                            v60 = v25
                                        }
                                        else -> {
                                            var v51: String = ""
                                            var v52: US48 = US48_RWhole
                                            var v53: US49 = US49_Entry
                                            var v54: String = ""
                                            var v55: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "t_replies"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RHsTable
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Data
                                                    var v29: String = ""
                                                    v29 = "TableReply"
                                                    var v30: String = ""
                                                    v30 = "Taken | Present | Again | Wait | Written | Family | Failed"
                                                    v51 = v26
                                                    v52 = v27
                                                    v53 = v28
                                                    v54 = v29
                                                    v55 = v30
                                                }
                                                else -> {
                                                    var v46: String = ""
                                                    var v47: US48 = US48_RWhole
                                                    var v48: US49 = US49_Entry
                                                    var v49: String = ""
                                                    var v50: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "t_published"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RHsTable
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Data
                                                            var v34: String = ""
                                                            v34 = "published"
                                                            var v35: String = ""
                                                            v35 = "ImmutableDictionary via Volatile.Write; lock-free reads by every thread"
                                                            v46 = v31
                                                            v47 = v32
                                                            v48 = v33
                                                            v49 = v34
                                                            v50 = v35
                                                        }
                                                        else -> {
                                                            var v41: String = ""
                                                            var v42: US48 = US48_RWhole
                                                            var v43: US49 = US49_Entry
                                                            var v44: String = ""
                                                            var v45: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "t_guard"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RHsTable
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Data
                                                                    var v39: String = ""
                                                                    v39 = "MVar<Table> guard"
                                                                    var v40: String = ""
                                                                    v40 = "serializes Decide calls"
                                                                    v41 = v36
                                                                    v42 = v37
                                                                    v43 = v38
                                                                    v44 = v39
                                                                    v45 = v40
                                                                }
                                                                else -> throw RuntimeException()
                                                            }
                                                            v46 = v41
                                                            v47 = v42
                                                            v48 = v43
                                                            v49 = v44
                                                            v50 = v45
                                                        }
                                                    }
                                                    v51 = v46
                                                    v52 = v47
                                                    v53 = v48
                                                    v54 = v49
                                                    v55 = v50
                                                }
                                            }
                                            v56 = v51
                                            v57 = v52
                                            v58 = v53
                                            v59 = v54
                                            v60 = v55
                                        }
                                    }
                                    v61 = v56
                                    v62 = v57
                                    v63 = v58
                                    v64 = v59
                                    v65 = v60
                                }
                            }
                            v66 = v61
                            v67 = v62
                            v68 = v63
                            v69 = v64
                            v70 = v65
                        }
                    }
                    v71 = v66
                    v72 = v67
                    v73 = v68
                    v74 = v69
                    v75 = v70
                }
            }
            v76 = v71
            v77 = v72
            v78 = v73
            v79 = v74
            v80 = v75
        }
    }
    return SpiralTuple6(v76, v77, v78, v79, v80)
}
fun sp_info_of_hs_graph_92(p0: US39): SpiralTuple6 {
    var v0: US39 = p0
    var v26: String = ""
    var v27: US48 = US48_RWhole
    var v28: US49 = US49_Entry
    var v29: String = ""
    var v30: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "g_graph"
            var v2: US48 = US48_RWhole
            v2 = US48_RHsGraph
            var v3: US49 = US49_Entry
            v3 = US49_Class
            var v4: String = ""
            v4 = "Graph()"
            var v5: String = ""
            v5 = "waits_on: Dictionary<int, int>; cycles counter"
            v26 = v1
            v27 = v2
            v28 = v3
            v29 = v4
            v30 = v5
        }
        else -> {
            var v21: String = ""
            var v22: US48 = US48_RWhole
            var v23: US49 = US49_Entry
            var v24: String = ""
            var v25: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "g_req"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RHsGraph
                    var v8: US49 = US49_Entry
                    v8 = US49_Data
                    var v9: String = ""
                    v9 = "GraphRequest"
                    var v10: String = ""
                    v10 = "AddEdge(waiter, owner, accepted) | RemoveEdge | CountCycles"
                    v21 = v6
                    v22 = v7
                    v23 = v8
                    v24 = v9
                    v25 = v10
                }
                else -> {
                    var v16: String = ""
                    var v17: US48 = US48_RWhole
                    var v18: US49 = US49_Entry
                    var v19: String = ""
                    var v20: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "g_ch"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RHsGraph
                            var v13: US49 = US49_Entry
                            v13 = US49_Data
                            var v14: String = ""
                            v14 = "Ch<GraphRequest> edges"
                            var v15: String = ""
                            v15 = "foreverServer"
                            v16 = v11
                            v17 = v12
                            v18 = v13
                            v19 = v14
                            v20 = v15
                        }
                        else -> throw RuntimeException()
                    }
                    v21 = v16
                    v22 = v17
                    v23 = v18
                    v24 = v19
                    v25 = v20
                }
            }
            v26 = v21
            v27 = v22
            v28 = v23
            v29 = v24
            v30 = v25
        }
    }
    return SpiralTuple6(v26, v27, v28, v29, v30)
}
fun sp_info_of_hs_work_93(p0: US40): SpiralTuple6 {
    var v0: US40 = p0
    var v36: String = ""
    var v37: US48 = US48_RWhole
    var v38: US49 = US49_Entry
    var v39: String = ""
    var v40: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "w_list"
            var v2: US48 = US48_RWhole
            v2 = US48_RHsWork
            var v3: US49 = US49_Entry
            v3 = US49_Class
            var v4: String = ""
            v4 = "Worklist(tokens)"
            var v5: String = ""
            v5 = "queue, idle IVars, outstanding, drained"
            v36 = v1
            v37 = v2
            v38 = v3
            v39 = v4
            v40 = v5
        }
        else -> {
            var v31: String = ""
            var v32: US48 = US48_RWhole
            var v33: US49 = US49_Entry
            var v34: String = ""
            var v35: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "w_req"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RHsWork
                    var v8: US49 = US49_Entry
                    v8 = US49_Data
                    var v9: String = ""
                    v9 = "WorkRequest"
                    var v10: String = ""
                    v10 = "Spawn | Take | Drain | Stop"
                    v31 = v6
                    v32 = v7
                    v33 = v8
                    v34 = v9
                    v35 = v10
                }
                else -> {
                    var v26: String = ""
                    var v27: US48 = US48_RWhole
                    var v28: US49 = US49_Entry
                    var v29: String = ""
                    var v30: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "w_settle"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RHsWork
                            var v13: US49 = US49_Entry
                            v13 = US49_Impl
                            var v14: String = ""
                            v14 = "Worklist.Settle"
                            var v15: String = ""
                            v15 = "hand queued bodies to idle threads while tokens > 0"
                            v26 = v11
                            v27 = v12
                            v28 = v13
                            v29 = v14
                            v30 = v15
                        }
                        else -> {
                            var v21: String = ""
                            var v22: US48 = US48_RWhole
                            var v23: US49 = US49_Entry
                            var v24: String = ""
                            var v25: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "w_ch"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RHsWork
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Data
                                    var v19: String = ""
                                    v19 = "Ch<WorkRequest> work"
                                    var v20: String = ""
                                    v20 = "foreverServer"
                                    v21 = v16
                                    v22 = v17
                                    v23 = v18
                                    v24 = v19
                                    v25 = v20
                                }
                                else -> throw RuntimeException()
                            }
                            v26 = v21
                            v27 = v22
                            v28 = v23
                            v29 = v24
                            v30 = v25
                        }
                    }
                    v31 = v26
                    v32 = v27
                    v33 = v28
                    v34 = v29
                    v35 = v30
                }
            }
            v36 = v31
            v37 = v32
            v38 = v33
            v39 = v34
            v40 = v35
        }
    }
    return SpiralTuple6(v36, v37, v38, v39, v40)
}
fun sp_info_of_hs_ctx_94(p0: US41): SpiralTuple6 {
    var v0: US41 = p0
    var v16: String = ""
    var v17: US48 = US48_RWhole
    var v18: US49 = US49_Entry
    var v19: String = ""
    var v20: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "c_ctx"
            var v2: US48 = US48_RWhole
            v2 = US48_RHsCtx
            var v3: US49 = US49_Entry
            v3 = US49_Data
            var v4: String = ""
            v4 = "BodyContext(id)"
            var v5: String = ""
            v5 = "Marks, Ready, Families, Nesting, Chain, Inherited, counters, Crashes"
            v16 = v1
            v17 = v2
            v18 = v3
            v19 = v4
            v20 = v5
        }
        else -> {
            var v11: String = ""
            var v12: US48 = US48_RWhole
            var v13: US49 = US49_Entry
            var v14: String = ""
            var v15: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "c_tl"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RHsCtx
                    var v8: US49 = US49_Entry
                    v8 = US49_Data
                    var v9: String = ""
                    v9 = "ThreadLocal context"
                    var v10: String = ""
                    v10 = "root context 0 + one per body thread"
                    v11 = v6
                    v12 = v7
                    v13 = v8
                    v14 = v9
                    v15 = v10
                }
                else -> throw RuntimeException()
            }
            v16 = v11
            v17 = v12
            v18 = v13
            v19 = v14
            v20 = v15
        }
    }
    return SpiralTuple6(v16, v17, v18, v19, v20)
}
fun sp_info_of_diverge_95(p0: US42): SpiralTuple6 {
    var v0: US42 = p0
    var v76: String = ""
    var v77: US48 = US48_RWhole
    var v78: US49 = US49_Entry
    var v79: String = ""
    var v80: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "d_order"
            var v2: US48 = US48_RWhole
            v2 = US48_RDiverge
            var v3: US49 = US49_Entry
            v3 = US49_Note
            var v4: String = ""
            v4 = "Order"
            var v5: String = ""
            v5 = "SF: depth first, a body runs where it is met. HS: annotated bodies are deferred; markers keep their place; Finish restores the canonical order"
            v76 = v1
            v77 = v2
            v78 = v3
            v79 = v4
            v80 = v5
        }
        else -> {
            var v71: String = ""
            var v72: US48 = US48_RWhole
            var v73: US49 = US49_Entry
            var v74: String = ""
            var v75: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "d_err"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RDiverge
                    var v8: US49 = US49_Entry
                    v8 = US49_Note
                    var v9: String = ""
                    v9 = "Errors"
                    var v10: String = ""
                    v10 = "SF: the first exception propagates. HS: a failure becomes a 'fail' marker; Finish raises the first one in canonical order"
                    v71 = v6
                    v72 = v7
                    v73 = v8
                    v74 = v9
                    v75 = v10
                }
                else -> {
                    var v66: String = ""
                    var v67: US48 = US48_RWhole
                    var v68: US49 = US49_Entry
                    var v69: String = ""
                    var v70: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "d_print"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RDiverge
                            var v13: US49 = US49_Entry
                            v13 = US49_Note
                            var v14: String = ""
                            v14 = "Printing"
                            var v15: String = ""
                            v15 = "SF: printfn immediately. HS: buffered as 'print' markers and replayed in order"
                            v66 = v11
                            v67 = v12
                            v68 = v13
                            v69 = v14
                            v70 = v15
                        }
                        else -> {
                            var v61: String = ""
                            var v62: US48 = US48_RWhole
                            var v63: US49 = US49_Entry
                            var v64: String = ""
                            var v65: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "d_rec"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RDiverge
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Note
                                    var v19: String = ""
                                    v19 = "Recursion"
                                    var v20: String = ""
                                    v20 = "SF: re-entering an owned key finds the pending value: unannotated -> 'Recursive join points must be annotated'; announce made the annotated type usable. HS: AnnounceOp also releases threads waiting on the key"
                                    v61 = v16
                                    v62 = v17
                                    v63 = v18
                                    v64 = v19
                                    v65 = v20
                                }
                                else -> {
                                    var v56: String = ""
                                    var v57: US48 = US48_RWhole
                                    var v58: US49 = US49_Entry
                                    var v59: String = ""
                                    var v60: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "d_conf"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RDiverge
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Note
                                            var v24: String = ""
                                            v24 = "Conflicts"
                                            var v25: String = ""
                                            v25 = "SF: impossible. HS: a wait-for cycle or a foreign claim of an abandoned key -> HopacsotaConflict -> whole peval rerun with inline_restart = true (single-flight order)"
                                            v56 = v21
                                            v57 = v22
                                            v58 = v23
                                            v59 = v24
                                            v60 = v25
                                        }
                                        else -> {
                                            var v51: String = ""
                                            var v52: US48 = US48_RWhole
                                            var v53: US49 = US49_Entry
                                            var v54: String = ""
                                            var v55: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "d_nest"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RDiverge
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Note
                                                    var v29: String = ""
                                                    v29 = "Counters"
                                                    var v30: String = ""
                                                    v30 = "SF: one shared uninhabited counter. HS: Nesting per BodyContext; dynamic_if/validating saved and zeroed per body"
                                                    v51 = v26
                                                    v52 = v27
                                                    v53 = v28
                                                    v54 = v29
                                                    v55 = v30
                                                }
                                                else -> {
                                                    var v46: String = ""
                                                    var v47: US48 = US48_RWhole
                                                    var v48: US49 = US49_Entry
                                                    var v49: String = ""
                                                    var v50: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "d_globals"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RDiverge
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Note
                                                            var v34: String = ""
                                                            v34 = "Validation globals"
                                                            var v35: String = ""
                                                            v35 = "SF: validation_root_globals for the build backend. HS: s.globals of the current key body"
                                                            v46 = v31
                                                            v47 = v32
                                                            v48 = v33
                                                            v49 = v34
                                                            v50 = v35
                                                        }
                                                        else -> {
                                                            var v41: String = ""
                                                            var v42: US48 = US48_RWhole
                                                            var v43: US49 = US49_Entry
                                                            var v44: String = ""
                                                            var v45: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "d_same"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RDiverge
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Note
                                                                    var v39: String = ""
                                                                    v39 = "Same result"
                                                                    var v40: String = ""
                                                                    v40 = "both builds must emit byte-identical code; the tables passed to codegen are the same Dictionary instances"
                                                                    v41 = v36
                                                                    v42 = v37
                                                                    v43 = v38
                                                                    v44 = v39
                                                                    v45 = v40
                                                                }
                                                                else -> throw RuntimeException()
                                                            }
                                                            v46 = v41
                                                            v47 = v42
                                                            v48 = v43
                                                            v49 = v44
                                                            v50 = v45
                                                        }
                                                    }
                                                    v51 = v46
                                                    v52 = v47
                                                    v53 = v48
                                                    v54 = v49
                                                    v55 = v50
                                                }
                                            }
                                            v56 = v51
                                            v57 = v52
                                            v58 = v53
                                            v59 = v54
                                            v60 = v55
                                        }
                                    }
                                    v61 = v56
                                    v62 = v57
                                    v63 = v58
                                    v64 = v59
                                    v65 = v60
                                }
                            }
                            v66 = v61
                            v67 = v62
                            v68 = v63
                            v69 = v64
                            v70 = v65
                        }
                    }
                    v71 = v66
                    v72 = v67
                    v73 = v68
                    v74 = v69
                    v75 = v70
                }
            }
            v76 = v71
            v77 = v72
            v78 = v73
            v79 = v74
            v80 = v75
        }
    }
    return SpiralTuple6(v76, v77, v78, v79, v80)
}
fun sp_info_of_codegen_96(p0: US43): SpiralTuple6 {
    var v0: US43 = p0
    var v46: String = ""
    var v47: US48 = US48_RWhole
    var v48: US49 = US49_Entry
    var v49: String = ""
    var v50: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "cg_adapter"
            var v2: US48 = US48_RWhole
            v2 = US48_RCodegen
            var v3: US49 = US49_Entry
            v3 = US49_Seam
            var v4: String = ""
            v4 = "CodegenAdapter (L12478)"
            var v5: String = ""
            v5 = "codegenMethodBody / codegenClosureBody read join_point_method/closure"
            v46 = v1
            v47 = v2
            v48 = v3
            v49 = v4
            v50 = v5
        }
        else -> {
            var v41: String = ""
            var v42: US48 = US48_RWhole
            var v43: US49 = US49_Entry
            var v44: String = ""
            var v45: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "cg_utils"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RCodegen
                    var v8: US49 = US49_Entry
                    v8 = US49_Fn
                    var v9: String = ""
                    v9 = "CodegenUtils (L12005)"
                    var v10: String = ""
                    v10 = "CodegenEnv, line/indent, generated names"
                    v41 = v6
                    v42 = v7
                    v43 = v8
                    v44 = v9
                    v45 = v10
                }
                else -> {
                    var v36: String = ""
                    var v37: US48 = US48_RWhole
                    var v38: US49 = US49_Entry
                    var v39: String = ""
                    var v40: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "cg_dead"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RCodegen
                            var v13: US49 = US49_Entry
                            v13 = US49_Fn
                            var v14: String = ""
                            v14 = "DeadPureLetElision (L12296)"
                            var v15: String = ""
                            v15 = "drops pure lets before emit"
                            v36 = v11
                            v37 = v12
                            v38 = v13
                            v39 = v14
                            v40 = v15
                        }
                        else -> {
                            var v31: String = ""
                            var v32: US48 = US48_RWhole
                            var v33: US49 = US49_Entry
                            var v34: String = ""
                            var v35: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "cg_unused"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RCodegen
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Fn
                                    var v19: String = ""
                                    v19 = "UnusedGlobals.prune (L12260)"
                                    var v20: String = ""
                                    v20 = "prunes result.globals"
                                    v31 = v16
                                    v32 = v17
                                    v33 = v18
                                    v34 = v19
                                    v35 = v20
                                }
                                else -> {
                                    var v26: String = ""
                                    var v27: US48 = US48_RWhole
                                    var v28: US49 = US49_Entry
                                    var v29: String = ""
                                    var v30: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "cg_refc"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RCodegen
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Fn
                                            var v24: String = ""
                                            v24 = "RefCounting (L24644)"
                                            var v25: String = ""
                                            v25 = "refc_prepass for C"
                                            v26 = v21
                                            v27 = v22
                                            v28 = v23
                                            v29 = v24
                                            v30 = v25
                                        }
                                        else -> throw RuntimeException()
                                    }
                                    v31 = v26
                                    v32 = v27
                                    v33 = v28
                                    v34 = v29
                                    v35 = v30
                                }
                            }
                            v36 = v31
                            v37 = v32
                            v38 = v33
                            v39 = v34
                            v40 = v35
                        }
                    }
                    v41 = v36
                    v42 = v37
                    v43 = v38
                    v44 = v39
                    v45 = v40
                }
            }
            v46 = v41
            v47 = v42
            v48 = v43
            v49 = v44
            v50 = v45
        }
    }
    return SpiralTuple6(v46, v47, v48, v49, v50)
}
fun sp_info_of_backends_97(p0: US44): SpiralTuple6 {
    var v0: US44 = p0
    var v186: String = ""
    var v187: US48 = US48_RWhole
    var v188: US49 = US49_Entry
    var v189: String = ""
    var v190: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "b_fsharp"
            var v2: US48 = US48_RWhole
            v2 = US48_RBackends
            var v3: US49 = US49_Entry
            v3 = US49_Be
            var v4: String = ""
            v4 = "F#"
            var v5: String = ""
            v5 = "codegenFsharp L12592"
            v186 = v1
            v187 = v2
            v188 = v3
            v189 = v4
            v190 = v5
        }
        else -> {
            var v181: String = ""
            var v182: US48 = US48_RWhole
            var v183: US49 = US49_Entry
            var v184: String = ""
            var v185: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "b_rust"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RBackends
                    var v8: US49 = US49_Entry
                    v8 = US49_Be
                    var v9: String = ""
                    v9 = "Rust"
                    var v10: String = ""
                    v10 = "codegenRust L13525"
                    v181 = v6
                    v182 = v7
                    v183 = v8
                    v184 = v9
                    v185 = v10
                }
                else -> {
                    var v176: String = ""
                    var v177: US48 = US48_RWhole
                    var v178: US49 = US49_Entry
                    var v179: String = ""
                    var v180: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "b_delphi"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RBackends
                            var v13: US49 = US49_Entry
                            v13 = US49_Be
                            var v14: String = ""
                            v14 = "Delphi"
                            var v15: String = ""
                            v15 = "L14686"
                            v176 = v11
                            v177 = v12
                            v178 = v13
                            v179 = v14
                            v180 = v15
                        }
                        else -> {
                            var v171: String = ""
                            var v172: US48 = US48_RWhole
                            var v173: US49 = US49_Entry
                            var v174: String = ""
                            var v175: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "b_zig"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RBackends
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Be
                                    var v19: String = ""
                                    v19 = "Zig"
                                    var v20: String = ""
                                    v20 = "L15368"
                                    v171 = v16
                                    v172 = v17
                                    v173 = v18
                                    v174 = v19
                                    v175 = v20
                                }
                                else -> {
                                    var v166: String = ""
                                    var v167: US48 = US48_RWhole
                                    var v168: US49 = US49_Entry
                                    var v169: String = ""
                                    var v170: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "b_lean"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RBackends
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Be
                                            var v24: String = ""
                                            v24 = "Lean"
                                            var v25: String = ""
                                            v25 = "L15966"
                                            v166 = v21
                                            v167 = v22
                                            v168 = v23
                                            v169 = v24
                                            v170 = v25
                                        }
                                        else -> {
                                            var v161: String = ""
                                            var v162: US48 = US48_RWhole
                                            var v163: US49 = US49_Entry
                                            var v164: String = ""
                                            var v165: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "b_bend"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RBackends
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Be
                                                    var v29: String = ""
                                                    v29 = "Bend"
                                                    var v30: String = ""
                                                    v30 = "L16804"
                                                    v161 = v26
                                                    v162 = v27
                                                    v163 = v28
                                                    v164 = v29
                                                    v165 = v30
                                                }
                                                else -> {
                                                    var v156: String = ""
                                                    var v157: US48 = US48_RWhole
                                                    var v158: US49 = US49_Entry
                                                    var v159: String = ""
                                                    var v160: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "b_gleam"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RBackends
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Be
                                                            var v34: String = ""
                                                            v34 = "Gleam"
                                                            var v35: String = ""
                                                            v35 = "L17745"
                                                            v156 = v31
                                                            v157 = v32
                                                            v158 = v33
                                                            v159 = v34
                                                            v160 = v35
                                                        }
                                                        else -> {
                                                            var v151: String = ""
                                                            var v152: US48 = US48_RWhole
                                                            var v153: US49 = US49_Entry
                                                            var v154: String = ""
                                                            var v155: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "b_lua"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RBackends
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Be
                                                                    var v39: String = ""
                                                                    v39 = "Lua"
                                                                    var v40: String = ""
                                                                    v40 = "L18640"
                                                                    v151 = v36
                                                                    v152 = v37
                                                                    v153 = v38
                                                                    v154 = v39
                                                                    v155 = v40
                                                                }
                                                                else -> {
                                                                    var v146: String = ""
                                                                    var v147: US48 = US48_RWhole
                                                                    var v148: US49 = US49_Entry
                                                                    var v149: String = ""
                                                                    var v150: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v41: String = ""
                                                                            v41 = "b_wasm"
                                                                            var v42: US48 = US48_RWhole
                                                                            v42 = US48_RBackends
                                                                            var v43: US49 = US49_Entry
                                                                            v43 = US49_Be
                                                                            var v44: String = ""
                                                                            v44 = "Wasm"
                                                                            var v45: String = ""
                                                                            v45 = "L19899"
                                                                            v146 = v41
                                                                            v147 = v42
                                                                            v148 = v43
                                                                            v149 = v44
                                                                            v150 = v45
                                                                        }
                                                                        else -> {
                                                                            var v141: String = ""
                                                                            var v142: US48 = US48_RWhole
                                                                            var v143: US49 = US49_Entry
                                                                            var v144: String = ""
                                                                            var v145: String = ""
                                                                            when (v0.tag) {
                                                                                9 -> {
                                                                                    var v46: String = ""
                                                                                    v46 = "b_wgsl"
                                                                                    var v47: US48 = US48_RWhole
                                                                                    v47 = US48_RBackends
                                                                                    var v48: US49 = US49_Entry
                                                                                    v48 = US49_Be
                                                                                    var v49: String = ""
                                                                                    v49 = "WGSL"
                                                                                    var v50: String = ""
                                                                                    v50 = "L20576"
                                                                                    v141 = v46
                                                                                    v142 = v47
                                                                                    v143 = v48
                                                                                    v144 = v49
                                                                                    v145 = v50
                                                                                }
                                                                                else -> {
                                                                                    var v136: String = ""
                                                                                    var v137: US48 = US48_RWhole
                                                                                    var v138: US49 = US49_Entry
                                                                                    var v139: String = ""
                                                                                    var v140: String = ""
                                                                                    when (v0.tag) {
                                                                                        10 -> {
                                                                                            var v51: String = ""
                                                                                            v51 = "b_calyx"
                                                                                            var v52: US48 = US48_RWhole
                                                                                            v52 = US48_RBackends
                                                                                            var v53: US49 = US49_Entry
                                                                                            v53 = US49_Be
                                                                                            var v54: String = ""
                                                                                            v54 = "Calyx"
                                                                                            var v55: String = ""
                                                                                            v55 = "L21151"
                                                                                            v136 = v51
                                                                                            v137 = v52
                                                                                            v138 = v53
                                                                                            v139 = v54
                                                                                            v140 = v55
                                                                                        }
                                                                                        else -> {
                                                                                            var v131: String = ""
                                                                                            var v132: US48 = US48_RWhole
                                                                                            var v133: US49 = US49_Entry
                                                                                            var v134: String = ""
                                                                                            var v135: String = ""
                                                                                            when (v0.tag) {
                                                                                                11 -> {
                                                                                                    var v56: String = ""
                                                                                                    v56 = "b_kotlin"
                                                                                                    var v57: US48 = US48_RWhole
                                                                                                    v57 = US48_RBackends
                                                                                                    var v58: US49 = US49_Entry
                                                                                                    v58 = US49_Be
                                                                                                    var v59: String = ""
                                                                                                    v59 = "Kotlin"
                                                                                                    var v60: String = ""
                                                                                                    v60 = "L21686"
                                                                                                    v131 = v56
                                                                                                    v132 = v57
                                                                                                    v133 = v58
                                                                                                    v134 = v59
                                                                                                    v135 = v60
                                                                                                }
                                                                                                else -> {
                                                                                                    var v126: String = ""
                                                                                                    var v127: US48 = US48_RWhole
                                                                                                    var v128: US49 = US49_Entry
                                                                                                    var v129: String = ""
                                                                                                    var v130: String = ""
                                                                                                    when (v0.tag) {
                                                                                                        12 -> {
                                                                                                            var v61: String = ""
                                                                                                            v61 = "b_swift"
                                                                                                            var v62: US48 = US48_RWhole
                                                                                                            v62 = US48_RBackends
                                                                                                            var v63: US49 = US49_Entry
                                                                                                            v63 = US49_Be
                                                                                                            var v64: String = ""
                                                                                                            v64 = "Swift"
                                                                                                            var v65: String = ""
                                                                                                            v65 = "L22493"
                                                                                                            v126 = v61
                                                                                                            v127 = v62
                                                                                                            v128 = v63
                                                                                                            v129 = v64
                                                                                                            v130 = v65
                                                                                                        }
                                                                                                        else -> {
                                                                                                            var v121: String = ""
                                                                                                            var v122: US48 = US48_RWhole
                                                                                                            var v123: US49 = US49_Entry
                                                                                                            var v124: String = ""
                                                                                                            var v125: String = ""
                                                                                                            when (v0.tag) {
                                                                                                                13 -> {
                                                                                                                    var v66: String = ""
                                                                                                                    v66 = "b_qasm"
                                                                                                                    var v67: US48 = US48_RWhole
                                                                                                                    v67 = US48_RBackends
                                                                                                                    var v68: US49 = US49_Entry
                                                                                                                    v68 = US49_Be
                                                                                                                    var v69: String = ""
                                                                                                                    v69 = "OpenQASM"
                                                                                                                    var v70: String = ""
                                                                                                                    v70 = "L23243"
                                                                                                                    v121 = v66
                                                                                                                    v122 = v67
                                                                                                                    v123 = v68
                                                                                                                    v124 = v69
                                                                                                                    v125 = v70
                                                                                                                }
                                                                                                                else -> {
                                                                                                                    var v116: String = ""
                                                                                                                    var v117: US48 = US48_RWhole
                                                                                                                    var v118: US49 = US49_Entry
                                                                                                                    var v119: String = ""
                                                                                                                    var v120: String = ""
                                                                                                                    when (v0.tag) {
                                                                                                                        14 -> {
                                                                                                                            var v71: String = ""
                                                                                                                            v71 = "b_ebpf"
                                                                                                                            var v72: US48 = US48_RWhole
                                                                                                                            v72 = US48_RBackends
                                                                                                                            var v73: US49 = US49_Entry
                                                                                                                            v73 = US49_Be
                                                                                                                            var v74: String = ""
                                                                                                                            v74 = "eBPF"
                                                                                                                            var v75: String = ""
                                                                                                                            v75 = "L23559"
                                                                                                                            v116 = v71
                                                                                                                            v117 = v72
                                                                                                                            v118 = v73
                                                                                                                            v119 = v74
                                                                                                                            v120 = v75
                                                                                                                        }
                                                                                                                        else -> {
                                                                                                                            var v111: String = ""
                                                                                                                            var v112: US48 = US48_RWhole
                                                                                                                            var v113: US49 = US49_Entry
                                                                                                                            var v114: String = ""
                                                                                                                            var v115: String = ""
                                                                                                                            when (v0.tag) {
                                                                                                                                15 -> {
                                                                                                                                    var v76: String = ""
                                                                                                                                    v76 = "b_ts"
                                                                                                                                    var v77: US48 = US48_RWhole
                                                                                                                                    v77 = US48_RBackends
                                                                                                                                    var v78: US49 = US49_Entry
                                                                                                                                    v78 = US49_Be
                                                                                                                                    var v79: String = ""
                                                                                                                                    v79 = "TypeScript"
                                                                                                                                    var v80: String = ""
                                                                                                                                    v80 = "L24026"
                                                                                                                                    v111 = v76
                                                                                                                                    v112 = v77
                                                                                                                                    v113 = v78
                                                                                                                                    v114 = v79
                                                                                                                                    v115 = v80
                                                                                                                                }
                                                                                                                                else -> {
                                                                                                                                    var v106: String = ""
                                                                                                                                    var v107: US48 = US48_RWhole
                                                                                                                                    var v108: US49 = US49_Entry
                                                                                                                                    var v109: String = ""
                                                                                                                                    var v110: String = ""
                                                                                                                                    when (v0.tag) {
                                                                                                                                        16 -> {
                                                                                                                                            var v81: String = ""
                                                                                                                                            v81 = "b_c"
                                                                                                                                            var v82: US48 = US48_RWhole
                                                                                                                                            v82 = US48_RBackends
                                                                                                                                            var v83: US49 = US49_Entry
                                                                                                                                            v83 = US49_Be
                                                                                                                                            var v84: String = ""
                                                                                                                                            v84 = "C"
                                                                                                                                            var v85: String = ""
                                                                                                                                            v85 = "L24781"
                                                                                                                                            v106 = v81
                                                                                                                                            v107 = v82
                                                                                                                                            v108 = v83
                                                                                                                                            v109 = v84
                                                                                                                                            v110 = v85
                                                                                                                                        }
                                                                                                                                        else -> {
                                                                                                                                            var v101: String = ""
                                                                                                                                            var v102: US48 = US48_RWhole
                                                                                                                                            var v103: US49 = US49_Entry
                                                                                                                                            var v104: String = ""
                                                                                                                                            var v105: String = ""
                                                                                                                                            when (v0.tag) {
                                                                                                                                                17 -> {
                                                                                                                                                    var v86: String = ""
                                                                                                                                                    v86 = "b_cpp"
                                                                                                                                                    var v87: US48 = US48_RWhole
                                                                                                                                                    v87 = US48_RBackends
                                                                                                                                                    var v88: US49 = US49_Entry
                                                                                                                                                    v88 = US49_Be
                                                                                                                                                    var v89: String = ""
                                                                                                                                                    v89 = "Cpp + Cuda"
                                                                                                                                                    var v90: String = ""
                                                                                                                                                    v90 = "L26089"
                                                                                                                                                    v101 = v86
                                                                                                                                                    v102 = v87
                                                                                                                                                    v103 = v88
                                                                                                                                                    v104 = v89
                                                                                                                                                    v105 = v90
                                                                                                                                                }
                                                                                                                                                else -> {
                                                                                                                                                    var v96: String = ""
                                                                                                                                                    var v97: US48 = US48_RWhole
                                                                                                                                                    var v98: US49 = US49_Entry
                                                                                                                                                    var v99: String = ""
                                                                                                                                                    var v100: String = ""
                                                                                                                                                    when (v0.tag) {
                                                                                                                                                        18 -> {
                                                                                                                                                            var v91: String = ""
                                                                                                                                                            v91 = "b_py"
                                                                                                                                                            var v92: US48 = US48_RWhole
                                                                                                                                                            v92 = US48_RBackends
                                                                                                                                                            var v93: US49 = US49_Entry
                                                                                                                                                            v93 = US49_Be
                                                                                                                                                            var v94: String = ""
                                                                                                                                                            v94 = "Python + Cuda"
                                                                                                                                                            var v95: String = ""
                                                                                                                                                            v95 = "L27214"
                                                                                                                                                            v96 = v91
                                                                                                                                                            v97 = v92
                                                                                                                                                            v98 = v93
                                                                                                                                                            v99 = v94
                                                                                                                                                            v100 = v95
                                                                                                                                                        }
                                                                                                                                                        else -> throw RuntimeException()
                                                                                                                                                    }
                                                                                                                                                    v101 = v96
                                                                                                                                                    v102 = v97
                                                                                                                                                    v103 = v98
                                                                                                                                                    v104 = v99
                                                                                                                                                    v105 = v100
                                                                                                                                                }
                                                                                                                                            }
                                                                                                                                            v106 = v101
                                                                                                                                            v107 = v102
                                                                                                                                            v108 = v103
                                                                                                                                            v109 = v104
                                                                                                                                            v110 = v105
                                                                                                                                        }
                                                                                                                                    }
                                                                                                                                    v111 = v106
                                                                                                                                    v112 = v107
                                                                                                                                    v113 = v108
                                                                                                                                    v114 = v109
                                                                                                                                    v115 = v110
                                                                                                                                }
                                                                                                                            }
                                                                                                                            v116 = v111
                                                                                                                            v117 = v112
                                                                                                                            v118 = v113
                                                                                                                            v119 = v114
                                                                                                                            v120 = v115
                                                                                                                        }
                                                                                                                    }
                                                                                                                    v121 = v116
                                                                                                                    v122 = v117
                                                                                                                    v123 = v118
                                                                                                                    v124 = v119
                                                                                                                    v125 = v120
                                                                                                                }
                                                                                                            }
                                                                                                            v126 = v121
                                                                                                            v127 = v122
                                                                                                            v128 = v123
                                                                                                            v129 = v124
                                                                                                            v130 = v125
                                                                                                        }
                                                                                                    }
                                                                                                    v131 = v126
                                                                                                    v132 = v127
                                                                                                    v133 = v128
                                                                                                    v134 = v129
                                                                                                    v135 = v130
                                                                                                }
                                                                                            }
                                                                                            v136 = v131
                                                                                            v137 = v132
                                                                                            v138 = v133
                                                                                            v139 = v134
                                                                                            v140 = v135
                                                                                        }
                                                                                    }
                                                                                    v141 = v136
                                                                                    v142 = v137
                                                                                    v143 = v138
                                                                                    v144 = v139
                                                                                    v145 = v140
                                                                                }
                                                                            }
                                                                            v146 = v141
                                                                            v147 = v142
                                                                            v148 = v143
                                                                            v149 = v144
                                                                            v150 = v145
                                                                        }
                                                                    }
                                                                    v151 = v146
                                                                    v152 = v147
                                                                    v153 = v148
                                                                    v154 = v149
                                                                    v155 = v150
                                                                }
                                                            }
                                                            v156 = v151
                                                            v157 = v152
                                                            v158 = v153
                                                            v159 = v154
                                                            v160 = v155
                                                        }
                                                    }
                                                    v161 = v156
                                                    v162 = v157
                                                    v163 = v158
                                                    v164 = v159
                                                    v165 = v160
                                                }
                                            }
                                            v166 = v161
                                            v167 = v162
                                            v168 = v163
                                            v169 = v164
                                            v170 = v165
                                        }
                                    }
                                    v171 = v166
                                    v172 = v167
                                    v173 = v168
                                    v174 = v169
                                    v175 = v170
                                }
                            }
                            v176 = v171
                            v177 = v172
                            v178 = v173
                            v179 = v174
                            v180 = v175
                        }
                    }
                    v181 = v176
                    v182 = v177
                    v183 = v178
                    v184 = v179
                    v185 = v180
                }
            }
            v186 = v181
            v187 = v182
            v188 = v183
            v189 = v184
            v190 = v185
        }
    }
    return SpiralTuple6(v186, v187, v188, v189, v190)
}
fun sp_info_of_server_98(p0: US45): SpiralTuple6 {
    var v0: US45 = p0
    var v76: String = ""
    var v77: US48 = US48_RWhole
    var v78: US49 = US49_Entry
    var v79: String = ""
    var v80: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "sv_main"
            var v2: US48 = US48_RWhole
            v2 = US48_RServer
            var v3: US49 = US49_Entry
            v3 = US49_Entry
            var v4: String = ""
            v4 = "main (L35517)"
            var v5: String = ""
            v5 = "CLI / server start"
            v76 = v1
            v77 = v2
            v78 = v3
            v79 = v4
            v80 = v5
        }
        else -> {
            var v71: String = ""
            var v72: US48 = US48_RWhole
            var v73: US49 = US49_Entry
            var v74: String = ""
            var v75: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "sv_hub"
                    var v7: US48 = US48_RWhole
                    v7 = US48_RServer
                    var v8: US49 = US49_Entry
                    v8 = US49_Fn
                    var v9: String = ""
                    v9 = "SpiralHub (L35435)"
                    var v10: String = ""
                    v10 = "SignalR hub"
                    v71 = v6
                    v72 = v7
                    v73 = v8
                    v74 = v9
                    v75 = v10
                }
                else -> {
                    var v66: String = ""
                    var v67: US48 = US48_RWhole
                    var v68: US49 = US49_Entry
                    var v69: String = ""
                    var v70: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "sv_new"
                            var v12: US48 = US48_RWhole
                            v12 = US48_RServer
                            var v13: US49 = US49_Entry
                            v13 = US49_Fn
                            var v14: String = ""
                            v14 = "new_server (L35265)"
                            var v15: String = ""
                            v15 = "ServerHooks"
                            v66 = v11
                            v67 = v12
                            v68 = v13
                            v69 = v14
                            v70 = v15
                        }
                        else -> {
                            var v61: String = ""
                            var v62: US48 = US48_RWhole
                            var v63: US49 = US49_Entry
                            var v64: String = ""
                            var v65: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "sv_super"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_RServer
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Fn
                                    var v19: String = ""
                                    v19 = "supervisor_server (L34729)"
                                    var v20: String = ""
                                    v20 = "requests, project state, attention"
                                    v61 = v16
                                    v62 = v17
                                    v63 = v18
                                    v64 = v19
                                    v65 = v20
                                }
                                else -> {
                                    var v56: String = ""
                                    var v57: US48 = US48_RWhole
                                    var v58: US49 = US49_Entry
                                    var v59: String = ""
                                    var v60: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "sv_build"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_RServer
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Fn
                                            var v24: String = ""
                                            v24 = "BuildFile body (L35085)"
                                            var v25: String = ""
                                            v25 = "runs on its own 1.5 GB background thread"
                                            v56 = v21
                                            v57 = v22
                                            v58 = v23
                                            v59 = v24
                                            v60 = v25
                                        }
                                        else -> {
                                            var v51: String = ""
                                            var v52: US48 = US48_RWhole
                                            var v53: US49 = US49_Entry
                                            var v54: String = ""
                                            var v55: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "sv_many"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_RServer
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Fn
                                                    var v29: String = ""
                                                    v29 = "build_many (L35099)"
                                                    var v30: String = ""
                                                    v30 = "peval -> codegen -> RUST-GAP check"
                                                    v51 = v26
                                                    v52 = v27
                                                    v53 = v28
                                                    v54 = v29
                                                    v55 = v30
                                                }
                                                else -> {
                                                    var v46: String = ""
                                                    var v47: US48 = US48_RWhole
                                                    var v48: US49 = US49_Entry
                                                    var v49: String = ""
                                                    var v50: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "sv_out"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_RServer
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Fn
                                                            var v34: String = ""
                                                            v34 = "OutputFiles (L33636)"
                                                            var v35: String = ""
                                                            v35 = "write_output_atomically_async"
                                                            v46 = v31
                                                            v47 = v32
                                                            v48 = v33
                                                            v49 = v34
                                                            v50 = v35
                                                        }
                                                        else -> {
                                                            var v41: String = ""
                                                            var v42: US48 = US48_RWhole
                                                            var v43: US49 = US49_Entry
                                                            var v44: String = ""
                                                            var v45: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "sv_xcompat"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_RServer
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Fn
                                                                    var v39: String = ""
                                                                    v39 = "XCompat (L34179)"
                                                                    var v40: String = ""
                                                                    v40 = "postability warnings before a build"
                                                                    v41 = v36
                                                                    v42 = v37
                                                                    v43 = v38
                                                                    v44 = v39
                                                                    v45 = v40
                                                                }
                                                                else -> throw RuntimeException()
                                                            }
                                                            v46 = v41
                                                            v47 = v42
                                                            v48 = v43
                                                            v49 = v44
                                                            v50 = v45
                                                        }
                                                    }
                                                    v51 = v46
                                                    v52 = v47
                                                    v53 = v48
                                                    v54 = v49
                                                    v55 = v50
                                                }
                                            }
                                            v56 = v51
                                            v57 = v52
                                            v58 = v53
                                            v59 = v54
                                            v60 = v55
                                        }
                                    }
                                    v61 = v56
                                    v62 = v57
                                    v63 = v58
                                    v64 = v59
                                    v65 = v60
                                }
                            }
                            v66 = v61
                            v67 = v62
                            v68 = v63
                            v69 = v64
                            v70 = v65
                        }
                    }
                    v71 = v66
                    v72 = v67
                    v73 = v68
                    v74 = v69
                    v75 = v70
                }
            }
            v76 = v71
            v77 = v72
            v78 = v73
            v79 = v74
            v80 = v75
        }
    }
    return SpiralTuple6(v76, v77, v78, v79, v80)
}
fun sp_info_of_env_99(p0: US46): SpiralTuple6 {
    var v0: US46 = p0
    var v86: String = ""
    var v87: US48 = US48_RWhole
    var v88: US49 = US49_Entry
    var v89: String = ""
    var v90: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "env_workers"
            var v2: US48 = US48_RWhole
            v2 = US48_REnv
            var v3: US49 = US49_Entry
            v3 = US49_Cfg
            var v4: String = ""
            v4 = "SPIRAL_HOPACSOTA_WORKERS"
            var v5: String = ""
            v5 = "body threads (default = ProcessorCount)"
            v86 = v1
            v87 = v2
            v88 = v3
            v89 = v4
            v90 = v5
        }
        else -> {
            var v81: String = ""
            var v82: US48 = US48_RWhole
            var v83: US49 = US49_Entry
            var v84: String = ""
            var v85: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: String = ""
                    v6 = "env_policy"
                    var v7: US48 = US48_RWhole
                    v7 = US48_REnv
                    var v8: US49 = US49_Entry
                    v8 = US49_Cfg
                    var v9: String = ""
                    v9 = "SPIRAL_HOPACSOTA_POLICY"
                    var v10: String = ""
                    v10 = "inline | dfs | bfs | random:<seed> | parallel"
                    v81 = v6
                    v82 = v7
                    v83 = v8
                    v84 = v9
                    v85 = v10
                }
                else -> {
                    var v76: String = ""
                    var v77: US48 = US48_RWhole
                    var v78: US49 = US49_Entry
                    var v79: String = ""
                    var v80: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: String = ""
                            v11 = "env_stats"
                            var v12: US48 = US48_RWhole
                            v12 = US48_REnv
                            var v13: US49 = US49_Entry
                            v13 = US49_Cfg
                            var v14: String = ""
                            v14 = "SPIRAL_HOPACSOTA_STATS=1"
                            var v15: String = ""
                            v15 = "engine counters on Close"
                            v76 = v11
                            v77 = v12
                            v78 = v13
                            v79 = v14
                            v80 = v15
                        }
                        else -> {
                            var v71: String = ""
                            var v72: US48 = US48_RWhole
                            var v73: US49 = US49_Entry
                            var v74: String = ""
                            var v75: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: String = ""
                                    v16 = "env_census"
                                    var v17: US48 = US48_RWhole
                                    v17 = US48_REnv
                                    var v18: US49 = US49_Entry
                                    v18 = US49_Cfg
                                    var v19: String = ""
                                    v19 = "SPIRAL_HOPACSOTA_CENSUS_OUT"
                                    var v20: String = ""
                                    v20 = "per-body timing file"
                                    v71 = v16
                                    v72 = v17
                                    v73 = v18
                                    v74 = v19
                                    v75 = v20
                                }
                                else -> {
                                    var v66: String = ""
                                    var v67: US48 = US48_RWhole
                                    var v68: US49 = US49_Entry
                                    var v69: String = ""
                                    var v70: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: String = ""
                                            v21 = "env_force"
                                            var v22: US48 = US48_RWhole
                                            v22 = US48_REnv
                                            var v23: US49 = US49_Entry
                                            v23 = US49_Cfg
                                            var v24: String = ""
                                            v24 = "SPIRAL_HOPACSOTA_FORCE_CONFLICT=1"
                                            var v25: String = ""
                                            v25 = "test the restart path"
                                            v66 = v21
                                            v67 = v22
                                            v68 = v23
                                            v69 = v24
                                            v70 = v25
                                        }
                                        else -> {
                                            var v61: String = ""
                                            var v62: US48 = US48_RWhole
                                            var v63: US49 = US49_Entry
                                            var v64: String = ""
                                            var v65: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: String = ""
                                                    v26 = "env_gap"
                                                    var v27: US48 = US48_RWhole
                                                    v27 = US48_REnv
                                                    var v28: US49 = US49_Entry
                                                    v28 = US49_Cfg
                                                    var v29: String = ""
                                                    v29 = "SPIRAL_RUST_GAP_TRACE=1"
                                                    var v30: String = ""
                                                    v30 = "forces 1 evaluating thread"
                                                    v61 = v26
                                                    v62 = v27
                                                    v63 = v28
                                                    v64 = v29
                                                    v65 = v30
                                                }
                                                else -> {
                                                    var v56: String = ""
                                                    var v57: US48 = US48_RWhole
                                                    var v58: US49 = US49_Entry
                                                    var v59: String = ""
                                                    var v60: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: String = ""
                                                            v31 = "env_if"
                                                            var v32: US48 = US48_RWhole
                                                            v32 = US48_REnv
                                                            var v33: US49 = US49_Entry
                                                            v33 = US49_Cfg
                                                            var v34: String = ""
                                                            v34 = "SPIRAL_IF_NESTING_LIMIT"
                                                            var v35: String = ""
                                                            v35 = "dynamic if depth guard"
                                                            v56 = v31
                                                            v57 = v32
                                                            v58 = v33
                                                            v59 = v34
                                                            v60 = v35
                                                        }
                                                        else -> {
                                                            var v51: String = ""
                                                            var v52: US48 = US48_RWhole
                                                            var v53: US49 = US49_Entry
                                                            var v54: String = ""
                                                            var v55: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: String = ""
                                                                    v36 = "env_union"
                                                                    var v37: US48 = US48_RWhole
                                                                    v37 = US48_REnv
                                                                    var v38: US49 = US49_Entry
                                                                    v38 = US49_Cfg
                                                                    var v39: String = ""
                                                                    v39 = "SPIRAL_UNION_NESTING_LIMIT"
                                                                    var v40: String = ""
                                                                    v40 = "union nesting guard"
                                                                    v51 = v36
                                                                    v52 = v37
                                                                    v53 = v38
                                                                    v54 = v39
                                                                    v55 = v40
                                                                }
                                                                else -> {
                                                                    var v46: String = ""
                                                                    var v47: US48 = US48_RWhole
                                                                    var v48: US49 = US49_Entry
                                                                    var v49: String = ""
                                                                    var v50: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v41: String = ""
                                                                            v41 = "env_define"
                                                                            var v42: US48 = US48_RWhole
                                                                            v42 = US48_REnv
                                                                            var v43: US49 = US49_Entry
                                                                            v43 = US49_Cfg
                                                                            var v44: String = ""
                                                                            v44 = "build define SPIRAL_CORE_HOPACSOTA"
                                                                            var v45: String = ""
                                                                            v45 = "build.ps1 -Mode hopacsota vs -Mode single-flight"
                                                                            v46 = v41
                                                                            v47 = v42
                                                                            v48 = v43
                                                                            v49 = v44
                                                                            v50 = v45
                                                                        }
                                                                        else -> throw RuntimeException()
                                                                    }
                                                                    v51 = v46
                                                                    v52 = v47
                                                                    v53 = v48
                                                                    v54 = v49
                                                                    v55 = v50
                                                                }
                                                            }
                                                            v56 = v51
                                                            v57 = v52
                                                            v58 = v53
                                                            v59 = v54
                                                            v60 = v55
                                                        }
                                                    }
                                                    v61 = v56
                                                    v62 = v57
                                                    v63 = v58
                                                    v64 = v59
                                                    v65 = v60
                                                }
                                            }
                                            v66 = v61
                                            v67 = v62
                                            v68 = v63
                                            v69 = v64
                                            v70 = v65
                                        }
                                    }
                                    v71 = v66
                                    v72 = v67
                                    v73 = v68
                                    v74 = v69
                                    v75 = v70
                                }
                            }
                            v76 = v71
                            v77 = v72
                            v78 = v73
                            v79 = v74
                            v80 = v75
                        }
                    }
                    v81 = v76
                    v82 = v77
                    v83 = v78
                    v84 = v79
                    v85 = v80
                }
            }
            v86 = v81
            v87 = v82
            v88 = v83
            v89 = v84
            v90 = v85
        }
    }
    return SpiralTuple6(v86, v87, v88, v89, v90)
}
fun sp_info_of_part_77(p0: US24): SpiralTuple6 {
    var v0: US24 = p0
    var v238: String = ""
    var v239: US48 = US48_RWhole
    var v240: US49 = US49_Entry
    var v241: String = ""
    var v242: String = ""
    when (v0.tag) {
        0 -> {
            var v1: US25 = (v0 as US24_PInfra).f0
            val spiral_tuple299 = sp_info_of_infra_78(v1)
            v238 = spiral_tuple299.v0
            v239 = spiral_tuple299.v1
            v240 = spiral_tuple299.v2
            v241 = spiral_tuple299.v3
            v242 = spiral_tuple299.v4
        }
        else -> {
            var v233: String = ""
            var v234: US48 = US48_RWhole
            var v235: US49 = US49_Entry
            var v236: String = ""
            var v237: String = ""
            when (v0.tag) {
                1 -> {
                    var v7: US26 = (v0 as US24_PFront).f0
                    val spiral_tuple867 = sp_info_of_front_79(v7)
                    v233 = spiral_tuple867.v0
                    v234 = spiral_tuple867.v1
                    v235 = spiral_tuple867.v2
                    v236 = spiral_tuple867.v3
                    v237 = spiral_tuple867.v4
                }
                else -> {
                    var v228: String = ""
                    var v229: US48 = US48_RWhole
                    var v230: US49 = US49_Entry
                    var v231: String = ""
                    var v232: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v13: US27 = (v0 as US24_PProj).f0
                            val spiral_tuple1563 = sp_info_of_proj_80(v13)
                            v228 = spiral_tuple1563.v0
                            v229 = spiral_tuple1563.v1
                            v230 = spiral_tuple1563.v2
                            v231 = spiral_tuple1563.v3
                            v232 = spiral_tuple1563.v4
                        }
                        else -> {
                            var v223: String = ""
                            var v224: US48 = US48_RWhole
                            var v225: US49 = US49_Entry
                            var v226: String = ""
                            var v227: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v19: US28 = (v0 as US24_PTyping).f0
                                    val spiral_tuple2395 = sp_info_of_typing_81(v19)
                                    v223 = spiral_tuple2395.v0
                                    v224 = spiral_tuple2395.v1
                                    v225 = spiral_tuple2395.v2
                                    v226 = spiral_tuple2395.v3
                                    v227 = spiral_tuple2395.v4
                                }
                                else -> {
                                    var v218: String = ""
                                    var v219: US48 = US48_RWhole
                                    var v220: US49 = US49_Entry
                                    var v221: String = ""
                                    var v222: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v25: US29 = (v0 as US24_PPrep).f0
                                            val spiral_tuple3355 = sp_info_of_prep_82(v25)
                                            v218 = spiral_tuple3355.v0
                                            v219 = spiral_tuple3355.v1
                                            v220 = spiral_tuple3355.v2
                                            v221 = spiral_tuple3355.v3
                                            v222 = spiral_tuple3355.v4
                                        }
                                        else -> {
                                            var v213: String = ""
                                            var v214: US48 = US48_RWhole
                                            var v215: US49 = US49_Entry
                                            var v216: String = ""
                                            var v217: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v31: US30 = (v0 as US24_PPe).f0
                                                    val spiral_tuple4439 = sp_info_of_pe_83(v31)
                                                    v213 = spiral_tuple4439.v0
                                                    v214 = spiral_tuple4439.v1
                                                    v215 = spiral_tuple4439.v2
                                                    v216 = spiral_tuple4439.v3
                                                    v217 = spiral_tuple4439.v4
                                                }
                                                else -> {
                                                    var v208: String = ""
                                                    var v209: US48 = US48_RWhole
                                                    var v210: US49 = US49_Entry
                                                    var v211: String = ""
                                                    var v212: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v37: US31 = (v0 as US24_PPeTables).f0
                                                            val spiral_tuple5655 = sp_info_of_pe_tables_84(v37)
                                                            v208 = spiral_tuple5655.v0
                                                            v209 = spiral_tuple5655.v1
                                                            v210 = spiral_tuple5655.v2
                                                            v211 = spiral_tuple5655.v3
                                                            v212 = spiral_tuple5655.v4
                                                        }
                                                        else -> {
                                                            var v203: String = ""
                                                            var v204: US48 = US48_RWhole
                                                            var v205: US49 = US49_Entry
                                                            var v206: String = ""
                                                            var v207: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v43: US32 = (v0 as US24_PPeOps).f0
                                                                    val spiral_tuple7003 = sp_info_of_pe_ops_85(v43)
                                                                    v203 = spiral_tuple7003.v0
                                                                    v204 = spiral_tuple7003.v1
                                                                    v205 = spiral_tuple7003.v2
                                                                    v206 = spiral_tuple7003.v3
                                                                    v207 = spiral_tuple7003.v4
                                                                }
                                                                else -> {
                                                                    var v198: String = ""
                                                                    var v199: US48 = US48_RWhole
                                                                    var v200: US49 = US49_Entry
                                                                    var v201: String = ""
                                                                    var v202: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v49: US33 = (v0 as US24_PPeGuards).f0
                                                                            val spiral_tuple8479 = sp_info_of_pe_guards_86(v49)
                                                                            v198 = spiral_tuple8479.v0
                                                                            v199 = spiral_tuple8479.v1
                                                                            v200 = spiral_tuple8479.v2
                                                                            v201 = spiral_tuple8479.v3
                                                                            v202 = spiral_tuple8479.v4
                                                                        }
                                                                        else -> {
                                                                            var v193: String = ""
                                                                            var v194: US48 = US48_RWhole
                                                                            var v195: US49 = US49_Entry
                                                                            var v196: String = ""
                                                                            var v197: String = ""
                                                                            when (v0.tag) {
                                                                                9 -> {
                                                                                    var v55: US34 = (v0 as US24_PSeams).f0
                                                                                    val spiral_tuple10083 = sp_info_of_seams_87(v55)
                                                                                    v193 = spiral_tuple10083.v0
                                                                                    v194 = spiral_tuple10083.v1
                                                                                    v195 = spiral_tuple10083.v2
                                                                                    v196 = spiral_tuple10083.v3
                                                                                    v197 = spiral_tuple10083.v4
                                                                                }
                                                                                else -> {
                                                                                    var v188: String = ""
                                                                                    var v189: US48 = US48_RWhole
                                                                                    var v190: US49 = US49_Entry
                                                                                    var v191: String = ""
                                                                                    var v192: String = ""
                                                                                    when (v0.tag) {
                                                                                        10 -> {
                                                                                            var v61: US35 = (v0 as US24_PSf).f0
                                                                                            val spiral_tuple11815 = sp_info_of_sf_88(v61)
                                                                                            v188 = spiral_tuple11815.v0
                                                                                            v189 = spiral_tuple11815.v1
                                                                                            v190 = spiral_tuple11815.v2
                                                                                            v191 = spiral_tuple11815.v3
                                                                                            v192 = spiral_tuple11815.v4
                                                                                        }
                                                                                        else -> {
                                                                                            var v183: String = ""
                                                                                            var v184: US48 = US48_RWhole
                                                                                            var v185: US49 = US49_Entry
                                                                                            var v186: String = ""
                                                                                            var v187: String = ""
                                                                                            when (v0.tag) {
                                                                                                11 -> {
                                                                                                    var v67: US36 = (v0 as US24_PHsSeams).f0
                                                                                                    val spiral_tuple13677 = sp_info_of_hs_seams_89(v67)
                                                                                                    v183 = spiral_tuple13677.v0
                                                                                                    v184 = spiral_tuple13677.v1
                                                                                                    v185 = spiral_tuple13677.v2
                                                                                                    v186 = spiral_tuple13677.v3
                                                                                                    v187 = spiral_tuple13677.v4
                                                                                                }
                                                                                                else -> {
                                                                                                    var v178: String = ""
                                                                                                    var v179: US48 = US48_RWhole
                                                                                                    var v180: US49 = US49_Entry
                                                                                                    var v181: String = ""
                                                                                                    var v182: String = ""
                                                                                                    when (v0.tag) {
                                                                                                        12 -> {
                                                                                                            var v73: US37 = (v0 as US24_PHsEngine).f0
                                                                                                            val spiral_tuple15674 = sp_info_of_hs_engine_90(v73)
                                                                                                            v178 = spiral_tuple15674.v0
                                                                                                            v179 = spiral_tuple15674.v1
                                                                                                            v180 = spiral_tuple15674.v2
                                                                                                            v181 = spiral_tuple15674.v3
                                                                                                            v182 = spiral_tuple15674.v4
                                                                                                        }
                                                                                                        else -> {
                                                                                                            var v173: String = ""
                                                                                                            var v174: US48 = US48_RWhole
                                                                                                            var v175: US49 = US49_Entry
                                                                                                            var v176: String = ""
                                                                                                            var v177: String = ""
                                                                                                            when (v0.tag) {
                                                                                                                13 -> {
                                                                                                                    var v79: US38 = (v0 as US24_PHsTable).f0
                                                                                                                    val spiral_tuple17799 = sp_info_of_hs_table_91(v79)
                                                                                                                    v173 = spiral_tuple17799.v0
                                                                                                                    v174 = spiral_tuple17799.v1
                                                                                                                    v175 = spiral_tuple17799.v2
                                                                                                                    v176 = spiral_tuple17799.v3
                                                                                                                    v177 = spiral_tuple17799.v4
                                                                                                                }
                                                                                                                else -> {
                                                                                                                    var v168: String = ""
                                                                                                                    var v169: US48 = US48_RWhole
                                                                                                                    var v170: US49 = US49_Entry
                                                                                                                    var v171: String = ""
                                                                                                                    var v172: String = ""
                                                                                                                    when (v0.tag) {
                                                                                                                        14 -> {
                                                                                                                            var v85: US39 = (v0 as US24_PHsGraph).f0
                                                                                                                            val spiral_tuple20051 = sp_info_of_hs_graph_92(v85)
                                                                                                                            v168 = spiral_tuple20051.v0
                                                                                                                            v169 = spiral_tuple20051.v1
                                                                                                                            v170 = spiral_tuple20051.v2
                                                                                                                            v171 = spiral_tuple20051.v3
                                                                                                                            v172 = spiral_tuple20051.v4
                                                                                                                        }
                                                                                                                        else -> {
                                                                                                                            var v163: String = ""
                                                                                                                            var v164: US48 = US48_RWhole
                                                                                                                            var v165: US49 = US49_Entry
                                                                                                                            var v166: String = ""
                                                                                                                            var v167: String = ""
                                                                                                                            when (v0.tag) {
                                                                                                                                15 -> {
                                                                                                                                    var v91: US40 = (v0 as US24_PHsWork).f0
                                                                                                                                    val spiral_tuple22430 = sp_info_of_hs_work_93(v91)
                                                                                                                                    v163 = spiral_tuple22430.v0
                                                                                                                                    v164 = spiral_tuple22430.v1
                                                                                                                                    v165 = spiral_tuple22430.v2
                                                                                                                                    v166 = spiral_tuple22430.v3
                                                                                                                                    v167 = spiral_tuple22430.v4
                                                                                                                                }
                                                                                                                                else -> {
                                                                                                                                    var v158: String = ""
                                                                                                                                    var v159: US48 = US48_RWhole
                                                                                                                                    var v160: US49 = US49_Entry
                                                                                                                                    var v161: String = ""
                                                                                                                                    var v162: String = ""
                                                                                                                                    when (v0.tag) {
                                                                                                                                        16 -> {
                                                                                                                                            var v97: US41 = (v0 as US24_PHsCtx).f0
                                                                                                                                            val spiral_tuple24935 = sp_info_of_hs_ctx_94(v97)
                                                                                                                                            v158 = spiral_tuple24935.v0
                                                                                                                                            v159 = spiral_tuple24935.v1
                                                                                                                                            v160 = spiral_tuple24935.v2
                                                                                                                                            v161 = spiral_tuple24935.v3
                                                                                                                                            v162 = spiral_tuple24935.v4
                                                                                                                                        }
                                                                                                                                        else -> {
                                                                                                                                            var v153: String = ""
                                                                                                                                            var v154: US48 = US48_RWhole
                                                                                                                                            var v155: US49 = US49_Entry
                                                                                                                                            var v156: String = ""
                                                                                                                                            var v157: String = ""
                                                                                                                                            when (v0.tag) {
                                                                                                                                                17 -> {
                                                                                                                                                    var v103: US42 = (v0 as US24_PDiverge).f0
                                                                                                                                                    val spiral_tuple27570 = sp_info_of_diverge_95(v103)
                                                                                                                                                    v153 = spiral_tuple27570.v0
                                                                                                                                                    v154 = spiral_tuple27570.v1
                                                                                                                                                    v155 = spiral_tuple27570.v2
                                                                                                                                                    v156 = spiral_tuple27570.v3
                                                                                                                                                    v157 = spiral_tuple27570.v4
                                                                                                                                                }
                                                                                                                                                else -> {
                                                                                                                                                    var v148: String = ""
                                                                                                                                                    var v149: US48 = US48_RWhole
                                                                                                                                                    var v150: US49 = US49_Entry
                                                                                                                                                    var v151: String = ""
                                                                                                                                                    var v152: String = ""
                                                                                                                                                    when (v0.tag) {
                                                                                                                                                        18 -> {
                                                                                                                                                            var v109: US43 = (v0 as US24_PCodegen).f0
                                                                                                                                                            val spiral_tuple30335 = sp_info_of_codegen_96(v109)
                                                                                                                                                            v148 = spiral_tuple30335.v0
                                                                                                                                                            v149 = spiral_tuple30335.v1
                                                                                                                                                            v150 = spiral_tuple30335.v2
                                                                                                                                                            v151 = spiral_tuple30335.v3
                                                                                                                                                            v152 = spiral_tuple30335.v4
                                                                                                                                                        }
                                                                                                                                                        else -> {
                                                                                                                                                            var v143: String = ""
                                                                                                                                                            var v144: US48 = US48_RWhole
                                                                                                                                                            var v145: US49 = US49_Entry
                                                                                                                                                            var v146: String = ""
                                                                                                                                                            var v147: String = ""
                                                                                                                                                            when (v0.tag) {
                                                                                                                                                                19 -> {
                                                                                                                                                                    var v115: US44 = (v0 as US24_PBackends).f0
                                                                                                                                                                    val spiral_tuple33229 = sp_info_of_backends_97(v115)
                                                                                                                                                                    v143 = spiral_tuple33229.v0
                                                                                                                                                                    v144 = spiral_tuple33229.v1
                                                                                                                                                                    v145 = spiral_tuple33229.v2
                                                                                                                                                                    v146 = spiral_tuple33229.v3
                                                                                                                                                                    v147 = spiral_tuple33229.v4
                                                                                                                                                                }
                                                                                                                                                                else -> {
                                                                                                                                                                    var v138: String = ""
                                                                                                                                                                    var v139: US48 = US48_RWhole
                                                                                                                                                                    var v140: US49 = US49_Entry
                                                                                                                                                                    var v141: String = ""
                                                                                                                                                                    var v142: String = ""
                                                                                                                                                                    when (v0.tag) {
                                                                                                                                                                        20 -> {
                                                                                                                                                                            var v121: US45 = (v0 as US24_PServer).f0
                                                                                                                                                                            val spiral_tuple36250 = sp_info_of_server_98(v121)
                                                                                                                                                                            v138 = spiral_tuple36250.v0
                                                                                                                                                                            v139 = spiral_tuple36250.v1
                                                                                                                                                                            v140 = spiral_tuple36250.v2
                                                                                                                                                                            v141 = spiral_tuple36250.v3
                                                                                                                                                                            v142 = spiral_tuple36250.v4
                                                                                                                                                                        }
                                                                                                                                                                        else -> {
                                                                                                                                                                            var v133: String = ""
                                                                                                                                                                            var v134: US48 = US48_RWhole
                                                                                                                                                                            var v135: US49 = US49_Entry
                                                                                                                                                                            var v136: String = ""
                                                                                                                                                                            var v137: String = ""
                                                                                                                                                                            when (v0.tag) {
                                                                                                                                                                                21 -> {
                                                                                                                                                                                    var v127: US46 = (v0 as US24_PEnv).f0
                                                                                                                                                                                    val spiral_tuple39394 = sp_info_of_env_99(v127)
                                                                                                                                                                                    v133 = spiral_tuple39394.v0
                                                                                                                                                                                    v134 = spiral_tuple39394.v1
                                                                                                                                                                                    v135 = spiral_tuple39394.v2
                                                                                                                                                                                    v136 = spiral_tuple39394.v3
                                                                                                                                                                                    v137 = spiral_tuple39394.v4
                                                                                                                                                                                }
                                                                                                                                                                                else -> throw RuntimeException()
                                                                                                                                                                            }
                                                                                                                                                                            v138 = v133
                                                                                                                                                                            v139 = v134
                                                                                                                                                                            v140 = v135
                                                                                                                                                                            v141 = v136
                                                                                                                                                                            v142 = v137
                                                                                                                                                                        }
                                                                                                                                                                    }
                                                                                                                                                                    v143 = v138
                                                                                                                                                                    v144 = v139
                                                                                                                                                                    v145 = v140
                                                                                                                                                                    v146 = v141
                                                                                                                                                                    v147 = v142
                                                                                                                                                                }
                                                                                                                                                            }
                                                                                                                                                            v148 = v143
                                                                                                                                                            v149 = v144
                                                                                                                                                            v150 = v145
                                                                                                                                                            v151 = v146
                                                                                                                                                            v152 = v147
                                                                                                                                                        }
                                                                                                                                                    }
                                                                                                                                                    v153 = v148
                                                                                                                                                    v154 = v149
                                                                                                                                                    v155 = v150
                                                                                                                                                    v156 = v151
                                                                                                                                                    v157 = v152
                                                                                                                                                }
                                                                                                                                            }
                                                                                                                                            v158 = v153
                                                                                                                                            v159 = v154
                                                                                                                                            v160 = v155
                                                                                                                                            v161 = v156
                                                                                                                                            v162 = v157
                                                                                                                                        }
                                                                                                                                    }
                                                                                                                                    v163 = v158
                                                                                                                                    v164 = v159
                                                                                                                                    v165 = v160
                                                                                                                                    v166 = v161
                                                                                                                                    v167 = v162
                                                                                                                                }
                                                                                                                            }
                                                                                                                            v168 = v163
                                                                                                                            v169 = v164
                                                                                                                            v170 = v165
                                                                                                                            v171 = v166
                                                                                                                            v172 = v167
                                                                                                                        }
                                                                                                                    }
                                                                                                                    v173 = v168
                                                                                                                    v174 = v169
                                                                                                                    v175 = v170
                                                                                                                    v176 = v171
                                                                                                                    v177 = v172
                                                                                                                }
                                                                                                            }
                                                                                                            v178 = v173
                                                                                                            v179 = v174
                                                                                                            v180 = v175
                                                                                                            v181 = v176
                                                                                                            v182 = v177
                                                                                                        }
                                                                                                    }
                                                                                                    v183 = v178
                                                                                                    v184 = v179
                                                                                                    v185 = v180
                                                                                                    v186 = v181
                                                                                                    v187 = v182
                                                                                                }
                                                                                            }
                                                                                            v188 = v183
                                                                                            v189 = v184
                                                                                            v190 = v185
                                                                                            v191 = v186
                                                                                            v192 = v187
                                                                                        }
                                                                                    }
                                                                                    v193 = v188
                                                                                    v194 = v189
                                                                                    v195 = v190
                                                                                    v196 = v191
                                                                                    v197 = v192
                                                                                }
                                                                            }
                                                                            v198 = v193
                                                                            v199 = v194
                                                                            v200 = v195
                                                                            v201 = v196
                                                                            v202 = v197
                                                                        }
                                                                    }
                                                                    v203 = v198
                                                                    v204 = v199
                                                                    v205 = v200
                                                                    v206 = v201
                                                                    v207 = v202
                                                                }
                                                            }
                                                            v208 = v203
                                                            v209 = v204
                                                            v210 = v205
                                                            v211 = v206
                                                            v212 = v207
                                                        }
                                                    }
                                                    v213 = v208
                                                    v214 = v209
                                                    v215 = v210
                                                    v216 = v211
                                                    v217 = v212
                                                }
                                            }
                                            v218 = v213
                                            v219 = v214
                                            v220 = v215
                                            v221 = v216
                                            v222 = v217
                                        }
                                    }
                                    v223 = v218
                                    v224 = v219
                                    v225 = v220
                                    v226 = v221
                                    v227 = v222
                                }
                            }
                            v228 = v223
                            v229 = v224
                            v230 = v225
                            v231 = v226
                            v232 = v227
                        }
                    }
                    v233 = v228
                    v234 = v229
                    v235 = v230
                    v236 = v231
                    v237 = v232
                }
            }
            v238 = v233
            v239 = v234
            v240 = v235
            v241 = v236
            v242 = v237
        }
    }
    return SpiralTuple6(v238, v239, v240, v241, v242)
}
fun sp_link_style_100(p0: US47): SpiralTuple7 {
    var v0: US47 = p0
    var v60: String = ""
    var v61: US13 = US13_Solid
    var v62: Int = 0
    var v63: US14 = US14_Standard
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "#263238"
            var v2: US13 = US13_Solid
            v2 = US13_Solid
            var v3: US14 = US14_Standard
            v3 = US14_Standard
            v60 = v1
            v61 = v2
            v62 = 30
            v63 = v3
        }
        else -> {
            var v56: String = ""
            var v57: US13 = US13_Solid
            var v58: Int = 0
            var v59: US14 = US14_Standard
            when (v0.tag) {
                1 -> {
                    var v4: String = ""
                    v4 = "#1565C0"
                    var v5: US13 = US13_Solid
                    v5 = US13_Solid
                    var v6: US14 = US14_Standard
                    v6 = US14_Standard
                    v56 = v4
                    v57 = v5
                    v58 = 15
                    v59 = v6
                }
                else -> {
                    var v52: String = ""
                    var v53: US13 = US13_Solid
                    var v54: Int = 0
                    var v55: US14 = US14_Standard
                    when (v0.tag) {
                        2 -> {
                            var v7: String = ""
                            v7 = "#9E9E9E"
                            var v8: US13 = US13_Solid
                            v8 = US13_Dashed
                            var v9: US14 = US14_Standard
                            v9 = US14_Standard
                            v52 = v7
                            v53 = v8
                            v54 = 10
                            v55 = v9
                        }
                        else -> {
                            var v48: String = ""
                            var v49: US13 = US13_Solid
                            var v50: Int = 0
                            var v51: US14 = US14_Standard
                            when (v0.tag) {
                                3 -> {
                                    var v10: String = ""
                                    v10 = "#00897B"
                                    var v11: US13 = US13_Solid
                                    v11 = US13_Dotted
                                    var v12: US14 = US14_Standard
                                    v12 = US14_Standard
                                    v48 = v10
                                    v49 = v11
                                    v50 = 15
                                    v51 = v12
                                }
                                else -> {
                                    var v44: String = ""
                                    var v45: US13 = US13_Solid
                                    var v46: Int = 0
                                    var v47: US14 = US14_Standard
                                    when (v0.tag) {
                                        4 -> {
                                            var v13: String = ""
                                            v13 = "#2E7D32"
                                            var v14: US13 = US13_Solid
                                            v14 = US13_Solid
                                            var v15: US14 = US14_Standard
                                            v15 = US14_WhiteDelta
                                            v44 = v13
                                            v45 = v14
                                            v46 = 30
                                            v47 = v15
                                        }
                                        else -> {
                                            var v40: String = ""
                                            var v41: US13 = US13_Solid
                                            var v42: Int = 0
                                            var v43: US14 = US14_Standard
                                            when (v0.tag) {
                                                5 -> {
                                                    var v16: String = ""
                                                    v16 = "#E65100"
                                                    var v17: US13 = US13_Solid
                                                    v17 = US13_Solid
                                                    var v18: US14 = US14_Standard
                                                    v18 = US14_WhiteDelta
                                                    v40 = v16
                                                    v41 = v17
                                                    v42 = 30
                                                    v43 = v18
                                                }
                                                else -> {
                                                    var v36: String = ""
                                                    var v37: US13 = US13_Solid
                                                    var v38: Int = 0
                                                    var v39: US14 = US14_Standard
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v19: String = ""
                                                            v19 = "#BF360C"
                                                            var v20: US13 = US13_Solid
                                                            v20 = US13_DashDotted
                                                            var v21: US14 = US14_Standard
                                                            v21 = US14_Standard
                                                            v36 = v19
                                                            v37 = v20
                                                            v38 = 18
                                                            v39 = v21
                                                        }
                                                        else -> {
                                                            var v32: String = ""
                                                            var v33: US13 = US13_Solid
                                                            var v34: Int = 0
                                                            var v35: US14 = US14_Standard
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v22: String = ""
                                                                    v22 = "#AD1457"
                                                                    var v23: US13 = US13_Solid
                                                                    v23 = US13_Dashed
                                                                    var v24: US14 = US14_Standard
                                                                    v24 = US14_WhiteDiamond
                                                                    v32 = v22
                                                                    v33 = v23
                                                                    v34 = 25
                                                                    v35 = v24
                                                                }
                                                                else -> {
                                                                    var v28: String = ""
                                                                    var v29: US13 = US13_Solid
                                                                    var v30: Int = 0
                                                                    var v31: US14 = US14_Standard
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v25: String = ""
                                                                            v25 = "#F9A825"
                                                                            var v26: US13 = US13_Solid
                                                                            v26 = US13_Dotted
                                                                            var v27: US14 = US14_Standard
                                                                            v27 = US14_Bare
                                                                            v28 = v25
                                                                            v29 = v26
                                                                            v30 = 15
                                                                            v31 = v27
                                                                        }
                                                                        else -> throw RuntimeException()
                                                                    }
                                                                    v32 = v28
                                                                    v33 = v29
                                                                    v34 = v30
                                                                    v35 = v31
                                                                }
                                                            }
                                                            v36 = v32
                                                            v37 = v33
                                                            v38 = v34
                                                            v39 = v35
                                                        }
                                                    }
                                                    v40 = v36
                                                    v41 = v37
                                                    v42 = v38
                                                    v43 = v39
                                                }
                                            }
                                            v44 = v40
                                            v45 = v41
                                            v46 = v42
                                            v47 = v43
                                        }
                                    }
                                    v48 = v44
                                    v49 = v45
                                    v50 = v46
                                    v51 = v47
                                }
                            }
                            v52 = v48
                            v53 = v49
                            v54 = v50
                            v55 = v51
                        }
                    }
                    v56 = v52
                    v57 = v53
                    v58 = v54
                    v59 = v55
                }
            }
            v60 = v56
            v61 = v57
            v62 = v58
            v63 = v59
        }
    }
    return SpiralTuple7(v60, v61, v62, v63)
}
fun sp_link_of_76(p0: US24, p1: US24, p2: US47, p3: String): SpiralTuple5 {
    var v0: US24 = p0
    var v1: US24 = p1
    var v2: US47 = p2
    var v3: String = p3
    var v4: String = ""
    var v5: US48 = US48_RWhole
    var v6: US49 = US49_Entry
    var v7: String = ""
    var v8: String = ""
    val spiral_tuple299 = sp_info_of_part_77(v0)
    v4 = spiral_tuple299.v0
    v5 = spiral_tuple299.v1
    v6 = spiral_tuple299.v2
    v7 = spiral_tuple299.v3
    v8 = spiral_tuple299.v4
    var v9: String = ""
    var v10: US48 = US48_RWhole
    var v11: US49 = US49_Entry
    var v12: String = ""
    var v13: String = ""
    val spiral_tuple625 = sp_info_of_part_77(v1)
    v9 = spiral_tuple625.v0
    v10 = spiral_tuple625.v1
    v11 = spiral_tuple625.v2
    v12 = spiral_tuple625.v3
    v13 = spiral_tuple625.v4
    var v14: String = ""
    var v15: US13 = US13_Solid
    var v16: Int = 0
    var v17: US14 = US14_Standard
    val spiral_tuple929 = sp_link_style_100(v2)
    v14 = spiral_tuple929.v0
    v15 = spiral_tuple929.v1
    v16 = spiral_tuple929.v2
    v17 = spiral_tuple929.v3
    return SpiralTuple5(v4, v9, v3, v14, v15, v16, v17)
}
fun sp_method75(p0: UH23, p1: UH17): UH17 {
    var v0: UH23 = p0
    var v1: UH17 = p1
    when (v0.tag) {
        1 -> {
            var v2: US24 = (v0 as UH23_Cons).f0
            var v3: US24 = (v0 as UH23_Cons).f1
            var v4: US47 = (v0 as UH23_Cons).f2
            var v5: String = (v0 as UH23_Cons).f3
            var v6: UH23 = (v0 as UH23_Cons).f4
            var v7: UH17 = UH17_Nil
            v7 = sp_method75(v6, v1)
            var v8: String = ""
            var v9: String = ""
            var v10: String = ""
            var v11: String = ""
            var v12: US13 = US13_Solid
            var v13: Int = 0
            var v14: US14 = US14_Standard
            val spiral_tuple678 = sp_link_of_76(v2, v3, v4, v5)
            v8 = spiral_tuple678.v0
            v9 = spiral_tuple678.v1
            v10 = spiral_tuple678.v2
            v11 = spiral_tuple678.v3
            v12 = spiral_tuple678.v4
            v13 = spiral_tuple678.v5
            v14 = spiral_tuple678.v6
            return UH17_Cons(v8, v9, v10, v11, v12, v13, v14, v7)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_parts_101(): UH50 {
    var v0: US25 = US25_NHashcons
    v0 = US25_NHashcons
    var v1: US24 = US24_PInfra(US25_NHashcons)
    v1 = US24_PInfra(v0)
    var v2: US25 = US25_NHashcons
    v2 = US25_NConsed
    var v3: US24 = US24_PInfra(US25_NHashcons)
    v3 = US24_PInfra(v2)
    var v4: US25 = US25_NHashcons
    v4 = US25_NStriped
    var v5: US24 = US24_PInfra(US25_NHashcons)
    v5 = US24_PInfra(v4)
    var v6: US25 = US25_NHashcons
    v6 = US25_NMemoize
    var v7: US24 = US24_PInfra(US25_NHashcons)
    v7 = US24_PInfra(v6)
    var v8: US25 = US25_NHashcons
    v8 = US25_NHopacext
    var v9: US24 = US24_PInfra(US25_NHashcons)
    v9 = US24_PInfra(v8)
    var v10: US25 = US25_NHashcons
    v10 = US25_NPvec
    var v11: US24 = US24_PInfra(US25_NHashcons)
    v11 = US24_PInfra(v10)
    var v12: US25 = US25_NHashcons
    v12 = US25_NParsec
    var v13: US24 = US24_PInfra(US25_NHashcons)
    v13 = US24_PInfra(v12)
    var v14: US25 = US25_NHashcons
    v14 = US25_NIdadapter
    var v15: US24 = US24_PInfra(US25_NHashcons)
    v15 = US24_PInfra(v14)
    var v16: US25 = US25_NHashcons
    v16 = US25_NHopaclib
    var v17: US24 = US24_PInfra(US25_NHashcons)
    v17 = US24_PInfra(v16)
    var v18: US26 = US26_NStartup
    v18 = US26_NStartup
    var v19: US24 = US24_PInfra(US25_NHashcons)
    v19 = US24_PFront(v18)
    var v20: US26 = US26_NStartup
    v20 = US26_NTokenize
    var v21: US24 = US24_PInfra(US25_NHashcons)
    v21 = US24_PFront(v20)
    var v22: US26 = US26_NStartup
    v22 = US26_NBlocksplit
    var v23: US24 = US24_PInfra(US25_NHashcons)
    v23 = US24_PFront(v22)
    var v24: US26 = US26_NStartup
    v24 = US26_NBlockparse
    var v25: US24 = US24_PInfra(US25_NHashcons)
    v25 = US24_PFront(v24)
    var v26: US26 = US26_NStartup
    v26 = US26_NBundle
    var v27: US24 = US24_PInfra(US25_NHashcons)
    v27 = US24_PFront(v26)
    var v28: US26 = US26_NStartup
    v28 = US26_NFormatter
    var v29: US24 = US24_PInfra(US25_NHashcons)
    v29 = US24_PFront(v28)
    var v30: US27 = US27_NSpiproj
    v30 = US27_NSpiproj
    var v31: US24 = US24_PInfra(US25_NHashcons)
    v31 = US24_PProj(v30)
    var v32: US27 = US27_NSpiproj
    v32 = US27_NWdiff
    var v33: US24 = US24_PInfra(US25_NHashcons)
    v33 = US24_PProj(v32)
    var v34: US27 = US27_NSpiproj
    v34 = US27_NWdiffprep
    var v35: US24 = US24_PInfra(US25_NHashcons)
    v35 = US24_PProj(v34)
    var v36: US27 = US27_NSpiproj
    v36 = US27_NGraph
    var v37: US24 = US24_PInfra(US25_NHashcons)
    v37 = US24_PProj(v36)
    var v38: US27 = US27_NSpiproj
    v38 = US27_NLoader
    var v39: US24 = US24_PInfra(US25_NHashcons)
    v39 = US24_PProj(v38)
    var v40: US28 = US28_NInfer
    v40 = US28_NInfer
    var v41: US24 = US24_PInfra(US25_NHashcons)
    v41 = US24_PTyping(v40)
    var v42: US28 = US28_NInfer
    v42 = US28_NInferadapter
    var v43: US24 = US24_PInfra(US25_NHashcons)
    v43 = US24_PTyping(v42)
    var v44: US28 = US28_NInfer
    v44 = US28_NInlinedepth
    var v45: US24 = US24_PInfra(US25_NHashcons)
    v45 = US24_PTyping(v44)
    var v46: US28 = US28_NInfer
    v46 = US28_NTypeerr
    var v47: US24 = US24_PInfra(US25_NHashcons)
    v47 = US24_PTyping(v46)
    var v48: US29 = US29_NPrepass
    v48 = US29_NPrepass
    var v49: US24 = US24_PInfra(US25_NHashcons)
    v49 = US24_PPrep(v48)
    var v50: US29 = US29_NPrepass
    v50 = US29_NPrepassadapter
    var v51: US24 = US24_PInfra(US25_NHashcons)
    v51 = US24_PPrep(v50)
    var v52: US29 = US29_NPrepass
    v52 = US29_NPrepenv
    var v53: US24 = US24_PInfra(US25_NHashcons)
    v53 = US24_PPrep(v52)
    var v54: US30 = US30_NPeval
    v54 = US30_NPeval
    var v55: US24 = US24_PInfra(US25_NHashcons)
    v55 = US24_PPe(v54)
    var v56: US30 = US30_NPeval
    v56 = US30_NTopenv
    var v57: US24 = US24_PInfra(US25_NHashcons)
    v57 = US24_PPe(v56)
    var v58: US30 = US30_NPeval
    v58 = US30_NLangenv
    var v59: US24 = US24_PInfra(US25_NHashcons)
    v59 = US24_PPe(v58)
    var v60: US30 = US30_NPeval
    v60 = US30_NResult
    var v61: US24 = US24_PInfra(US25_NHashcons)
    v61 = US24_PPe(v60)
    var v62: US30 = US30_NPeval
    v62 = US30_NTermscope
    var v63: US24 = US24_PInfra(US25_NHashcons)
    v63 = US24_PPe(v62)
    var v64: US30 = US30_NPeval
    v64 = US30_NCse
    var v65: US24 = US24_PInfra(US25_NHashcons)
    v65 = US24_PPe(v64)
    var v66: US31 = US31_NJpm
    v66 = US31_NJpm
    var v67: US24 = US24_PInfra(US25_NHashcons)
    v67 = US24_PPeTables(v66)
    var v68: US31 = US31_NJpm
    v68 = US31_NJpc
    var v69: US24 = US24_PInfra(US25_NHashcons)
    v69 = US24_PPeTables(v68)
    var v70: US31 = US31_NJpm
    v70 = US31_NJpt
    var v71: US24 = US24_PInfra(US25_NHashcons)
    v71 = US24_PPeTables(v70)
    var v72: US31 = US31_NJpm
    v72 = US31_NBsv
    var v73: US24 = US24_PInfra(US25_NHashcons)
    v73 = US24_PPeTables(v72)
    var v74: US31 = US31_NJpm
    v74 = US31_NGlobals
    var v75: US24 = US24_PInfra(US25_NHashcons)
    v75 = US24_PPeTables(v74)
    var v76: US32 = US32_NClosure
    v76 = US32_NClosure
    var v77: US24 = US24_PInfra(US25_NHashcons)
    v77 = US24_PPeOps(v76)
    var v78: US32 = US32_NClosure
    v78 = US32_NTjp
    var v79: US24 = US24_PInfra(US25_NHashcons)
    v79 = US24_PPeOps(v78)
    var v80: US32 = US32_NClosure
    v80 = US32_NMjp
    var v81: US24 = US24_PInfra(US25_NHashcons)
    v81 = US24_PPeOps(v80)
    var v82: US32 = US32_NClosure
    v82 = US32_NBswitch
    var v83: US24 = US24_PInfra(US25_NHashcons)
    v83 = US24_PPeOps(v82)
    var v84: US32 = US32_NClosure
    v84 = US32_NUninh
    var v85: US24 = US24_PInfra(US25_NHashcons)
    v85 = US24_PPeOps(v84)
    var v86: US32 = US32_NClosure
    v86 = US32_NPrintst
    var v87: US24 = US24_PInfra(US25_NHashcons)
    v87 = US24_PPeOps(v86)
    var v88: US32 = US32_NClosure
    v88 = US32_NMainroot
    var v89: US24 = US24_PInfra(US25_NHashcons)
    v89 = US24_PPeOps(v88)
    var v90: US33 = US33_NRunaway
    v90 = US33_NRunaway
    var v91: US24 = US24_PInfra(US25_NHashcons)
    v91 = US24_PPeGuards(v90)
    var v92: US33 = US33_NRunaway
    v92 = US33_NDynif
    var v93: US24 = US24_PInfra(US25_NHashcons)
    v93 = US24_PPeGuards(v92)
    var v94: US33 = US33_NRunaway
    v94 = US33_NUnionnest
    var v95: US24 = US24_PInfra(US25_NHashcons)
    v95 = US24_PPeGuards(v94)
    var v96: US33 = US33_NRunaway
    v96 = US33_NGindex
    var v97: US24 = US24_PInfra(US25_NHashcons)
    v97 = US24_PPeGuards(v96)
    var v98: US33 = US33_NRunaway
    v98 = US33_NRustgap
    var v99: US24 = US24_PInfra(US25_NHashcons)
    v99 = US24_PPeGuards(v98)
    var v100: US34 = US34_NAdapter
    v100 = US34_NAdapter
    var v101: US24 = US24_PInfra(US25_NHashcons)
    v101 = US24_PSeams(v100)
    var v102: US34 = US34_NAdapter
    v102 = US34_NSSession
    var v103: US24 = US24_PInfra(US25_NHashcons)
    v103 = US24_PSeams(v102)
    var v104: US34 = US34_NAdapter
    v104 = US34_NSClaimty
    var v105: US24 = US24_PInfra(US25_NHashcons)
    v105 = US24_PSeams(v104)
    var v106: US34 = US34_NAdapter
    v106 = US34_NSStart
    var v107: US24 = US24_PInfra(US25_NHashcons)
    v107 = US24_PSeams(v106)
    var v108: US34 = US34_NAdapter
    v108 = US34_NSRestart
    var v109: US24 = US24_PInfra(US25_NHashcons)
    v109 = US24_PSeams(v108)
    var v110: US34 = US34_NAdapter
    v110 = US34_NSRoot
    var v111: US24 = US24_PInfra(US25_NHashcons)
    v111 = US24_PSeams(v110)
    var v112: US34 = US34_NAdapter
    v112 = US34_NSDefer
    var v113: US24 = US24_PInfra(US25_NHashcons)
    v113 = US24_PSeams(v112)
    var v114: US34 = US34_NAdapter
    v114 = US34_NSClaim
    var v115: US24 = US24_PInfra(US25_NHashcons)
    v115 = US24_PSeams(v114)
    var v116: US34 = US34_NAdapter
    v116 = US34_NSAnnounce
    var v117: US24 = US24_PInfra(US25_NHashcons)
    v117 = US24_PSeams(v116)
    var v118: US34 = US34_NAdapter
    v118 = US34_NSFill
    var v119: US24 = US24_PInfra(US25_NHashcons)
    v119 = US24_PSeams(v118)
    var v120: US34 = US34_NAdapter
    v120 = US34_NSFamily
    var v121: US24 = US24_PInfra(US25_NHashcons)
    v121 = US24_PSeams(v120)
    var v122: US34 = US34_NAdapter
    v122 = US34_NSKeyhit
    var v123: US24 = US24_PInfra(US25_NHashcons)
    v123 = US24_PSeams(v122)
    var v124: US34 = US34_NAdapter
    v124 = US34_NSKeybody
    var v125: US24 = US24_PInfra(US25_NHashcons)
    v125 = US24_PSeams(v124)
    var v126: US34 = US34_NAdapter
    v126 = US34_NSPrint
    var v127: US24 = US24_PInfra(US25_NHashcons)
    v127 = US24_PSeams(v126)
    var v128: US34 = US34_NAdapter
    v128 = US34_NSNesting
    var v129: US24 = US24_PInfra(US25_NHashcons)
    v129 = US24_PSeams(v128)
    var v130: US34 = US34_NAdapter
    v130 = US34_NSValidated
    var v131: US24 = US24_PInfra(US25_NHashcons)
    v131 = US24_PSeams(v130)
    var v132: US34 = US34_NAdapter
    v132 = US34_NSRead
    var v133: US24 = US24_PInfra(US25_NHashcons)
    v133 = US24_PSeams(v132)
    var v134: US34 = US34_NAdapter
    v134 = US34_NSActive
    var v135: US24 = US24_PInfra(US25_NHashcons)
    v135 = US24_PSeams(v134)
    var v136: US35 = US35_NSfSession
    v136 = US35_NSfSession
    var v137: US24 = US24_PInfra(US25_NHashcons)
    v137 = US24_PSf(v136)
    var v138: US35 = US35_NSfSession
    v138 = US35_NSfStart
    var v139: US24 = US24_PInfra(US25_NHashcons)
    v139 = US24_PSf(v138)
    var v140: US35 = US35_NSfSession
    v140 = US35_NSfRestart
    var v141: US24 = US24_PInfra(US25_NHashcons)
    v141 = US24_PSf(v140)
    var v142: US35 = US35_NSfSession
    v142 = US35_NSfRoot
    var v143: US24 = US24_PInfra(US25_NHashcons)
    v143 = US24_PSf(v142)
    var v144: US35 = US35_NSfSession
    v144 = US35_NSfDefer
    var v145: US24 = US24_PInfra(US25_NHashcons)
    v145 = US24_PSf(v144)
    var v146: US35 = US35_NSfSession
    v146 = US35_NSfClaim
    var v147: US24 = US24_PInfra(US25_NHashcons)
    v147 = US24_PSf(v146)
    var v148: US35 = US35_NSfSession
    v148 = US35_NSfAnnounce
    var v149: US24 = US24_PInfra(US25_NHashcons)
    v149 = US24_PSf(v148)
    var v150: US35 = US35_NSfSession
    v150 = US35_NSfFill
    var v151: US24 = US24_PInfra(US25_NHashcons)
    v151 = US24_PSf(v150)
    var v152: US35 = US35_NSfSession
    v152 = US35_NSfFamily
    var v153: US24 = US24_PInfra(US25_NHashcons)
    v153 = US24_PSf(v152)
    var v154: US35 = US35_NSfSession
    v154 = US35_NSfKeyhit
    var v155: US24 = US24_PInfra(US25_NHashcons)
    v155 = US24_PSf(v154)
    var v156: US35 = US35_NSfSession
    v156 = US35_NSfKeybody
    var v157: US24 = US24_PInfra(US25_NHashcons)
    v157 = US24_PSf(v156)
    var v158: US35 = US35_NSfSession
    v158 = US35_NSfPrint
    var v159: US24 = US24_PInfra(US25_NHashcons)
    v159 = US24_PSf(v158)
    var v160: US35 = US35_NSfSession
    v160 = US35_NSfNesting
    var v161: US24 = US24_PInfra(US25_NHashcons)
    v161 = US24_PSf(v160)
    var v162: US35 = US35_NSfSession
    v162 = US35_NSfValidated
    var v163: US24 = US24_PInfra(US25_NHashcons)
    v163 = US24_PSf(v162)
    var v164: US35 = US35_NSfSession
    v164 = US35_NSfRead
    var v165: US24 = US24_PInfra(US25_NHashcons)
    v165 = US24_PSf(v164)
    var v166: US35 = US35_NSfSession
    v166 = US35_NSfActive
    var v167: US24 = US24_PInfra(US25_NHashcons)
    v167 = US24_PSf(v166)
    var v168: US36 = US36_NHsPolicy
    v168 = US36_NHsPolicy
    var v169: US24 = US24_PInfra(US25_NHashcons)
    v169 = US24_PHsSeams(v168)
    var v170: US36 = US36_NHsPolicy
    v170 = US36_NHsSession
    var v171: US24 = US24_PInfra(US25_NHashcons)
    v171 = US24_PHsSeams(v170)
    var v172: US36 = US36_NHsPolicy
    v172 = US36_NHsStart
    var v173: US24 = US24_PInfra(US25_NHashcons)
    v173 = US24_PHsSeams(v172)
    var v174: US36 = US36_NHsPolicy
    v174 = US36_NHsRestart
    var v175: US24 = US24_PInfra(US25_NHashcons)
    v175 = US24_PHsSeams(v174)
    var v176: US36 = US36_NHsPolicy
    v176 = US36_NHsRoot
    var v177: US24 = US24_PInfra(US25_NHashcons)
    v177 = US24_PHsSeams(v176)
    var v178: US36 = US36_NHsPolicy
    v178 = US36_NHsDefer
    var v179: US24 = US24_PInfra(US25_NHashcons)
    v179 = US24_PHsSeams(v178)
    var v180: US36 = US36_NHsPolicy
    v180 = US36_NHsBody
    var v181: US24 = US24_PInfra(US25_NHashcons)
    v181 = US24_PHsSeams(v180)
    var v182: US36 = US36_NHsPolicy
    v182 = US36_NHsDrain
    var v183: US24 = US24_PInfra(US25_NHashcons)
    v183 = US24_PHsSeams(v182)
    var v184: US36 = US36_NHsPolicy
    v184 = US36_NHsKeyhit
    var v185: US24 = US24_PInfra(US25_NHashcons)
    v185 = US24_PHsSeams(v184)
    var v186: US36 = US36_NHsPolicy
    v186 = US36_NHsKeybody
    var v187: US24 = US24_PInfra(US25_NHashcons)
    v187 = US24_PHsSeams(v186)
    var v188: US36 = US36_NHsPolicy
    v188 = US36_NHsPrint
    var v189: US24 = US24_PInfra(US25_NHashcons)
    v189 = US24_PHsSeams(v188)
    var v190: US36 = US36_NHsPolicy
    v190 = US36_NHsFinish
    var v191: US24 = US24_PInfra(US25_NHashcons)
    v191 = US24_PHsSeams(v190)
    var v192: US36 = US36_NHsPolicy
    v192 = US36_NHsCensus
    var v193: US24 = US24_PInfra(US25_NHashcons)
    v193 = US24_PHsSeams(v192)
    var v194: US36 = US36_NHsPolicy
    v194 = US36_NHsFallback
    var v195: US24 = US24_PInfra(US25_NHashcons)
    v195 = US24_PHsSeams(v194)
    var v196: US36 = US36_NHsPolicy
    v196 = US36_NHsMarks
    var v197: US24 = US24_PInfra(US25_NHashcons)
    v197 = US24_PHsSeams(v196)
    var v198: US37 = US37_NEEngine
    v198 = US37_NEEngine
    var v199: US24 = US24_PInfra(US25_NHashcons)
    v199 = US24_PHsEngine(v198)
    var v200: US37 = US37_NEEngine
    v200 = US37_NEControl
    var v201: US24 = US24_PInfra(US25_NHashcons)
    v201 = US24_PHsEngine(v200)
    var v202: US37 = US37_NEEngine
    v202 = US37_NEBridge
    var v203: US24 = US24_PInfra(US25_NHashcons)
    v203 = US24_PHsEngine(v202)
    var v204: US37 = US37_NEEngine
    v204 = US37_NEAsk
    var v205: US24 = US24_PInfra(US25_NHashcons)
    v205 = US24_PHsEngine(v204)
    var v206: US37 = US37_NEEngine
    v206 = US37_NESettle
    var v207: US24 = US24_PInfra(US25_NHashcons)
    v207 = US24_PHsEngine(v206)
    var v208: US37 = US37_NEEngine
    v208 = US37_NEAwait
    var v209: US24 = US24_PInfra(US25_NHashcons)
    v209 = US24_PHsEngine(v208)
    var v210: US37 = US37_NEEngine
    v210 = US37_NEAbandon
    var v211: US24 = US24_PInfra(US25_NHashcons)
    v211 = US24_PHsEngine(v210)
    var v212: US37 = US37_NEEngine
    v212 = US37_NEStack
    var v213: US24 = US24_PInfra(US25_NHashcons)
    v213 = US24_PHsEngine(v212)
    var v214: US37 = US37_NEEngine
    v214 = US37_NEThreads
    var v215: US24 = US24_PInfra(US25_NHashcons)
    v215 = US24_PHsEngine(v214)
    var v216: US37 = US37_NEEngine
    v216 = US37_NELoop
    var v217: US24 = US24_PInfra(US25_NHashcons)
    v217 = US24_PHsEngine(v216)
    var v218: US37 = US37_NEEngine
    v218 = US37_NEClaim
    var v219: US24 = US24_PInfra(US25_NHashcons)
    v219 = US24_PHsEngine(v218)
    var v220: US37 = US37_NEEngine
    v220 = US37_NEValidated
    var v221: US24 = US24_PInfra(US25_NHashcons)
    v221 = US24_PHsEngine(v220)
    var v222: US37 = US37_NEEngine
    v222 = US37_NERead
    var v223: US24 = US24_PInfra(US25_NHashcons)
    v223 = US24_PHsEngine(v222)
    var v224: US37 = US37_NEEngine
    v224 = US37_NEFill
    var v225: US24 = US24_PInfra(US25_NHashcons)
    v225 = US24_PHsEngine(v224)
    var v226: US37 = US37_NEEngine
    v226 = US37_NEAnnounce
    var v227: US24 = US24_PInfra(US25_NHashcons)
    v227 = US24_PHsEngine(v226)
    var v228: US37 = US37_NEEngine
    v228 = US37_NEFamily
    var v229: US24 = US24_PInfra(US25_NHashcons)
    v229 = US24_PHsEngine(v228)
    var v230: US37 = US37_NEEngine
    v230 = US37_NESpawn
    var v231: US24 = US24_PInfra(US25_NHashcons)
    v231 = US24_PHsEngine(v230)
    var v232: US37 = US37_NEEngine
    v232 = US37_NEDrain
    var v233: US24 = US24_PInfra(US25_NHashcons)
    v233 = US24_PHsEngine(v232)
    var v234: US37 = US37_NEEngine
    v234 = US37_NECheck
    var v235: US24 = US24_PInfra(US25_NHashcons)
    v235 = US24_PHsEngine(v234)
    var v236: US37 = US37_NEEngine
    v236 = US37_NEClose
    var v237: US24 = US24_PInfra(US25_NHashcons)
    v237 = US24_PHsEngine(v236)
    var v238: US37 = US37_NEEngine
    v238 = US37_NEConflict
    var v239: US24 = US24_PInfra(US25_NHashcons)
    v239 = US24_PHsEngine(v238)
    var v240: US38 = US38_NTTable
    v240 = US38_NTTable
    var v241: US24 = US24_PInfra(US25_NHashcons)
    v241 = US24_PHsTable(v240)
    var v242: US38 = US38_NTTable
    v242 = US38_NTDecide
    var v243: US24 = US24_PInfra(US25_NHashcons)
    v243 = US24_PHsTable(v242)
    var v244: US38 = US38_NTTable
    v244 = US38_NTEntry
    var v245: US24 = US24_PInfra(US25_NHashcons)
    v245 = US24_PHsTable(v244)
    var v246: US38 = US38_NTTable
    v246 = US38_NTState
    var v247: US24 = US24_PInfra(US25_NHashcons)
    v247 = US24_PHsTable(v246)
    var v248: US38 = US38_NTTable
    v248 = US38_NTOps
    var v249: US24 = US24_PInfra(US25_NHashcons)
    v249 = US24_PHsTable(v248)
    var v250: US38 = US38_NTTable
    v250 = US38_NTReplies
    var v251: US24 = US24_PInfra(US25_NHashcons)
    v251 = US24_PHsTable(v250)
    var v252: US38 = US38_NTTable
    v252 = US38_NTPublished
    var v253: US24 = US24_PInfra(US25_NHashcons)
    v253 = US24_PHsTable(v252)
    var v254: US38 = US38_NTTable
    v254 = US38_NTGuard
    var v255: US24 = US24_PInfra(US25_NHashcons)
    v255 = US24_PHsTable(v254)
    var v256: US39 = US39_NGGraph
    v256 = US39_NGGraph
    var v257: US24 = US24_PInfra(US25_NHashcons)
    v257 = US24_PHsGraph(v256)
    var v258: US39 = US39_NGGraph
    v258 = US39_NGReq
    var v259: US24 = US24_PInfra(US25_NHashcons)
    v259 = US24_PHsGraph(v258)
    var v260: US39 = US39_NGGraph
    v260 = US39_NGCh
    var v261: US24 = US24_PInfra(US25_NHashcons)
    v261 = US24_PHsGraph(v260)
    var v262: US40 = US40_NWList
    v262 = US40_NWList
    var v263: US24 = US24_PInfra(US25_NHashcons)
    v263 = US24_PHsWork(v262)
    var v264: US40 = US40_NWList
    v264 = US40_NWReq
    var v265: US24 = US24_PInfra(US25_NHashcons)
    v265 = US24_PHsWork(v264)
    var v266: US40 = US40_NWList
    v266 = US40_NWSettle
    var v267: US24 = US24_PInfra(US25_NHashcons)
    v267 = US24_PHsWork(v266)
    var v268: US40 = US40_NWList
    v268 = US40_NWCh
    var v269: US24 = US24_PInfra(US25_NHashcons)
    v269 = US24_PHsWork(v268)
    var v270: US41 = US41_NCCtx
    v270 = US41_NCCtx
    var v271: US24 = US24_PInfra(US25_NHashcons)
    v271 = US24_PHsCtx(v270)
    var v272: US41 = US41_NCCtx
    v272 = US41_NCTl
    var v273: US24 = US24_PInfra(US25_NHashcons)
    v273 = US24_PHsCtx(v272)
    var v274: US42 = US42_NDOrder
    v274 = US42_NDOrder
    var v275: US24 = US24_PInfra(US25_NHashcons)
    v275 = US24_PDiverge(v274)
    var v276: US42 = US42_NDOrder
    v276 = US42_NDErr
    var v277: US24 = US24_PInfra(US25_NHashcons)
    v277 = US24_PDiverge(v276)
    var v278: US42 = US42_NDOrder
    v278 = US42_NDPrint
    var v279: US24 = US24_PInfra(US25_NHashcons)
    v279 = US24_PDiverge(v278)
    var v280: US42 = US42_NDOrder
    v280 = US42_NDRec
    var v281: US24 = US24_PInfra(US25_NHashcons)
    v281 = US24_PDiverge(v280)
    var v282: US42 = US42_NDOrder
    v282 = US42_NDConf
    var v283: US24 = US24_PInfra(US25_NHashcons)
    v283 = US24_PDiverge(v282)
    var v284: US42 = US42_NDOrder
    v284 = US42_NDNest
    var v285: US24 = US24_PInfra(US25_NHashcons)
    v285 = US24_PDiverge(v284)
    var v286: US42 = US42_NDOrder
    v286 = US42_NDGlobals
    var v287: US24 = US24_PInfra(US25_NHashcons)
    v287 = US24_PDiverge(v286)
    var v288: US42 = US42_NDOrder
    v288 = US42_NDSame
    var v289: US24 = US24_PInfra(US25_NHashcons)
    v289 = US24_PDiverge(v288)
    var v290: US43 = US43_NCgAdapter
    v290 = US43_NCgAdapter
    var v291: US24 = US24_PInfra(US25_NHashcons)
    v291 = US24_PCodegen(v290)
    var v292: US43 = US43_NCgAdapter
    v292 = US43_NCgUtils
    var v293: US24 = US24_PInfra(US25_NHashcons)
    v293 = US24_PCodegen(v292)
    var v294: US43 = US43_NCgAdapter
    v294 = US43_NCgDead
    var v295: US24 = US24_PInfra(US25_NHashcons)
    v295 = US24_PCodegen(v294)
    var v296: US43 = US43_NCgAdapter
    v296 = US43_NCgUnused
    var v297: US24 = US24_PInfra(US25_NHashcons)
    v297 = US24_PCodegen(v296)
    var v298: US43 = US43_NCgAdapter
    v298 = US43_NCgRefc
    var v299: US24 = US24_PInfra(US25_NHashcons)
    v299 = US24_PCodegen(v298)
    var v300: US44 = US44_NBFsharp
    v300 = US44_NBFsharp
    var v301: US24 = US24_PInfra(US25_NHashcons)
    v301 = US24_PBackends(v300)
    var v302: US44 = US44_NBFsharp
    v302 = US44_NBRust
    var v303: US24 = US24_PInfra(US25_NHashcons)
    v303 = US24_PBackends(v302)
    var v304: US44 = US44_NBFsharp
    v304 = US44_NBDelphi
    var v305: US24 = US24_PInfra(US25_NHashcons)
    v305 = US24_PBackends(v304)
    var v306: US44 = US44_NBFsharp
    v306 = US44_NBZig
    var v307: US24 = US24_PInfra(US25_NHashcons)
    v307 = US24_PBackends(v306)
    var v308: US44 = US44_NBFsharp
    v308 = US44_NBLean
    var v309: US24 = US24_PInfra(US25_NHashcons)
    v309 = US24_PBackends(v308)
    var v310: US44 = US44_NBFsharp
    v310 = US44_NBBend
    var v311: US24 = US24_PInfra(US25_NHashcons)
    v311 = US24_PBackends(v310)
    var v312: US44 = US44_NBFsharp
    v312 = US44_NBGleam
    var v313: US24 = US24_PInfra(US25_NHashcons)
    v313 = US24_PBackends(v312)
    var v314: US44 = US44_NBFsharp
    v314 = US44_NBLua
    var v315: US24 = US24_PInfra(US25_NHashcons)
    v315 = US24_PBackends(v314)
    var v316: US44 = US44_NBFsharp
    v316 = US44_NBWasm
    var v317: US24 = US24_PInfra(US25_NHashcons)
    v317 = US24_PBackends(v316)
    var v318: US44 = US44_NBFsharp
    v318 = US44_NBWgsl
    var v319: US24 = US24_PInfra(US25_NHashcons)
    v319 = US24_PBackends(v318)
    var v320: US44 = US44_NBFsharp
    v320 = US44_NBCalyx
    var v321: US24 = US24_PInfra(US25_NHashcons)
    v321 = US24_PBackends(v320)
    var v322: US44 = US44_NBFsharp
    v322 = US44_NBKotlin
    var v323: US24 = US24_PInfra(US25_NHashcons)
    v323 = US24_PBackends(v322)
    var v324: US44 = US44_NBFsharp
    v324 = US44_NBSwift
    var v325: US24 = US24_PInfra(US25_NHashcons)
    v325 = US24_PBackends(v324)
    var v326: US44 = US44_NBFsharp
    v326 = US44_NBQasm
    var v327: US24 = US24_PInfra(US25_NHashcons)
    v327 = US24_PBackends(v326)
    var v328: US44 = US44_NBFsharp
    v328 = US44_NBEbpf
    var v329: US24 = US24_PInfra(US25_NHashcons)
    v329 = US24_PBackends(v328)
    var v330: US44 = US44_NBFsharp
    v330 = US44_NBTs
    var v331: US24 = US24_PInfra(US25_NHashcons)
    v331 = US24_PBackends(v330)
    var v332: US44 = US44_NBFsharp
    v332 = US44_NBC
    var v333: US24 = US24_PInfra(US25_NHashcons)
    v333 = US24_PBackends(v332)
    var v334: US44 = US44_NBFsharp
    v334 = US44_NBCpp
    var v335: US24 = US24_PInfra(US25_NHashcons)
    v335 = US24_PBackends(v334)
    var v336: US44 = US44_NBFsharp
    v336 = US44_NBPy
    var v337: US24 = US24_PInfra(US25_NHashcons)
    v337 = US24_PBackends(v336)
    var v338: US45 = US45_NSvMain
    v338 = US45_NSvMain
    var v339: US24 = US24_PInfra(US25_NHashcons)
    v339 = US24_PServer(v338)
    var v340: US45 = US45_NSvMain
    v340 = US45_NSvHub
    var v341: US24 = US24_PInfra(US25_NHashcons)
    v341 = US24_PServer(v340)
    var v342: US45 = US45_NSvMain
    v342 = US45_NSvNew
    var v343: US24 = US24_PInfra(US25_NHashcons)
    v343 = US24_PServer(v342)
    var v344: US45 = US45_NSvMain
    v344 = US45_NSvSuper
    var v345: US24 = US24_PInfra(US25_NHashcons)
    v345 = US24_PServer(v344)
    var v346: US45 = US45_NSvMain
    v346 = US45_NSvBuild
    var v347: US24 = US24_PInfra(US25_NHashcons)
    v347 = US24_PServer(v346)
    var v348: US45 = US45_NSvMain
    v348 = US45_NSvMany
    var v349: US24 = US24_PInfra(US25_NHashcons)
    v349 = US24_PServer(v348)
    var v350: US45 = US45_NSvMain
    v350 = US45_NSvOut
    var v351: US24 = US24_PInfra(US25_NHashcons)
    v351 = US24_PServer(v350)
    var v352: US45 = US45_NSvMain
    v352 = US45_NSvXcompat
    var v353: US24 = US24_PInfra(US25_NHashcons)
    v353 = US24_PServer(v352)
    var v354: US46 = US46_NEnvWorkers
    v354 = US46_NEnvWorkers
    var v355: US24 = US24_PInfra(US25_NHashcons)
    v355 = US24_PEnv(v354)
    var v356: US46 = US46_NEnvWorkers
    v356 = US46_NEnvPolicy
    var v357: US24 = US24_PInfra(US25_NHashcons)
    v357 = US24_PEnv(v356)
    var v358: US46 = US46_NEnvWorkers
    v358 = US46_NEnvStats
    var v359: US24 = US24_PInfra(US25_NHashcons)
    v359 = US24_PEnv(v358)
    var v360: US46 = US46_NEnvWorkers
    v360 = US46_NEnvCensus
    var v361: US24 = US24_PInfra(US25_NHashcons)
    v361 = US24_PEnv(v360)
    var v362: US46 = US46_NEnvWorkers
    v362 = US46_NEnvForce
    var v363: US24 = US24_PInfra(US25_NHashcons)
    v363 = US24_PEnv(v362)
    var v364: US46 = US46_NEnvWorkers
    v364 = US46_NEnvGap
    var v365: US24 = US24_PInfra(US25_NHashcons)
    v365 = US24_PEnv(v364)
    var v366: US46 = US46_NEnvWorkers
    v366 = US46_NEnvIf
    var v367: US24 = US24_PInfra(US25_NHashcons)
    v367 = US24_PEnv(v366)
    var v368: US46 = US46_NEnvWorkers
    v368 = US46_NEnvUnion
    var v369: US24 = US24_PInfra(US25_NHashcons)
    v369 = US24_PEnv(v368)
    var v370: US46 = US46_NEnvWorkers
    v370 = US46_NEnvDefine
    var v371: US24 = US24_PInfra(US25_NHashcons)
    v371 = US24_PEnv(v370)
    var v372: UH50 = UH50_Nil
    v372 = UH50_Nil
    var v373: UH50 = UH50_Nil
    v373 = UH50_Cons(v371, v372)
    var v374: UH50 = UH50_Nil
    v374 = UH50_Cons(v369, v373)
    var v375: UH50 = UH50_Nil
    v375 = UH50_Cons(v367, v374)
    var v376: UH50 = UH50_Nil
    v376 = UH50_Cons(v365, v375)
    var v377: UH50 = UH50_Nil
    v377 = UH50_Cons(v363, v376)
    var v378: UH50 = UH50_Nil
    v378 = UH50_Cons(v361, v377)
    var v379: UH50 = UH50_Nil
    v379 = UH50_Cons(v359, v378)
    var v380: UH50 = UH50_Nil
    v380 = UH50_Cons(v357, v379)
    var v381: UH50 = UH50_Nil
    v381 = UH50_Cons(v355, v380)
    var v382: UH50 = UH50_Nil
    v382 = UH50_Cons(v353, v381)
    var v383: UH50 = UH50_Nil
    v383 = UH50_Cons(v351, v382)
    var v384: UH50 = UH50_Nil
    v384 = UH50_Cons(v349, v383)
    var v385: UH50 = UH50_Nil
    v385 = UH50_Cons(v347, v384)
    var v386: UH50 = UH50_Nil
    v386 = UH50_Cons(v345, v385)
    var v387: UH50 = UH50_Nil
    v387 = UH50_Cons(v343, v386)
    var v388: UH50 = UH50_Nil
    v388 = UH50_Cons(v341, v387)
    var v389: UH50 = UH50_Nil
    v389 = UH50_Cons(v339, v388)
    var v390: UH50 = UH50_Nil
    v390 = UH50_Cons(v337, v389)
    var v391: UH50 = UH50_Nil
    v391 = UH50_Cons(v335, v390)
    var v392: UH50 = UH50_Nil
    v392 = UH50_Cons(v333, v391)
    var v393: UH50 = UH50_Nil
    v393 = UH50_Cons(v331, v392)
    var v394: UH50 = UH50_Nil
    v394 = UH50_Cons(v329, v393)
    var v395: UH50 = UH50_Nil
    v395 = UH50_Cons(v327, v394)
    var v396: UH50 = UH50_Nil
    v396 = UH50_Cons(v325, v395)
    var v397: UH50 = UH50_Nil
    v397 = UH50_Cons(v323, v396)
    var v398: UH50 = UH50_Nil
    v398 = UH50_Cons(v321, v397)
    var v399: UH50 = UH50_Nil
    v399 = UH50_Cons(v319, v398)
    var v400: UH50 = UH50_Nil
    v400 = UH50_Cons(v317, v399)
    var v401: UH50 = UH50_Nil
    v401 = UH50_Cons(v315, v400)
    var v402: UH50 = UH50_Nil
    v402 = UH50_Cons(v313, v401)
    var v403: UH50 = UH50_Nil
    v403 = UH50_Cons(v311, v402)
    var v404: UH50 = UH50_Nil
    v404 = UH50_Cons(v309, v403)
    var v405: UH50 = UH50_Nil
    v405 = UH50_Cons(v307, v404)
    var v406: UH50 = UH50_Nil
    v406 = UH50_Cons(v305, v405)
    var v407: UH50 = UH50_Nil
    v407 = UH50_Cons(v303, v406)
    var v408: UH50 = UH50_Nil
    v408 = UH50_Cons(v301, v407)
    var v409: UH50 = UH50_Nil
    v409 = UH50_Cons(v299, v408)
    var v410: UH50 = UH50_Nil
    v410 = UH50_Cons(v297, v409)
    var v411: UH50 = UH50_Nil
    v411 = UH50_Cons(v295, v410)
    var v412: UH50 = UH50_Nil
    v412 = UH50_Cons(v293, v411)
    var v413: UH50 = UH50_Nil
    v413 = UH50_Cons(v291, v412)
    var v414: UH50 = UH50_Nil
    v414 = UH50_Cons(v289, v413)
    var v415: UH50 = UH50_Nil
    v415 = UH50_Cons(v287, v414)
    var v416: UH50 = UH50_Nil
    v416 = UH50_Cons(v285, v415)
    var v417: UH50 = UH50_Nil
    v417 = UH50_Cons(v283, v416)
    var v418: UH50 = UH50_Nil
    v418 = UH50_Cons(v281, v417)
    var v419: UH50 = UH50_Nil
    v419 = UH50_Cons(v279, v418)
    var v420: UH50 = UH50_Nil
    v420 = UH50_Cons(v277, v419)
    var v421: UH50 = UH50_Nil
    v421 = UH50_Cons(v275, v420)
    var v422: UH50 = UH50_Nil
    v422 = UH50_Cons(v273, v421)
    var v423: UH50 = UH50_Nil
    v423 = UH50_Cons(v271, v422)
    var v424: UH50 = UH50_Nil
    v424 = UH50_Cons(v269, v423)
    var v425: UH50 = UH50_Nil
    v425 = UH50_Cons(v267, v424)
    var v426: UH50 = UH50_Nil
    v426 = UH50_Cons(v265, v425)
    var v427: UH50 = UH50_Nil
    v427 = UH50_Cons(v263, v426)
    var v428: UH50 = UH50_Nil
    v428 = UH50_Cons(v261, v427)
    var v429: UH50 = UH50_Nil
    v429 = UH50_Cons(v259, v428)
    var v430: UH50 = UH50_Nil
    v430 = UH50_Cons(v257, v429)
    var v431: UH50 = UH50_Nil
    v431 = UH50_Cons(v255, v430)
    var v432: UH50 = UH50_Nil
    v432 = UH50_Cons(v253, v431)
    var v433: UH50 = UH50_Nil
    v433 = UH50_Cons(v251, v432)
    var v434: UH50 = UH50_Nil
    v434 = UH50_Cons(v249, v433)
    var v435: UH50 = UH50_Nil
    v435 = UH50_Cons(v247, v434)
    var v436: UH50 = UH50_Nil
    v436 = UH50_Cons(v245, v435)
    var v437: UH50 = UH50_Nil
    v437 = UH50_Cons(v243, v436)
    var v438: UH50 = UH50_Nil
    v438 = UH50_Cons(v241, v437)
    var v439: UH50 = UH50_Nil
    v439 = UH50_Cons(v239, v438)
    var v440: UH50 = UH50_Nil
    v440 = UH50_Cons(v237, v439)
    var v441: UH50 = UH50_Nil
    v441 = UH50_Cons(v235, v440)
    var v442: UH50 = UH50_Nil
    v442 = UH50_Cons(v233, v441)
    var v443: UH50 = UH50_Nil
    v443 = UH50_Cons(v231, v442)
    var v444: UH50 = UH50_Nil
    v444 = UH50_Cons(v229, v443)
    var v445: UH50 = UH50_Nil
    v445 = UH50_Cons(v227, v444)
    var v446: UH50 = UH50_Nil
    v446 = UH50_Cons(v225, v445)
    var v447: UH50 = UH50_Nil
    v447 = UH50_Cons(v223, v446)
    var v448: UH50 = UH50_Nil
    v448 = UH50_Cons(v221, v447)
    var v449: UH50 = UH50_Nil
    v449 = UH50_Cons(v219, v448)
    var v450: UH50 = UH50_Nil
    v450 = UH50_Cons(v217, v449)
    var v451: UH50 = UH50_Nil
    v451 = UH50_Cons(v215, v450)
    var v452: UH50 = UH50_Nil
    v452 = UH50_Cons(v213, v451)
    var v453: UH50 = UH50_Nil
    v453 = UH50_Cons(v211, v452)
    var v454: UH50 = UH50_Nil
    v454 = UH50_Cons(v209, v453)
    var v455: UH50 = UH50_Nil
    v455 = UH50_Cons(v207, v454)
    var v456: UH50 = UH50_Nil
    v456 = UH50_Cons(v205, v455)
    var v457: UH50 = UH50_Nil
    v457 = UH50_Cons(v203, v456)
    var v458: UH50 = UH50_Nil
    v458 = UH50_Cons(v201, v457)
    var v459: UH50 = UH50_Nil
    v459 = UH50_Cons(v199, v458)
    var v460: UH50 = UH50_Nil
    v460 = UH50_Cons(v197, v459)
    var v461: UH50 = UH50_Nil
    v461 = UH50_Cons(v195, v460)
    var v462: UH50 = UH50_Nil
    v462 = UH50_Cons(v193, v461)
    var v463: UH50 = UH50_Nil
    v463 = UH50_Cons(v191, v462)
    var v464: UH50 = UH50_Nil
    v464 = UH50_Cons(v189, v463)
    var v465: UH50 = UH50_Nil
    v465 = UH50_Cons(v187, v464)
    var v466: UH50 = UH50_Nil
    v466 = UH50_Cons(v185, v465)
    var v467: UH50 = UH50_Nil
    v467 = UH50_Cons(v183, v466)
    var v468: UH50 = UH50_Nil
    v468 = UH50_Cons(v181, v467)
    var v469: UH50 = UH50_Nil
    v469 = UH50_Cons(v179, v468)
    var v470: UH50 = UH50_Nil
    v470 = UH50_Cons(v177, v469)
    var v471: UH50 = UH50_Nil
    v471 = UH50_Cons(v175, v470)
    var v472: UH50 = UH50_Nil
    v472 = UH50_Cons(v173, v471)
    var v473: UH50 = UH50_Nil
    v473 = UH50_Cons(v171, v472)
    var v474: UH50 = UH50_Nil
    v474 = UH50_Cons(v169, v473)
    var v475: UH50 = UH50_Nil
    v475 = UH50_Cons(v167, v474)
    var v476: UH50 = UH50_Nil
    v476 = UH50_Cons(v165, v475)
    var v477: UH50 = UH50_Nil
    v477 = UH50_Cons(v163, v476)
    var v478: UH50 = UH50_Nil
    v478 = UH50_Cons(v161, v477)
    var v479: UH50 = UH50_Nil
    v479 = UH50_Cons(v159, v478)
    var v480: UH50 = UH50_Nil
    v480 = UH50_Cons(v157, v479)
    var v481: UH50 = UH50_Nil
    v481 = UH50_Cons(v155, v480)
    var v482: UH50 = UH50_Nil
    v482 = UH50_Cons(v153, v481)
    var v483: UH50 = UH50_Nil
    v483 = UH50_Cons(v151, v482)
    var v484: UH50 = UH50_Nil
    v484 = UH50_Cons(v149, v483)
    var v485: UH50 = UH50_Nil
    v485 = UH50_Cons(v147, v484)
    var v486: UH50 = UH50_Nil
    v486 = UH50_Cons(v145, v485)
    var v487: UH50 = UH50_Nil
    v487 = UH50_Cons(v143, v486)
    var v488: UH50 = UH50_Nil
    v488 = UH50_Cons(v141, v487)
    var v489: UH50 = UH50_Nil
    v489 = UH50_Cons(v139, v488)
    var v490: UH50 = UH50_Nil
    v490 = UH50_Cons(v137, v489)
    var v491: UH50 = UH50_Nil
    v491 = UH50_Cons(v135, v490)
    var v492: UH50 = UH50_Nil
    v492 = UH50_Cons(v133, v491)
    var v493: UH50 = UH50_Nil
    v493 = UH50_Cons(v131, v492)
    var v494: UH50 = UH50_Nil
    v494 = UH50_Cons(v129, v493)
    var v495: UH50 = UH50_Nil
    v495 = UH50_Cons(v127, v494)
    var v496: UH50 = UH50_Nil
    v496 = UH50_Cons(v125, v495)
    var v497: UH50 = UH50_Nil
    v497 = UH50_Cons(v123, v496)
    var v498: UH50 = UH50_Nil
    v498 = UH50_Cons(v121, v497)
    var v499: UH50 = UH50_Nil
    v499 = UH50_Cons(v119, v498)
    var v500: UH50 = UH50_Nil
    v500 = UH50_Cons(v117, v499)
    var v501: UH50 = UH50_Nil
    v501 = UH50_Cons(v115, v500)
    var v502: UH50 = UH50_Nil
    v502 = UH50_Cons(v113, v501)
    var v503: UH50 = UH50_Nil
    v503 = UH50_Cons(v111, v502)
    var v504: UH50 = UH50_Nil
    v504 = UH50_Cons(v109, v503)
    var v505: UH50 = UH50_Nil
    v505 = UH50_Cons(v107, v504)
    var v506: UH50 = UH50_Nil
    v506 = UH50_Cons(v105, v505)
    var v507: UH50 = UH50_Nil
    v507 = UH50_Cons(v103, v506)
    var v508: UH50 = UH50_Nil
    v508 = UH50_Cons(v101, v507)
    var v509: UH50 = UH50_Nil
    v509 = UH50_Cons(v99, v508)
    var v510: UH50 = UH50_Nil
    v510 = UH50_Cons(v97, v509)
    var v511: UH50 = UH50_Nil
    v511 = UH50_Cons(v95, v510)
    var v512: UH50 = UH50_Nil
    v512 = UH50_Cons(v93, v511)
    var v513: UH50 = UH50_Nil
    v513 = UH50_Cons(v91, v512)
    var v514: UH50 = UH50_Nil
    v514 = UH50_Cons(v89, v513)
    var v515: UH50 = UH50_Nil
    v515 = UH50_Cons(v87, v514)
    var v516: UH50 = UH50_Nil
    v516 = UH50_Cons(v85, v515)
    var v517: UH50 = UH50_Nil
    v517 = UH50_Cons(v83, v516)
    var v518: UH50 = UH50_Nil
    v518 = UH50_Cons(v81, v517)
    var v519: UH50 = UH50_Nil
    v519 = UH50_Cons(v79, v518)
    var v520: UH50 = UH50_Nil
    v520 = UH50_Cons(v77, v519)
    var v521: UH50 = UH50_Nil
    v521 = UH50_Cons(v75, v520)
    var v522: UH50 = UH50_Nil
    v522 = UH50_Cons(v73, v521)
    var v523: UH50 = UH50_Nil
    v523 = UH50_Cons(v71, v522)
    var v524: UH50 = UH50_Nil
    v524 = UH50_Cons(v69, v523)
    var v525: UH50 = UH50_Nil
    v525 = UH50_Cons(v67, v524)
    var v526: UH50 = UH50_Nil
    v526 = UH50_Cons(v65, v525)
    var v527: UH50 = UH50_Nil
    v527 = UH50_Cons(v63, v526)
    var v528: UH50 = UH50_Nil
    v528 = UH50_Cons(v61, v527)
    var v529: UH50 = UH50_Nil
    v529 = UH50_Cons(v59, v528)
    var v530: UH50 = UH50_Nil
    v530 = UH50_Cons(v57, v529)
    var v531: UH50 = UH50_Nil
    v531 = UH50_Cons(v55, v530)
    var v532: UH50 = UH50_Nil
    v532 = UH50_Cons(v53, v531)
    var v533: UH50 = UH50_Nil
    v533 = UH50_Cons(v51, v532)
    var v534: UH50 = UH50_Nil
    v534 = UH50_Cons(v49, v533)
    var v535: UH50 = UH50_Nil
    v535 = UH50_Cons(v47, v534)
    var v536: UH50 = UH50_Nil
    v536 = UH50_Cons(v45, v535)
    var v537: UH50 = UH50_Nil
    v537 = UH50_Cons(v43, v536)
    var v538: UH50 = UH50_Nil
    v538 = UH50_Cons(v41, v537)
    var v539: UH50 = UH50_Nil
    v539 = UH50_Cons(v39, v538)
    var v540: UH50 = UH50_Nil
    v540 = UH50_Cons(v37, v539)
    var v541: UH50 = UH50_Nil
    v541 = UH50_Cons(v35, v540)
    var v542: UH50 = UH50_Nil
    v542 = UH50_Cons(v33, v541)
    var v543: UH50 = UH50_Nil
    v543 = UH50_Cons(v31, v542)
    var v544: UH50 = UH50_Nil
    v544 = UH50_Cons(v29, v543)
    var v545: UH50 = UH50_Nil
    v545 = UH50_Cons(v27, v544)
    var v546: UH50 = UH50_Nil
    v546 = UH50_Cons(v25, v545)
    var v547: UH50 = UH50_Nil
    v547 = UH50_Cons(v23, v546)
    var v548: UH50 = UH50_Nil
    v548 = UH50_Cons(v21, v547)
    var v549: UH50 = UH50_Nil
    v549 = UH50_Cons(v19, v548)
    var v550: UH50 = UH50_Nil
    v550 = UH50_Cons(v17, v549)
    var v551: UH50 = UH50_Nil
    v551 = UH50_Cons(v15, v550)
    var v552: UH50 = UH50_Nil
    v552 = UH50_Cons(v13, v551)
    var v553: UH50 = UH50_Nil
    v553 = UH50_Cons(v11, v552)
    var v554: UH50 = UH50_Nil
    v554 = UH50_Cons(v9, v553)
    var v555: UH50 = UH50_Nil
    v555 = UH50_Cons(v7, v554)
    var v556: UH50 = UH50_Nil
    v556 = UH50_Cons(v5, v555)
    var v557: UH50 = UH50_Nil
    v557 = UH50_Cons(v3, v556)
    return UH50_Cons(v1, v557)
}
fun sp_method102(p0: UH50, p1: Int): Int {
    var v0: UH50 = p0
    var v1: Int = p1
    when (v0.tag) {
        1 -> {
            var v2: US24 = (v0 as UH50_Cons).f0
            var v3: UH50 = (v0 as UH50_Cons).f1
            var v4: Int = 0
            v4 = (v1 + 1)
            return sp_method102(v3, v4)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_kind_style_104(p0: US49): SpiralTuple3 {
    var v0: US49 = p0
    var v149: US12 = US12_Rectangle
    var v150: String = ""
    var v151: String = ""
    var v152: US13 = US13_Solid
    var v153: Int = 0
    var v154: String = ""
    when (v0.tag) {
        0 -> {
            var v1: US12 = US12_Rectangle
            v1 = US12_Octagon
            var v2: String = ""
            v2 = "#37474F"
            var v3: String = ""
            v3 = "#000000"
            var v4: US13 = US13_Solid
            v4 = US13_Solid
            var v5: String = ""
            v5 = "#FFFFFF"
            v149 = v1
            v150 = v2
            v151 = v3
            v152 = v4
            v153 = 20
            v154 = v5
        }
        else -> {
            var v143: US12 = US12_Rectangle
            var v144: String = ""
            var v145: String = ""
            var v146: US13 = US13_Solid
            var v147: Int = 0
            var v148: String = ""
            when (v0.tag) {
                1 -> {
                    var v6: US12 = US12_Rectangle
                    v6 = US12_RoundRectangle
                    var v7: String = ""
                    v7 = "#FAFAFA"
                    var v8: String = ""
                    v8 = "#616161"
                    var v9: US13 = US13_Solid
                    v9 = US13_Solid
                    var v10: String = ""
                    v10 = "#000000"
                    v143 = v6
                    v144 = v7
                    v145 = v8
                    v146 = v9
                    v147 = 10
                    v148 = v10
                }
                else -> {
                    var v137: US12 = US12_Rectangle
                    var v138: String = ""
                    var v139: String = ""
                    var v140: US13 = US13_Solid
                    var v141: Int = 0
                    var v142: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v11: US12 = US12_Rectangle
                            v11 = US12_Rectangle
                            var v12: String = ""
                            v12 = "#FFF6D5"
                            var v13: String = ""
                            v13 = "#8D6E63"
                            var v14: US13 = US13_Solid
                            v14 = US13_Solid
                            var v15: String = ""
                            v15 = "#000000"
                            v137 = v11
                            v138 = v12
                            v139 = v13
                            v140 = v14
                            v141 = 10
                            v142 = v15
                        }
                        else -> {
                            var v131: US12 = US12_Rectangle
                            var v132: String = ""
                            var v133: String = ""
                            var v134: US13 = US13_Solid
                            var v135: Int = 0
                            var v136: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v16: US12 = US12_Rectangle
                                    v16 = US12_Hexagon
                                    var v17: String = ""
                                    v17 = "#EDE7F6"
                                    var v18: String = ""
                                    v18 = "#5E35B1"
                                    var v19: US13 = US13_Solid
                                    v19 = US13_Solid
                                    var v20: String = ""
                                    v20 = "#000000"
                                    v131 = v16
                                    v132 = v17
                                    v133 = v18
                                    v134 = v19
                                    v135 = 15
                                    v136 = v20
                                }
                                else -> {
                                    var v125: US12 = US12_Rectangle
                                    var v126: String = ""
                                    var v127: String = ""
                                    var v128: US13 = US13_Solid
                                    var v129: Int = 0
                                    var v130: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v21: US12 = US12_Rectangle
                                            v21 = US12_RoundRectangle
                                            var v22: String = ""
                                            v22 = "#BBDEFB"
                                            var v23: String = ""
                                            v23 = "#0D47A1"
                                            var v24: US13 = US13_Solid
                                            v24 = US13_Solid
                                            var v25: String = ""
                                            v25 = "#000000"
                                            v125 = v21
                                            v126 = v22
                                            v127 = v23
                                            v128 = v24
                                            v129 = 25
                                            v130 = v25
                                        }
                                        else -> {
                                            var v119: US12 = US12_Rectangle
                                            var v120: String = ""
                                            var v121: String = ""
                                            var v122: US13 = US13_Solid
                                            var v123: Int = 0
                                            var v124: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v26: US12 = US12_Rectangle
                                                    v26 = US12_Hexagon
                                                    var v27: String = ""
                                                    v27 = "#E1BEE7"
                                                    var v28: String = ""
                                                    v28 = "#6A1B9A"
                                                    var v29: US13 = US13_Solid
                                                    v29 = US13_Solid
                                                    var v30: String = ""
                                                    v30 = "#000000"
                                                    v119 = v26
                                                    v120 = v27
                                                    v121 = v28
                                                    v122 = v29
                                                    v123 = 20
                                                    v124 = v30
                                                }
                                                else -> {
                                                    var v113: US12 = US12_Rectangle
                                                    var v114: String = ""
                                                    var v115: String = ""
                                                    var v116: US13 = US13_Solid
                                                    var v117: Int = 0
                                                    var v118: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v31: US12 = US12_Rectangle
                                                            v31 = US12_Octagon
                                                            var v32: String = ""
                                                            v32 = "#B2DFDB"
                                                            var v33: String = ""
                                                            v33 = "#00695C"
                                                            var v34: US13 = US13_Solid
                                                            v34 = US13_Solid
                                                            var v35: String = ""
                                                            v35 = "#000000"
                                                            v113 = v31
                                                            v114 = v32
                                                            v115 = v33
                                                            v116 = v34
                                                            v117 = 15
                                                            v118 = v35
                                                        }
                                                        else -> {
                                                            var v107: US12 = US12_Rectangle
                                                            var v108: String = ""
                                                            var v109: String = ""
                                                            var v110: US13 = US13_Solid
                                                            var v111: Int = 0
                                                            var v112: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v36: US12 = US12_Rectangle
                                                                    v36 = US12_Rectangle
                                                                    var v37: String = ""
                                                                    v37 = "#FFFDE7"
                                                                    var v38: String = ""
                                                                    v38 = "#F9A825"
                                                                    var v39: US13 = US13_Solid
                                                                    v39 = US13_Dashed
                                                                    var v40: String = ""
                                                                    v40 = "#000000"
                                                                    v107 = v36
                                                                    v108 = v37
                                                                    v109 = v38
                                                                    v110 = v39
                                                                    v111 = 20
                                                                    v112 = v40
                                                                }
                                                                else -> {
                                                                    var v101: US12 = US12_Rectangle
                                                                    var v102: String = ""
                                                                    var v103: String = ""
                                                                    var v104: US13 = US13_Solid
                                                                    var v105: Int = 0
                                                                    var v106: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v41: US12 = US12_Rectangle
                                                                            v41 = US12_Parallelogram
                                                                            var v42: String = ""
                                                                            v42 = "#ECEFF1"
                                                                            var v43: String = ""
                                                                            v43 = "#455A64"
                                                                            var v44: US13 = US13_Solid
                                                                            v44 = US13_Solid
                                                                            var v45: String = ""
                                                                            v45 = "#000000"
                                                                            v101 = v41
                                                                            v102 = v42
                                                                            v103 = v43
                                                                            v104 = v44
                                                                            v105 = 10
                                                                            v106 = v45
                                                                        }
                                                                        else -> {
                                                                            var v95: US12 = US12_Rectangle
                                                                            var v96: String = ""
                                                                            var v97: String = ""
                                                                            var v98: US13 = US13_Solid
                                                                            var v99: Int = 0
                                                                            var v100: String = ""
                                                                            when (v0.tag) {
                                                                                9 -> {
                                                                                    var v46: US12 = US12_Rectangle
                                                                                    v46 = US12_Octagon
                                                                                    var v47: String = ""
                                                                                    v47 = "#FFCDD2"
                                                                                    var v48: String = ""
                                                                                    v48 = "#B71C1C"
                                                                                    var v49: US13 = US13_Solid
                                                                                    v49 = US13_Solid
                                                                                    var v50: String = ""
                                                                                    v50 = "#000000"
                                                                                    v95 = v46
                                                                                    v96 = v47
                                                                                    v97 = v48
                                                                                    v98 = v49
                                                                                    v99 = 25
                                                                                    v100 = v50
                                                                                }
                                                                                else -> {
                                                                                    var v89: US12 = US12_Rectangle
                                                                                    var v90: String = ""
                                                                                    var v91: String = ""
                                                                                    var v92: US13 = US13_Solid
                                                                                    var v93: Int = 0
                                                                                    var v94: String = ""
                                                                                    when (v0.tag) {
                                                                                        10 -> {
                                                                                            var v51: US12 = US12_Rectangle
                                                                                            v51 = US12_Rectangle
                                                                                            var v52: String = ""
                                                                                            v52 = "#E3F2FD"
                                                                                            var v53: String = ""
                                                                                            v53 = "#1565C0"
                                                                                            var v54: US13 = US13_Solid
                                                                                            v54 = US13_Solid
                                                                                            var v55: String = ""
                                                                                            v55 = "#000000"
                                                                                            v89 = v51
                                                                                            v90 = v52
                                                                                            v91 = v53
                                                                                            v92 = v54
                                                                                            v93 = 10
                                                                                            v94 = v55
                                                                                        }
                                                                                        else -> {
                                                                                            var v83: US12 = US12_Rectangle
                                                                                            var v84: String = ""
                                                                                            var v85: String = ""
                                                                                            var v86: US13 = US13_Solid
                                                                                            var v87: Int = 0
                                                                                            var v88: String = ""
                                                                                            when (v0.tag) {
                                                                                                11 -> {
                                                                                                    var v56: US12 = US12_Rectangle
                                                                                                    v56 = US12_Ellipse
                                                                                                    var v57: String = ""
                                                                                                    v57 = "#EEEEEE"
                                                                                                    var v58: String = ""
                                                                                                    v58 = "#424242"
                                                                                                    var v59: US13 = US13_Solid
                                                                                                    v59 = US13_Solid
                                                                                                    var v60: String = ""
                                                                                                    v60 = "#000000"
                                                                                                    v83 = v56
                                                                                                    v84 = v57
                                                                                                    v85 = v58
                                                                                                    v86 = v59
                                                                                                    v87 = 10
                                                                                                    v88 = v60
                                                                                                }
                                                                                                else -> {
                                                                                                    var v77: US12 = US12_Rectangle
                                                                                                    var v78: String = ""
                                                                                                    var v79: String = ""
                                                                                                    var v80: US13 = US13_Solid
                                                                                                    var v81: Int = 0
                                                                                                    var v82: String = ""
                                                                                                    when (v0.tag) {
                                                                                                        12 -> {
                                                                                                            var v61: US12 = US12_Rectangle
                                                                                                            v61 = US12_Rectangle
                                                                                                            var v62: String = ""
                                                                                                            v62 = "#FFFFFF"
                                                                                                            var v63: String = ""
                                                                                                            v63 = "#212121"
                                                                                                            var v64: US13 = US13_Solid
                                                                                                            v64 = US13_Solid
                                                                                                            var v65: String = ""
                                                                                                            v65 = "#000000"
                                                                                                            v77 = v61
                                                                                                            v78 = v62
                                                                                                            v79 = v63
                                                                                                            v80 = v64
                                                                                                            v81 = 30
                                                                                                            v82 = v65
                                                                                                        }
                                                                                                        else -> {
                                                                                                            var v71: US12 = US12_Rectangle
                                                                                                            var v72: String = ""
                                                                                                            var v73: String = ""
                                                                                                            var v74: US13 = US13_Solid
                                                                                                            var v75: Int = 0
                                                                                                            var v76: String = ""
                                                                                                            when (v0.tag) {
                                                                                                                13 -> {
                                                                                                                    var v66: US12 = US12_Rectangle
                                                                                                                    v66 = US12_RoundRectangle
                                                                                                                    var v67: String = ""
                                                                                                                    v67 = "#FFFFFF"
                                                                                                                    var v68: String = ""
                                                                                                                    v68 = "#424242"
                                                                                                                    var v69: US13 = US13_Solid
                                                                                                                    v69 = US13_Solid
                                                                                                                    var v70: String = ""
                                                                                                                    v70 = "#000000"
                                                                                                                    v71 = v66
                                                                                                                    v72 = v67
                                                                                                                    v73 = v68
                                                                                                                    v74 = v69
                                                                                                                    v75 = 15
                                                                                                                    v76 = v70
                                                                                                                }
                                                                                                                else -> throw RuntimeException()
                                                                                                            }
                                                                                                            v77 = v71
                                                                                                            v78 = v72
                                                                                                            v79 = v73
                                                                                                            v80 = v74
                                                                                                            v81 = v75
                                                                                                            v82 = v76
                                                                                                        }
                                                                                                    }
                                                                                                    v83 = v77
                                                                                                    v84 = v78
                                                                                                    v85 = v79
                                                                                                    v86 = v80
                                                                                                    v87 = v81
                                                                                                    v88 = v82
                                                                                                }
                                                                                            }
                                                                                            v89 = v83
                                                                                            v90 = v84
                                                                                            v91 = v85
                                                                                            v92 = v86
                                                                                            v93 = v87
                                                                                            v94 = v88
                                                                                        }
                                                                                    }
                                                                                    v95 = v89
                                                                                    v96 = v90
                                                                                    v97 = v91
                                                                                    v98 = v92
                                                                                    v99 = v93
                                                                                    v100 = v94
                                                                                }
                                                                            }
                                                                            v101 = v95
                                                                            v102 = v96
                                                                            v103 = v97
                                                                            v104 = v98
                                                                            v105 = v99
                                                                            v106 = v100
                                                                        }
                                                                    }
                                                                    v107 = v101
                                                                    v108 = v102
                                                                    v109 = v103
                                                                    v110 = v104
                                                                    v111 = v105
                                                                    v112 = v106
                                                                }
                                                            }
                                                            v113 = v107
                                                            v114 = v108
                                                            v115 = v109
                                                            v116 = v110
                                                            v117 = v111
                                                            v118 = v112
                                                        }
                                                    }
                                                    v119 = v113
                                                    v120 = v114
                                                    v121 = v115
                                                    v122 = v116
                                                    v123 = v117
                                                    v124 = v118
                                                }
                                            }
                                            v125 = v119
                                            v126 = v120
                                            v127 = v121
                                            v128 = v122
                                            v129 = v123
                                            v130 = v124
                                        }
                                    }
                                    v131 = v125
                                    v132 = v126
                                    v133 = v127
                                    v134 = v128
                                    v135 = v129
                                    v136 = v130
                                }
                            }
                            v137 = v131
                            v138 = v132
                            v139 = v133
                            v140 = v134
                            v141 = v135
                            v142 = v136
                        }
                    }
                    v143 = v137
                    v144 = v138
                    v145 = v139
                    v146 = v140
                    v147 = v141
                    v148 = v142
                }
            }
            v149 = v143
            v150 = v144
            v151 = v145
            v152 = v146
            v153 = v147
            v154 = v148
        }
    }
    return SpiralTuple3(v149, v150, v151, v152, v153, v154)
}
fun sp_kind_style_in_105(p0: US49, p1: US51): SpiralTuple3 {
    var v0: US49 = p0
    var v1: US51 = p1
    var v2: US12 = US12_Rectangle
    var v3: String = ""
    var v4: String = ""
    var v5: US13 = US13_Solid
    var v6: Int = 0
    var v7: String = ""
    val spiral_tuple261 = sp_kind_style_104(v0)
    v2 = spiral_tuple261.v0
    v3 = spiral_tuple261.v1
    v4 = spiral_tuple261.v2
    v5 = spiral_tuple261.v3
    v6 = spiral_tuple261.v4
    v7 = spiral_tuple261.v5
    var v30: US12 = US12_Rectangle
    var v31: String = ""
    var v32: String = ""
    var v33: US13 = US13_Solid
    var v34: Int = 0
    var v35: String = ""
    when (v0.tag) {
        13 -> {
            when (v1.tag) {
                0 -> {
                    var v8: String = ""
                    v8 = "#C8E6C9"
                    var v9: String = ""
                    v9 = "#1B5E20"
                    v30 = v2
                    v31 = v8
                    v32 = v9
                    v33 = v5
                    v34 = v6
                    v35 = v7
                }
                else -> {
                    var v18: US12 = US12_Rectangle
                    var v19: String = ""
                    var v20: String = ""
                    var v21: US13 = US13_Solid
                    var v22: Int = 0
                    var v23: String = ""
                    when (v1.tag) {
                        1 -> {
                            var v10: String = ""
                            v10 = "#FFE0B2"
                            var v11: String = ""
                            v11 = "#BF360C"
                            v18 = v2
                            v19 = v10
                            v20 = v11
                            v21 = v5
                            v22 = v6
                            v23 = v7
                        }
                        else -> {
                            var v12: US12 = US12_Rectangle
                            var v13: String = ""
                            var v14: String = ""
                            var v15: US13 = US13_Solid
                            var v16: Int = 0
                            var v17: String = ""
                            when (v1.tag) {
                                2 -> {
                                    v12 = v2
                                    v13 = v3
                                    v14 = v4
                                    v15 = v5
                                    v16 = v6
                                    v17 = v7
                                }
                                else -> throw RuntimeException()
                            }
                            v18 = v12
                            v19 = v13
                            v20 = v14
                            v21 = v15
                            v22 = v16
                            v23 = v17
                        }
                    }
                    v30 = v18
                    v31 = v19
                    v32 = v20
                    v33 = v21
                    v34 = v22
                    v35 = v23
                }
            }
        }
        else -> {
            v30 = v2
            v31 = v3
            v32 = v4
            v33 = v5
            v34 = v6
            v35 = v7
        }
    }
    return SpiralTuple3(v30, v31, v32, v33, v34, v35)
}
fun sp_legend_103(): UH10 {
    var v0: US49 = US49_Entry
    v0 = US49_Api
    var v1: US12 = US12_Rectangle
    var v2: String = ""
    var v3: String = ""
    var v4: US13 = US13_Solid
    var v5: Int = 0
    var v6: String = ""
    val spiral_tuple232 = sp_kind_style_104(v0)
    v1 = spiral_tuple232.v0
    v2 = spiral_tuple232.v1
    v3 = spiral_tuple232.v2
    v4 = spiral_tuple232.v3
    v5 = spiral_tuple232.v4
    v6 = spiral_tuple232.v5
    var v7: US49 = US49_Entry
    v7 = US49_Impl
    var v8: US51 = US51_SingleFlight
    v8 = US51_SingleFlight
    var v9: US12 = US12_Rectangle
    var v10: String = ""
    var v11: String = ""
    var v12: US13 = US13_Solid
    var v13: Int = 0
    var v14: String = ""
    val spiral_tuple722 = sp_kind_style_in_105(v7, v8)
    v9 = spiral_tuple722.v0
    v10 = spiral_tuple722.v1
    v11 = spiral_tuple722.v2
    v12 = spiral_tuple722.v3
    v13 = spiral_tuple722.v4
    v14 = spiral_tuple722.v5
    var v15: US49 = US49_Entry
    v15 = US49_Impl
    var v16: US51 = US51_SingleFlight
    v16 = US51_Hopacsota
    var v17: US12 = US12_Rectangle
    var v18: String = ""
    var v19: String = ""
    var v20: US13 = US13_Solid
    var v21: Int = 0
    var v22: String = ""
    val spiral_tuple1226 = sp_kind_style_in_105(v15, v16)
    v17 = spiral_tuple1226.v0
    v18 = spiral_tuple1226.v1
    v19 = spiral_tuple1226.v2
    v20 = spiral_tuple1226.v3
    v21 = spiral_tuple1226.v4
    v22 = spiral_tuple1226.v5
    var v23: US49 = US49_Entry
    v23 = US49_Op
    var v24: US12 = US12_Rectangle
    var v25: String = ""
    var v26: String = ""
    var v27: US13 = US13_Solid
    var v28: Int = 0
    var v29: String = ""
    val spiral_tuple1675 = sp_kind_style_104(v23)
    v24 = spiral_tuple1675.v0
    v25 = spiral_tuple1675.v1
    v26 = spiral_tuple1675.v2
    v27 = spiral_tuple1675.v3
    v28 = spiral_tuple1675.v4
    v29 = spiral_tuple1675.v5
    var v30: US49 = US49_Entry
    v30 = US49_Data
    var v31: US12 = US12_Rectangle
    var v32: String = ""
    var v33: String = ""
    var v34: US13 = US13_Solid
    var v35: Int = 0
    var v36: String = ""
    val spiral_tuple2118 = sp_kind_style_104(v30)
    v31 = spiral_tuple2118.v0
    v32 = spiral_tuple2118.v1
    v33 = spiral_tuple2118.v2
    v34 = spiral_tuple2118.v3
    v35 = spiral_tuple2118.v4
    v36 = spiral_tuple2118.v5
    var v37: US49 = US49_Entry
    v37 = US49_Type
    var v38: US12 = US12_Rectangle
    var v39: String = ""
    var v40: String = ""
    var v41: US13 = US13_Solid
    var v42: Int = 0
    var v43: String = ""
    val spiral_tuple2561 = sp_kind_style_104(v37)
    v38 = spiral_tuple2561.v0
    v39 = spiral_tuple2561.v1
    v40 = spiral_tuple2561.v2
    v41 = spiral_tuple2561.v3
    v42 = spiral_tuple2561.v4
    v43 = spiral_tuple2561.v5
    var v44: US49 = US49_Entry
    v44 = US49_Note
    var v45: US12 = US12_Rectangle
    var v46: String = ""
    var v47: String = ""
    var v48: US13 = US13_Solid
    var v49: Int = 0
    var v50: String = ""
    val spiral_tuple3004 = sp_kind_style_104(v44)
    v45 = spiral_tuple3004.v0
    v46 = spiral_tuple3004.v1
    v47 = spiral_tuple3004.v2
    v48 = spiral_tuple3004.v3
    v49 = spiral_tuple3004.v4
    v50 = spiral_tuple3004.v5
    var v51: US49 = US49_Entry
    v51 = US49_Cfg
    var v52: US12 = US12_Rectangle
    var v53: String = ""
    var v54: String = ""
    var v55: US13 = US13_Solid
    var v56: Int = 0
    var v57: String = ""
    val spiral_tuple3446 = sp_kind_style_104(v51)
    v52 = spiral_tuple3446.v0
    v53 = spiral_tuple3446.v1
    v54 = spiral_tuple3446.v2
    v55 = spiral_tuple3446.v3
    v56 = spiral_tuple3446.v4
    v57 = spiral_tuple3446.v5
    var v58: US49 = US49_Entry
    v58 = US49_Err
    var v59: US12 = US12_Rectangle
    var v60: String = ""
    var v61: String = ""
    var v62: US13 = US13_Solid
    var v63: Int = 0
    var v64: String = ""
    val spiral_tuple3888 = sp_kind_style_104(v58)
    v59 = spiral_tuple3888.v0
    v60 = spiral_tuple3888.v1
    v61 = spiral_tuple3888.v2
    v62 = spiral_tuple3888.v3
    v63 = spiral_tuple3888.v4
    v64 = spiral_tuple3888.v5
    var v65: US47 = US47_Flow
    v65 = US47_Flow
    var v66: String = ""
    var v67: US13 = US13_Solid
    var v68: Int = 0
    var v69: US14 = US14_Standard
    val spiral_tuple4279 = sp_link_style_100(v65)
    v66 = spiral_tuple4279.v0
    v67 = spiral_tuple4279.v1
    v68 = spiral_tuple4279.v2
    v69 = spiral_tuple4279.v3
    var v70: US47 = US47_Flow
    v70 = US47_Call
    var v71: String = ""
    var v72: US13 = US13_Solid
    var v73: Int = 0
    var v74: US14 = US14_Standard
    val spiral_tuple4610 = sp_link_style_100(v70)
    v71 = spiral_tuple4610.v0
    v72 = spiral_tuple4610.v1
    v73 = spiral_tuple4610.v2
    v74 = spiral_tuple4610.v3
    var v75: US47 = US47_Flow
    v75 = US47_DataFlow
    var v76: String = ""
    var v77: US13 = US13_Solid
    var v78: Int = 0
    var v79: US14 = US14_Standard
    val spiral_tuple4945 = sp_link_style_100(v75)
    v76 = spiral_tuple4945.v0
    v77 = spiral_tuple4945.v1
    v78 = spiral_tuple4945.v2
    v79 = spiral_tuple4945.v3
    var v80: US47 = US47_Flow
    v80 = US47_ToSingleFlight
    var v81: String = ""
    var v82: US13 = US13_Solid
    var v83: Int = 0
    var v84: US14 = US14_Standard
    val spiral_tuple5286 = sp_link_style_100(v80)
    v81 = spiral_tuple5286.v0
    v82 = spiral_tuple5286.v1
    v83 = spiral_tuple5286.v2
    v84 = spiral_tuple5286.v3
    var v85: US47 = US47_Flow
    v85 = US47_ToHopacsota
    var v86: String = ""
    var v87: US13 = US13_Solid
    var v88: Int = 0
    var v89: US14 = US14_Standard
    val spiral_tuple5624 = sp_link_style_100(v85)
    v86 = spiral_tuple5624.v0
    v87 = spiral_tuple5624.v1
    v88 = spiral_tuple5624.v2
    v89 = spiral_tuple5624.v3
    var v90: US47 = US47_Flow
    v90 = US47_Engine
    var v91: String = ""
    var v92: US13 = US13_Solid
    var v93: Int = 0
    var v94: US14 = US14_Standard
    val spiral_tuple5957 = sp_link_style_100(v90)
    v91 = spiral_tuple5957.v0
    v92 = spiral_tuple5957.v1
    v93 = spiral_tuple5957.v2
    v94 = spiral_tuple5957.v3
    var v95: US47 = US47_Flow
    v95 = US47_Reuse
    var v96: String = ""
    var v97: US13 = US13_Solid
    var v98: Int = 0
    var v99: US14 = US14_Standard
    val spiral_tuple6289 = sp_link_style_100(v95)
    v96 = spiral_tuple6289.v0
    v97 = spiral_tuple6289.v1
    v98 = spiral_tuple6289.v2
    v99 = spiral_tuple6289.v3
    var v100: US47 = US47_Flow
    v100 = US47_NoteLink
    var v101: String = ""
    var v102: US13 = US13_Solid
    var v103: Int = 0
    var v104: US14 = US14_Standard
    val spiral_tuple6630 = sp_link_style_100(v100)
    v101 = spiral_tuple6630.v0
    v102 = spiral_tuple6630.v1
    v103 = spiral_tuple6630.v2
    v104 = spiral_tuple6630.v3
    var v105: String = ""
    v105 = "seam API"
    var v106: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v106 = US11_Swatch(v105, v1, v2, v3, v4, v5, v6)
    var v107: String = ""
    v107 = "single-flight impl"
    var v108: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v108 = US11_Swatch(v107, v9, v10, v11, v12, v13, v14)
    var v109: String = ""
    v109 = "hopacsota impl"
    var v110: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v110 = US11_Swatch(v109, v17, v18, v19, v20, v21, v22)
    var v111: String = ""
    v111 = "peval operation"
    var v112: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v112 = US11_Swatch(v111, v24, v25, v26, v27, v28, v29)
    var v113: String = ""
    v113 = "state / data"
    var v114: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v114 = US11_Swatch(v113, v31, v32, v33, v34, v35, v36)
    var v115: String = ""
    v115 = "type"
    var v116: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v116 = US11_Swatch(v115, v38, v39, v40, v41, v42, v43)
    var v117: String = ""
    v117 = "divergence note"
    var v118: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v118 = US11_Swatch(v117, v45, v46, v47, v48, v49, v50)
    var v119: String = ""
    v119 = "env switch"
    var v120: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v120 = US11_Swatch(v119, v52, v53, v54, v55, v56, v57)
    var v121: String = ""
    v121 = "error"
    var v122: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v122 = US11_Swatch(v121, v59, v60, v61, v62, v63, v64)
    var v123: String = ""
    v123 = "pipeline"
    var v124: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v124 = US11_Stroke(v123, v66, v67, v68, v69)
    var v125: String = ""
    v125 = "call"
    var v126: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v126 = US11_Stroke(v125, v71, v72, v73, v74)
    var v127: String = ""
    v127 = "data"
    var v128: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v128 = US11_Stroke(v127, v76, v77, v78, v79)
    var v129: String = ""
    v129 = "-> single-flight"
    var v130: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v130 = US11_Stroke(v129, v81, v82, v83, v84)
    var v131: String = ""
    v131 = "-> hopacsota"
    var v132: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v132 = US11_Stroke(v131, v86, v87, v88, v89)
    var v133: String = ""
    v133 = "engine"
    var v134: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v134 = US11_Stroke(v133, v91, v92, v93, v94)
    var v135: String = ""
    v135 = "reuse"
    var v136: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v136 = US11_Stroke(v135, v96, v97, v98, v99)
    var v137: String = ""
    v137 = "note"
    var v138: US11 = US11_Swatch("", US12_Rectangle, "", "", US13_Solid, 0, "")
    v138 = US11_Stroke(v137, v101, v102, v103, v104)
    var v139: UH10 = UH10_Nil
    v139 = UH10_Nil
    var v140: UH10 = UH10_Nil
    v140 = UH10_Cons(v138, v139)
    var v141: UH10 = UH10_Nil
    v141 = UH10_Cons(v136, v140)
    var v142: UH10 = UH10_Nil
    v142 = UH10_Cons(v134, v141)
    var v143: UH10 = UH10_Nil
    v143 = UH10_Cons(v132, v142)
    var v144: UH10 = UH10_Nil
    v144 = UH10_Cons(v130, v143)
    var v145: UH10 = UH10_Nil
    v145 = UH10_Cons(v128, v144)
    var v146: UH10 = UH10_Nil
    v146 = UH10_Cons(v126, v145)
    var v147: UH10 = UH10_Nil
    v147 = UH10_Cons(v124, v146)
    var v148: UH10 = UH10_Nil
    v148 = UH10_Cons(v122, v147)
    var v149: UH10 = UH10_Nil
    v149 = UH10_Cons(v120, v148)
    var v150: UH10 = UH10_Nil
    v150 = UH10_Cons(v118, v149)
    var v151: UH10 = UH10_Nil
    v151 = UH10_Cons(v116, v150)
    var v152: UH10 = UH10_Nil
    v152 = UH10_Cons(v114, v151)
    var v153: UH10 = UH10_Nil
    v153 = UH10_Cons(v112, v152)
    var v154: UH10 = UH10_Nil
    v154 = UH10_Cons(v110, v153)
    var v155: UH10 = UH10_Nil
    v155 = UH10_Cons(v108, v154)
    return UH10_Cons(v106, v155)
}
fun sp_method107(p0: UH15, p1: UH15): UH15 {
    var v0: UH15 = p0
    var v1: UH15 = p1
    when (v0.tag) {
        1 -> {
            var v2: UH16 = (v0 as UH15_Cons).f0
            var v3: UH15 = (v0 as UH15_Cons).f1
            var v4: UH15 = UH15_Nil
            v4 = sp_method107(v3, v1)
            return UH15_Cons(v2, v4)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_regions_108(): UH52 {
    var v0: US48 = US48_RWhole
    v0 = US48_RWhole
    var v1: US48 = US48_RWhole
    v1 = US48_RInfra
    var v2: US48 = US48_RWhole
    v2 = US48_RFront
    var v3: US48 = US48_RWhole
    v3 = US48_RProj
    var v4: US48 = US48_RWhole
    v4 = US48_RTyping
    var v5: US48 = US48_RWhole
    v5 = US48_RPrep
    var v6: US48 = US48_RWhole
    v6 = US48_RPe
    var v7: US48 = US48_RWhole
    v7 = US48_RPeTables
    var v8: US48 = US48_RWhole
    v8 = US48_RPeOps
    var v9: US48 = US48_RWhole
    v9 = US48_RPeGuards
    var v10: US48 = US48_RWhole
    v10 = US48_RSeams
    var v11: US48 = US48_RWhole
    v11 = US48_RSf
    var v12: US48 = US48_RWhole
    v12 = US48_RHs
    var v13: US48 = US48_RWhole
    v13 = US48_RHsSeams
    var v14: US48 = US48_RWhole
    v14 = US48_RHsEngine
    var v15: US48 = US48_RWhole
    v15 = US48_RHsTable
    var v16: US48 = US48_RWhole
    v16 = US48_RHsGraph
    var v17: US48 = US48_RWhole
    v17 = US48_RHsWork
    var v18: US48 = US48_RWhole
    v18 = US48_RHsCtx
    var v19: US48 = US48_RWhole
    v19 = US48_RDiverge
    var v20: US48 = US48_RWhole
    v20 = US48_RCodegen
    var v21: US48 = US48_RWhole
    v21 = US48_RBackends
    var v22: US48 = US48_RWhole
    v22 = US48_RServer
    var v23: US48 = US48_RWhole
    v23 = US48_REnv
    var v24: UH52 = UH52_Nil
    v24 = UH52_Nil
    var v25: UH52 = UH52_Nil
    v25 = UH52_Cons(v23, v24)
    var v26: UH52 = UH52_Nil
    v26 = UH52_Cons(v22, v25)
    var v27: UH52 = UH52_Nil
    v27 = UH52_Cons(v21, v26)
    var v28: UH52 = UH52_Nil
    v28 = UH52_Cons(v20, v27)
    var v29: UH52 = UH52_Nil
    v29 = UH52_Cons(v19, v28)
    var v30: UH52 = UH52_Nil
    v30 = UH52_Cons(v18, v29)
    var v31: UH52 = UH52_Nil
    v31 = UH52_Cons(v17, v30)
    var v32: UH52 = UH52_Nil
    v32 = UH52_Cons(v16, v31)
    var v33: UH52 = UH52_Nil
    v33 = UH52_Cons(v15, v32)
    var v34: UH52 = UH52_Nil
    v34 = UH52_Cons(v14, v33)
    var v35: UH52 = UH52_Nil
    v35 = UH52_Cons(v13, v34)
    var v36: UH52 = UH52_Nil
    v36 = UH52_Cons(v12, v35)
    var v37: UH52 = UH52_Nil
    v37 = UH52_Cons(v11, v36)
    var v38: UH52 = UH52_Nil
    v38 = UH52_Cons(v10, v37)
    var v39: UH52 = UH52_Nil
    v39 = UH52_Cons(v9, v38)
    var v40: UH52 = UH52_Nil
    v40 = UH52_Cons(v8, v39)
    var v41: UH52 = UH52_Nil
    v41 = UH52_Cons(v7, v40)
    var v42: UH52 = UH52_Nil
    v42 = UH52_Cons(v6, v41)
    var v43: UH52 = UH52_Nil
    v43 = UH52_Cons(v5, v42)
    var v44: UH52 = UH52_Nil
    v44 = UH52_Cons(v4, v43)
    var v45: UH52 = UH52_Nil
    v45 = UH52_Cons(v3, v44)
    var v46: UH52 = UH52_Nil
    v46 = UH52_Cons(v2, v45)
    var v47: UH52 = UH52_Nil
    v47 = UH52_Cons(v1, v46)
    return UH52_Cons(v0, v47)
}
fun sp_info_of_region_111(p0: US48): SpiralTuple8 {
    var v0: US48 = p0
    var v212: String = ""
    var v213: US53 = US53_TopLevel
    var v214: String = ""
    var v215: String = ""
    when (v0.tag) {
        0 -> {
            var v1: String = ""
            v1 = "whole"
            var v2: US53 = US53_TopLevel
            v2 = US53_TopLevel
            var v3: String = ""
            v3 = "spiral_compiler.fs (35,585 lines, 912 sections)"
            var v4: String = ""
            v4 = "apps/compiler: one F# file = the whole compiler core"
            v212 = v1
            v213 = v2
            v214 = v3
            v215 = v4
        }
        else -> {
            var v208: String = ""
            var v209: US53 = US53_TopLevel
            var v210: String = ""
            var v211: String = ""
            when (v0.tag) {
                1 -> {
                    var v5: String = ""
                    v5 = "infra"
                    var v6: US48 = US48_RWhole
                    v6 = US48_RWhole
                    var v7: US53 = US53_TopLevel
                    v7 = US53_Under(v6)
                    var v8: String = ""
                    v8 = "Shared infrastructure"
                    var v9: String = ""
                    v9 = "used by every phase, both builds"
                    v208 = v5
                    v209 = v7
                    v210 = v8
                    v211 = v9
                }
                else -> {
                    var v204: String = ""
                    var v205: US53 = US53_TopLevel
                    var v206: String = ""
                    var v207: String = ""
                    when (v0.tag) {
                        2 -> {
                            var v10: String = ""
                            v10 = "front"
                            var v11: US48 = US48_RWhole
                            v11 = US48_RWhole
                            var v12: US53 = US53_TopLevel
                            v12 = US53_Under(v11)
                            var v13: String = ""
                            v13 = "Front end"
                            var v14: String = ""
                            v14 = "text -> tokens -> blocks -> AST bundles"
                            v204 = v10
                            v205 = v12
                            v206 = v13
                            v207 = v14
                        }
                        else -> {
                            var v200: String = ""
                            var v201: US53 = US53_TopLevel
                            var v202: String = ""
                            var v203: String = ""
                            when (v0.tag) {
                                3 -> {
                                    var v15: String = ""
                                    v15 = "proj"
                                    var v16: US48 = US48_RWhole
                                    v16 = US48_RWhole
                                    var v17: US53 = US53_TopLevel
                                    v17 = US53_Under(v16)
                                    var v18: String = ""
                                    v18 = "Projects & incremental (WDiff / SpiProj)"
                                    var v19: String = ""
                                    v19 = "Hopac promises, dirty-node recompute"
                                    v200 = v15
                                    v201 = v17
                                    v202 = v18
                                    v203 = v19
                                }
                                else -> {
                                    var v196: String = ""
                                    var v197: US53 = US53_TopLevel
                                    var v198: String = ""
                                    var v199: String = ""
                                    when (v0.tag) {
                                        4 -> {
                                            var v20: String = ""
                                            v20 = "typing"
                                            var v21: US48 = US48_RWhole
                                            v21 = US48_RWhole
                                            var v22: US53 = US53_TopLevel
                                            v22 = US53_Under(v21)
                                            var v23: String = ""
                                            v23 = "Type inference"
                                            var v24: String = ""
                                            v24 = "Infer + adapters"
                                            v196 = v20
                                            v197 = v22
                                            v198 = v23
                                            v199 = v24
                                        }
                                        else -> {
                                            var v192: String = ""
                                            var v193: US53 = US53_TopLevel
                                            var v194: String = ""
                                            var v195: String = ""
                                            when (v0.tag) {
                                                5 -> {
                                                    var v25: String = ""
                                                    v25 = "prep"
                                                    var v26: US48 = US48_RWhole
                                                    v26 = US48_RWhole
                                                    var v27: US53 = US53_TopLevel
                                                    v27 = US53_Under(v26)
                                                    var v28: String = ""
                                                    v28 = "Prepass"
                                                    var v29: String = ""
                                                    v29 = "lowering to the evaluable core"
                                                    v192 = v25
                                                    v193 = v27
                                                    v194 = v28
                                                    v195 = v29
                                                }
                                                else -> {
                                                    var v188: String = ""
                                                    var v189: US53 = US53_TopLevel
                                                    var v190: String = ""
                                                    var v191: String = ""
                                                    when (v0.tag) {
                                                        6 -> {
                                                            var v30: String = ""
                                                            v30 = "pe"
                                                            var v31: US48 = US48_RWhole
                                                            v31 = US48_RWhole
                                                            var v32: US53 = US53_TopLevel
                                                            v32 = US53_Under(v31)
                                                            var v33: String = ""
                                                            v33 = "Partial evaluation core (PartEval, L8930-12004)"
                                                            var v34: String = ""
                                                            v34 = "the SAME code in both builds; every concurrency-sensitive step goes through PartEvalSeams"
                                                            v188 = v30
                                                            v189 = v32
                                                            v190 = v33
                                                            v191 = v34
                                                        }
                                                        else -> {
                                                            var v184: String = ""
                                                            var v185: US53 = US53_TopLevel
                                                            var v186: String = ""
                                                            var v187: String = ""
                                                            when (v0.tag) {
                                                                7 -> {
                                                                    var v35: String = ""
                                                                    v35 = "pe_tables"
                                                                    var v36: US48 = US48_RWhole
                                                                    v36 = US48_RPe
                                                                    var v37: US53 = US53_TopLevel
                                                                    v37 = US53_Under(v36)
                                                                    var v38: String = ""
                                                                    v38 = "peval state (per build)"
                                                                    var v39: String = ""
                                                                    v39 = "memo tables keyed by hash-consed join point keys"
                                                                    v184 = v35
                                                                    v185 = v37
                                                                    v186 = v38
                                                                    v187 = v39
                                                                }
                                                                else -> {
                                                                    var v180: String = ""
                                                                    var v181: US53 = US53_TopLevel
                                                                    var v182: String = ""
                                                                    var v183: String = ""
                                                                    when (v0.tag) {
                                                                        8 -> {
                                                                            var v40: String = ""
                                                                            v40 = "pe_ops"
                                                                            var v41: US48 = US48_RWhole
                                                                            v41 = US48_RPe
                                                                            var v42: US53 = US53_TopLevel
                                                                            v42 = US53_Under(v41)
                                                                            var v43: String = ""
                                                                            v43 = "peval operations that touch the seams"
                                                                            var v44: String = ""
                                                                            v44 = "35 call sites"
                                                                            v180 = v40
                                                                            v181 = v42
                                                                            v182 = v43
                                                                            v183 = v44
                                                                        }
                                                                        else -> {
                                                                            var v176: String = ""
                                                                            var v177: US53 = US53_TopLevel
                                                                            var v178: String = ""
                                                                            var v179: String = ""
                                                                            when (v0.tag) {
                                                                                9 -> {
                                                                                    var v45: String = ""
                                                                                    v45 = "pe_guards"
                                                                                    var v46: US48 = US48_RWhole
                                                                                    v46 = US48_RPe
                                                                                    var v47: US53 = US53_TopLevel
                                                                                    v47 = US53_Under(v46)
                                                                                    var v48: String = ""
                                                                                    v48 = "Runaway guards & thread-local state"
                                                                                    var v49: String = ""
                                                                                    v49 = "what a parallel body must save/restore"
                                                                                    v176 = v45
                                                                                    v177 = v47
                                                                                    v178 = v48
                                                                                    v179 = v49
                                                                                }
                                                                                else -> {
                                                                                    var v172: String = ""
                                                                                    var v173: US53 = US53_TopLevel
                                                                                    var v174: String = ""
                                                                                    var v175: String = ""
                                                                                    when (v0.tag) {
                                                                                        10 -> {
                                                                                            var v50: String = ""
                                                                                            v50 = "seams"
                                                                                            var v51: US48 = US48_RWhole
                                                                                            v51 = US48_RWhole
                                                                                            var v52: US53 = US53_TopLevel
                                                                                            v52 = US53_Under(v51)
                                                                                            var v53: String = ""
                                                                                            v53 = "PartEvalSeams API (identical signatures in both builds)"
                                                                                            var v54: String = ""
                                                                                            v54 = "Adapter<'env> + Session<'env> + 16 operations"
                                                                                            v172 = v50
                                                                                            v173 = v52
                                                                                            v174 = v53
                                                                                            v175 = v54
                                                                                        }
                                                                                        else -> {
                                                                                            var v168: String = ""
                                                                                            var v169: US53 = US53_TopLevel
                                                                                            var v170: String = ""
                                                                                            var v171: String = ""
                                                                                            when (v0.tag) {
                                                                                                11 -> {
                                                                                                    var v55: String = ""
                                                                                                    v55 = "sf"
                                                                                                    var v56: US48 = US48_RWhole
                                                                                                    v56 = US48_RWhole
                                                                                                    var v57: US53 = US53_TopLevel
                                                                                                    v57 = US53_Under(v56)
                                                                                                    var v58: String = ""
                                                                                                    v58 = "SINGLE-FLIGHT build (#else branch, L8892-8928)"
                                                                                                    var v59: String = ""
                                                                                                    v59 = "every seam is an F# inline function: zero cost, depth-first, one thread"
                                                                                                    v168 = v55
                                                                                                    v169 = v57
                                                                                                    v170 = v58
                                                                                                    v171 = v59
                                                                                                }
                                                                                                else -> {
                                                                                                    var v164: String = ""
                                                                                                    var v165: US53 = US53_TopLevel
                                                                                                    var v166: String = ""
                                                                                                    var v167: String = ""
                                                                                                    when (v0.tag) {
                                                                                                        12 -> {
                                                                                                            var v60: String = ""
                                                                                                            v60 = "hs"
                                                                                                            var v61: US48 = US48_RWhole
                                                                                                            v61 = US48_RWhole
                                                                                                            var v62: US53 = US53_TopLevel
                                                                                                            v62 = US53_Under(v61)
                                                                                                            var v63: String = ""
                                                                                                            v63 = "HOPACSOTA build (#if SPIRAL_CORE_HOPACSOTA, L8241-8890)"
                                                                                                            var v64: String = ""
                                                                                                            v64 = "the single-flight core + a parallel layer"
                                                                                                            v164 = v60
                                                                                                            v165 = v62
                                                                                                            v166 = v63
                                                                                                            v167 = v64
                                                                                                        }
                                                                                                        else -> {
                                                                                                            var v160: String = ""
                                                                                                            var v161: US53 = US53_TopLevel
                                                                                                            var v162: String = ""
                                                                                                            var v163: String = ""
                                                                                                            when (v0.tag) {
                                                                                                                13 -> {
                                                                                                                    var v65: String = ""
                                                                                                                    v65 = "hs_seams"
                                                                                                                    var v66: US48 = US48_RWhole
                                                                                                                    v66 = US48_RHs
                                                                                                                    var v67: US53 = US53_TopLevel
                                                                                                                    v67 = US53_Under(v66)
                                                                                                                    var v68: String = ""
                                                                                                                    v68 = "PartEvalSeams (hopacsota, L8660-8890)"
                                                                                                                    var v69: String = ""
                                                                                                                    v69 = "Session: policy, deferral, markers, canonical replay"
                                                                                                                    v160 = v65
                                                                                                                    v161 = v67
                                                                                                                    v162 = v68
                                                                                                                    v163 = v69
                                                                                                                }
                                                                                                                else -> {
                                                                                                                    var v156: String = ""
                                                                                                                    var v157: US53 = US53_TopLevel
                                                                                                                    var v158: String = ""
                                                                                                                    var v159: String = ""
                                                                                                                    when (v0.tag) {
                                                                                                                        14 -> {
                                                                                                                            var v70: String = ""
                                                                                                                            v70 = "hs_engine"
                                                                                                                            var v71: US48 = US48_RWhole
                                                                                                                            v71 = US48_RHs
                                                                                                                            var v72: US53 = US53_TopLevel
                                                                                                                            v72 = US53_Under(v71)
                                                                                                                            var v73: String = ""
                                                                                                                            v73 = "PartEvalEngine (L8243-8657)"
                                                                                                                            var v74: String = ""
                                                                                                                            v74 = "Hopac-mediated claim table on dedicated 1.5 GB body threads"
                                                                                                                            v156 = v70
                                                                                                                            v157 = v72
                                                                                                                            v158 = v73
                                                                                                                            v159 = v74
                                                                                                                        }
                                                                                                                        else -> {
                                                                                                                            var v152: String = ""
                                                                                                                            var v153: US53 = US53_TopLevel
                                                                                                                            var v154: String = ""
                                                                                                                            var v155: String = ""
                                                                                                                            when (v0.tag) {
                                                                                                                                15 -> {
                                                                                                                                    var v75: String = ""
                                                                                                                                    v75 = "hs_table"
                                                                                                                                    var v76: US48 = US48_RWhole
                                                                                                                                    v76 = US48_RHsEngine
                                                                                                                                    var v77: US53 = US53_TopLevel
                                                                                                                                    v77 = US53_Under(v76)
                                                                                                                                    var v78: String = ""
                                                                                                                                    v78 = "Table: the single source of truth for keys"
                                                                                                                                    var v79: String = ""
                                                                                                                                    v79 = "serialized behind MVar<Table>"
                                                                                                                                    v152 = v75
                                                                                                                                    v153 = v77
                                                                                                                                    v154 = v78
                                                                                                                                    v155 = v79
                                                                                                                                }
                                                                                                                                else -> {
                                                                                                                                    var v148: String = ""
                                                                                                                                    var v149: US53 = US53_TopLevel
                                                                                                                                    var v150: String = ""
                                                                                                                                    var v151: String = ""
                                                                                                                                    when (v0.tag) {
                                                                                                                                        16 -> {
                                                                                                                                            var v80: String = ""
                                                                                                                                            v80 = "hs_graph"
                                                                                                                                            var v81: US48 = US48_RWhole
                                                                                                                                            v81 = US48_RHsEngine
                                                                                                                                            var v82: US53 = US53_TopLevel
                                                                                                                                            v82 = US53_Under(v81)
                                                                                                                                            var v83: String = ""
                                                                                                                                            v83 = "Graph: wait-for edges"
                                                                                                                                            var v84: String = ""
                                                                                                                                            v84 = "deadlock detection"
                                                                                                                                            v148 = v80
                                                                                                                                            v149 = v82
                                                                                                                                            v150 = v83
                                                                                                                                            v151 = v84
                                                                                                                                        }
                                                                                                                                        else -> {
                                                                                                                                            var v144: String = ""
                                                                                                                                            var v145: US53 = US53_TopLevel
                                                                                                                                            var v146: String = ""
                                                                                                                                            var v147: String = ""
                                                                                                                                            when (v0.tag) {
                                                                                                                                                17 -> {
                                                                                                                                                    var v85: String = ""
                                                                                                                                                    v85 = "hs_work"
                                                                                                                                                    var v86: US48 = US48_RWhole
                                                                                                                                                    v86 = US48_RHsEngine
                                                                                                                                                    var v87: US53 = US53_TopLevel
                                                                                                                                                    v87 = US53_Under(v86)
                                                                                                                                                    var v88: String = ""
                                                                                                                                                    v88 = "Worklist: body scheduling"
                                                                                                                                                    var v89: String = ""
                                                                                                                                                    v89 = "tokens = workers - 1"
                                                                                                                                                    v144 = v85
                                                                                                                                                    v145 = v87
                                                                                                                                                    v146 = v88
                                                                                                                                                    v147 = v89
                                                                                                                                                }
                                                                                                                                                else -> {
                                                                                                                                                    var v140: String = ""
                                                                                                                                                    var v141: US53 = US53_TopLevel
                                                                                                                                                    var v142: String = ""
                                                                                                                                                    var v143: String = ""
                                                                                                                                                    when (v0.tag) {
                                                                                                                                                        18 -> {
                                                                                                                                                            var v90: String = ""
                                                                                                                                                            v90 = "hs_ctx"
                                                                                                                                                            var v91: US48 = US48_RWhole
                                                                                                                                                            v91 = US48_RHsEngine
                                                                                                                                                            var v92: US53 = US53_TopLevel
                                                                                                                                                            v92 = US53_Under(v91)
                                                                                                                                                            var v93: String = ""
                                                                                                                                                            v93 = "Per-thread BodyContext"
                                                                                                                                                            var v94: String = ""
                                                                                                                                                            v94 = "ThreadLocal, one per body thread + root"
                                                                                                                                                            v140 = v90
                                                                                                                                                            v141 = v92
                                                                                                                                                            v142 = v93
                                                                                                                                                            v143 = v94
                                                                                                                                                        }
                                                                                                                                                        else -> {
                                                                                                                                                            var v136: String = ""
                                                                                                                                                            var v137: US53 = US53_TopLevel
                                                                                                                                                            var v138: String = ""
                                                                                                                                                            var v139: String = ""
                                                                                                                                                            when (v0.tag) {
                                                                                                                                                                19 -> {
                                                                                                                                                                    var v95: String = ""
                                                                                                                                                                    v95 = "diverge"
                                                                                                                                                                    var v96: US48 = US48_RWhole
                                                                                                                                                                    v96 = US48_RWhole
                                                                                                                                                                    var v97: US53 = US53_TopLevel
                                                                                                                                                                    v97 = US53_Under(v96)
                                                                                                                                                                    var v98: String = ""
                                                                                                                                                                    v98 = "How the two builds diverge"
                                                                                                                                                                    var v99: String = ""
                                                                                                                                                                    v99 = "same inputs, same output text, different journeys"
                                                                                                                                                                    v136 = v95
                                                                                                                                                                    v137 = v97
                                                                                                                                                                    v138 = v98
                                                                                                                                                                    v139 = v99
                                                                                                                                                                }
                                                                                                                                                                else -> {
                                                                                                                                                                    var v132: String = ""
                                                                                                                                                                    var v133: US53 = US53_TopLevel
                                                                                                                                                                    var v134: String = ""
                                                                                                                                                                    var v135: String = ""
                                                                                                                                                                    when (v0.tag) {
                                                                                                                                                                        20 -> {
                                                                                                                                                                            var v100: String = ""
                                                                                                                                                                            v100 = "codegen"
                                                                                                                                                                            var v101: US48 = US48_RWhole
                                                                                                                                                                            v101 = US48_RWhole
                                                                                                                                                                            var v102: US53 = US53_TopLevel
                                                                                                                                                                            v102 = US53_Under(v101)
                                                                                                                                                                            var v103: String = ""
                                                                                                                                                                            v103 = "Code generation (19 backends)"
                                                                                                                                                                            var v104: String = ""
                                                                                                                                                                            v104 = "reads the peval result tables"
                                                                                                                                                                            v132 = v100
                                                                                                                                                                            v133 = v102
                                                                                                                                                                            v134 = v103
                                                                                                                                                                            v135 = v104
                                                                                                                                                                        }
                                                                                                                                                                        else -> {
                                                                                                                                                                            var v128: String = ""
                                                                                                                                                                            var v129: US53 = US53_TopLevel
                                                                                                                                                                            var v130: String = ""
                                                                                                                                                                            var v131: String = ""
                                                                                                                                                                            when (v0.tag) {
                                                                                                                                                                                21 -> {
                                                                                                                                                                                    var v105: String = ""
                                                                                                                                                                                    v105 = "backends"
                                                                                                                                                                                    var v106: US48 = US48_RWhole
                                                                                                                                                                                    v106 = US48_RCodegen
                                                                                                                                                                                    var v107: US53 = US53_TopLevel
                                                                                                                                                                                    v107 = US53_Under(v106)
                                                                                                                                                                                    var v108: String = ""
                                                                                                                                                                                    v108 = "Backends"
                                                                                                                                                                                    var v109: String = ""
                                                                                                                                                                                    v109 = "build_many / build dispatch (L35115)"
                                                                                                                                                                                    v128 = v105
                                                                                                                                                                                    v129 = v107
                                                                                                                                                                                    v130 = v108
                                                                                                                                                                                    v131 = v109
                                                                                                                                                                                }
                                                                                                                                                                                else -> {
                                                                                                                                                                                    var v124: String = ""
                                                                                                                                                                                    var v125: US53 = US53_TopLevel
                                                                                                                                                                                    var v126: String = ""
                                                                                                                                                                                    var v127: String = ""
                                                                                                                                                                                    when (v0.tag) {
                                                                                                                                                                                        22 -> {
                                                                                                                                                                                            var v110: String = ""
                                                                                                                                                                                            v110 = "server"
                                                                                                                                                                                            var v111: US48 = US48_RWhole
                                                                                                                                                                                            v111 = US48_RWhole
                                                                                                                                                                                            var v112: US53 = US53_TopLevel
                                                                                                                                                                                            v112 = US53_Under(v111)
                                                                                                                                                                                            var v113: String = ""
                                                                                                                                                                                            v113 = "Supervisor, server & entry points"
                                                                                                                                                                                            var v114: String = ""
                                                                                                                                                                                            v114 = "where a build starts"
                                                                                                                                                                                            v124 = v110
                                                                                                                                                                                            v125 = v112
                                                                                                                                                                                            v126 = v113
                                                                                                                                                                                            v127 = v114
                                                                                                                                                                                        }
                                                                                                                                                                                        else -> {
                                                                                                                                                                                            var v120: String = ""
                                                                                                                                                                                            var v121: US53 = US53_TopLevel
                                                                                                                                                                                            var v122: String = ""
                                                                                                                                                                                            var v123: String = ""
                                                                                                                                                                                            when (v0.tag) {
                                                                                                                                                                                                23 -> {
                                                                                                                                                                                                    var v115: String = ""
                                                                                                                                                                                                    v115 = "env"
                                                                                                                                                                                                    var v116: US48 = US48_RWhole
                                                                                                                                                                                                    v116 = US48_RWhole
                                                                                                                                                                                                    var v117: US53 = US53_TopLevel
                                                                                                                                                                                                    v117 = US53_Under(v116)
                                                                                                                                                                                                    var v118: String = ""
                                                                                                                                                                                                    v118 = "Environment switches"
                                                                                                                                                                                                    var v119: String = ""
                                                                                                                                                                                                    v119 = "read once at startup"
                                                                                                                                                                                                    v120 = v115
                                                                                                                                                                                                    v121 = v117
                                                                                                                                                                                                    v122 = v118
                                                                                                                                                                                                    v123 = v119
                                                                                                                                                                                                }
                                                                                                                                                                                                else -> throw RuntimeException()
                                                                                                                                                                                            }
                                                                                                                                                                                            v124 = v120
                                                                                                                                                                                            v125 = v121
                                                                                                                                                                                            v126 = v122
                                                                                                                                                                                            v127 = v123
                                                                                                                                                                                        }
                                                                                                                                                                                    }
                                                                                                                                                                                    v128 = v124
                                                                                                                                                                                    v129 = v125
                                                                                                                                                                                    v130 = v126
                                                                                                                                                                                    v131 = v127
                                                                                                                                                                                }
                                                                                                                                                                            }
                                                                                                                                                                            v132 = v128
                                                                                                                                                                            v133 = v129
                                                                                                                                                                            v134 = v130
                                                                                                                                                                            v135 = v131
                                                                                                                                                                        }
                                                                                                                                                                    }
                                                                                                                                                                    v136 = v132
                                                                                                                                                                    v137 = v133
                                                                                                                                                                    v138 = v134
                                                                                                                                                                    v139 = v135
                                                                                                                                                                }
                                                                                                                                                            }
                                                                                                                                                            v140 = v136
                                                                                                                                                            v141 = v137
                                                                                                                                                            v142 = v138
                                                                                                                                                            v143 = v139
                                                                                                                                                        }
                                                                                                                                                    }
                                                                                                                                                    v144 = v140
                                                                                                                                                    v145 = v141
                                                                                                                                                    v146 = v142
                                                                                                                                                    v147 = v143
                                                                                                                                                }
                                                                                                                                            }
                                                                                                                                            v148 = v144
                                                                                                                                            v149 = v145
                                                                                                                                            v150 = v146
                                                                                                                                            v151 = v147
                                                                                                                                        }
                                                                                                                                    }
                                                                                                                                    v152 = v148
                                                                                                                                    v153 = v149
                                                                                                                                    v154 = v150
                                                                                                                                    v155 = v151
                                                                                                                                }
                                                                                                                            }
                                                                                                                            v156 = v152
                                                                                                                            v157 = v153
                                                                                                                            v158 = v154
                                                                                                                            v159 = v155
                                                                                                                        }
                                                                                                                    }
                                                                                                                    v160 = v156
                                                                                                                    v161 = v157
                                                                                                                    v162 = v158
                                                                                                                    v163 = v159
                                                                                                                }
                                                                                                            }
                                                                                                            v164 = v160
                                                                                                            v165 = v161
                                                                                                            v166 = v162
                                                                                                            v167 = v163
                                                                                                        }
                                                                                                    }
                                                                                                    v168 = v164
                                                                                                    v169 = v165
                                                                                                    v170 = v166
                                                                                                    v171 = v167
                                                                                                }
                                                                                            }
                                                                                            v172 = v168
                                                                                            v173 = v169
                                                                                            v174 = v170
                                                                                            v175 = v171
                                                                                        }
                                                                                    }
                                                                                    v176 = v172
                                                                                    v177 = v173
                                                                                    v178 = v174
                                                                                    v179 = v175
                                                                                }
                                                                            }
                                                                            v180 = v176
                                                                            v181 = v177
                                                                            v182 = v178
                                                                            v183 = v179
                                                                        }
                                                                    }
                                                                    v184 = v180
                                                                    v185 = v181
                                                                    v186 = v182
                                                                    v187 = v183
                                                                }
                                                            }
                                                            v188 = v184
                                                            v189 = v185
                                                            v190 = v186
                                                            v191 = v187
                                                        }
                                                    }
                                                    v192 = v188
                                                    v193 = v189
                                                    v194 = v190
                                                    v195 = v191
                                                }
                                            }
                                            v196 = v192
                                            v197 = v193
                                            v198 = v194
                                            v199 = v195
                                        }
                                    }
                                    v200 = v196
                                    v201 = v197
                                    v202 = v198
                                    v203 = v199
                                }
                            }
                            v204 = v200
                            v205 = v201
                            v206 = v202
                            v207 = v203
                        }
                    }
                    v208 = v204
                    v209 = v205
                    v210 = v206
                    v211 = v207
                }
            }
            v212 = v208
            v213 = v209
            v214 = v210
            v215 = v211
        }
    }
    return SpiralTuple8(v212, v213, v214, v215)
}
fun sp_same_region_112(p0: US48, p1: US48): Boolean {
    var v0: US48 = p0
    var v1: US48 = p1
    var v2: String = ""
    var v3: US53 = US53_TopLevel
    var v4: String = ""
    var v5: String = ""
    val spiral_tuple203 = sp_info_of_region_111(v0)
    v2 = spiral_tuple203.v0
    v3 = spiral_tuple203.v1
    v4 = spiral_tuple203.v2
    v5 = spiral_tuple203.v3
    var v6: String = ""
    var v7: US53 = US53_TopLevel
    var v8: String = ""
    var v9: String = ""
    val spiral_tuple472 = sp_info_of_region_111(v1)
    v6 = spiral_tuple472.v0
    v7 = spiral_tuple472.v1
    v8 = spiral_tuple472.v2
    v9 = spiral_tuple472.v3
    var v10: Boolean = false
    v10 = (v2 == v6)
    return v10
}
fun sp_under_110(p0: US48, p1: US48): Boolean {
    var v0: US48 = p0
    var v1: US48 = p1
    var v2: String = ""
    var v3: US53 = US53_TopLevel
    var v4: String = ""
    var v5: String = ""
    val spiral_tuple197 = sp_info_of_region_111(v1)
    v2 = spiral_tuple197.v0
    v3 = spiral_tuple197.v1
    v4 = spiral_tuple197.v2
    v5 = spiral_tuple197.v3
    when (v3.tag) {
        0 -> {
            return false
        }
        1 -> {
            var v6: US48 = (v3 as US53_Under).f0
            return sp_same_region_112(v6, v0)
        }
        else -> throw RuntimeException()
    }
}
fun sp_top_of_114(p0: US48): US48 {
    var v0: US48 = p0
    var v1: String = ""
    var v2: US53 = US53_TopLevel
    var v3: String = ""
    var v4: String = ""
    val spiral_tuple163 = sp_info_of_region_111(v0)
    v1 = spiral_tuple163.v0
    v2 = spiral_tuple163.v1
    v3 = spiral_tuple163.v2
    v4 = spiral_tuple163.v3
    when (v2.tag) {
        0 -> {
            return v0
        }
        1 -> {
            var v5: US48 = (v2 as US53_Under).f0
            when (v5.tag) {
                0 -> {
                    return v0
                }
                else -> {
                    return sp_top_of_114(v5)
                }
            }
        }
        else -> throw RuntimeException()
    }
}
fun sp_fill_of_115(p0: US48): String {
    var v0: US48 = p0
    when (v0.tag) {
        20 -> {
            var v11: String = ""
            v11 = "#E1F5FE"
            return v11
        }
        19 -> {
            var v10: String = ""
            v10 = "#FFFDE7"
            return v10
        }
        23 -> {
            var v13: String = ""
            v13 = "#FAFAFA"
            return v13
        }
        2 -> {
            var v2: String = ""
            v2 = "#E8F5E9"
            return v2
        }
        12 -> {
            var v9: String = ""
            v9 = "#FFF3E0"
            return v9
        }
        1 -> {
            var v1: String = ""
            v1 = "#F5F5F5"
            return v1
        }
        6 -> {
            var v6: String = ""
            v6 = "#EDE7F6"
            return v6
        }
        5 -> {
            var v5: String = ""
            v5 = "#FCE4EC"
            return v5
        }
        3 -> {
            var v3: String = ""
            v3 = "#E0F7FA"
            return v3
        }
        10 -> {
            var v7: String = ""
            v7 = "#E3F2FD"
            return v7
        }
        22 -> {
            var v12: String = ""
            v12 = "#ECEFF1"
            return v12
        }
        11 -> {
            var v8: String = ""
            v8 = "#E8F5E9"
            return v8
        }
        4 -> {
            var v4: String = ""
            v4 = "#F3E5F5"
            return v4
        }
        else -> {
            var v14: String = ""
            v14 = "#FFFFFF"
            return v14
        }
    }
}
fun sp_header_of_116(p0: US48): String {
    var v0: US48 = p0
    var v1: US48 = US48_RWhole
    v1 = sp_top_of_114(v0)
    when (v1.tag) {
        12 -> {
            var v3: String = ""
            v3 = "#E65100"
            return v3
        }
        10 -> {
            var v4: String = ""
            v4 = "#0D47A1"
            return v4
        }
        11 -> {
            var v2: String = ""
            v2 = "#2E7D32"
            return v2
        }
        else -> {
            var v5: US48 = US48_RWhole
            v5 = sp_top_of_114(v0)
            var v6: Boolean = false
            v6 = sp_same_region_112(v5, v0)
            if (v6) {
                var v7: String = ""
                v7 = "#000000"
                return v7
            } else {
                var v8: String = ""
                v8 = "#37474F"
                return v8
            }
        }
    }
}
fun sp_frame_of_113(p0: US48): SpiralTuple9 {
    var v0: US48 = p0
    var v1: String = ""
    var v2: US53 = US53_TopLevel
    var v3: String = ""
    var v4: String = ""
    val spiral_tuple173 = sp_info_of_region_111(v0)
    v1 = spiral_tuple173.v0
    v2 = spiral_tuple173.v1
    v3 = spiral_tuple173.v2
    v4 = spiral_tuple173.v3
    var v5: US48 = US48_RWhole
    v5 = sp_top_of_114(v0)
    var v6: Boolean = false
    v6 = sp_same_region_112(v5, v0)
    var v7: Boolean = false
    v7 = (v6 == false)
    var v8: String = ""
    v8 = ""
    var v9: Boolean = false
    v9 = (v4 == v8)
    var v13: String = ""
    if (v9) {
        v13 = v3
    } else {
        var v10: String = ""
        v10 = "  -  "
        var v11: String = ""
        v11 = v3 + v10 
        var v12: String = ""
        v12 = v11 + v4 
        v13 = v12
    }
    var v14: String = ""
    v14 = sp_fill_of_115(v0)
    var v15: String = ""
    v15 = sp_header_of_116(v0)
    var v16: Int = 0
    if (v7) {
        v16 = 18
    } else {
        v16 = 26
    }
    return SpiralTuple9(v1, v13, v14, v15, v7, v16)
}
fun sp_method109(p0: US48, p1: UH52, p2: UH15): UH15 {
    var v0: US48 = p0
    var v1: UH52 = p1
    var v2: UH15 = p2
    when (v1.tag) {
        1 -> {
            var v3: US48 = (v1 as UH52_Cons).f0
            var v4: UH52 = (v1 as UH52_Cons).f1
            var v5: Boolean = false
            v5 = sp_under_110(v0, v3)
            var v15: UH15 = UH15_Nil
            if (v5) {
                var v6: String = ""
                var v7: String = ""
                var v8: String = ""
                var v9: String = ""
                var v10: Boolean = false
                var v11: Int = 0
                val spiral_tuple603 = sp_frame_of_113(v3)
                v6 = spiral_tuple603.v0
                v7 = spiral_tuple603.v1
                v8 = spiral_tuple603.v2
                v9 = spiral_tuple603.v3
                v10 = spiral_tuple603.v4
                v11 = spiral_tuple603.v5
                var v12: UH15 = UH15_Nil
                v12 = sp_members_of_106(v3)
                var v13: UH16 = UH16_Leaf("", "", UH5_Nil, US12_Rectangle, "", "", US13_Solid, 0, "", 0, 0, 0, false)
                v13 = UH16_Nest(v6, v7, v8, v9, v10, v11, v12)
                v15 = UH15_Cons(v13, v2)
            } else {
                v15 = v2
            }
            return sp_method109(v0, v4, v15)
        }
        0 -> {
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_method117(p0: UH15, p1: UH15): UH15 {
    var v0: UH15 = p0
    var v1: UH15 = p1
    when (v0.tag) {
        1 -> {
            var v2: UH16 = (v0 as UH15_Cons).f0
            var v3: UH15 = (v0 as UH15_Cons).f1
            var v4: UH15 = UH15_Nil
            v4 = UH15_Cons(v2, v1)
            return sp_method117(v3, v4)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_build_of_120(p0: US48): US51 {
    var v0: US48 = p0
    var v1: US48 = US48_RWhole
    v1 = sp_top_of_114(v0)
    when (v1.tag) {
        12 -> {
            return US51_Hopacsota
        }
        11 -> {
            return US51_SingleFlight
        }
        else -> {
            return US51_Shared
        }
    }
}
fun sp_wrapped_123(p0: Int, p1: UH5, p2: String, p3: UH5): UH5 {
    var v0: Int = p0
    var v1: UH5 = p1
    var v2: String = p2
    var v3: UH5 = p3
    when (v1.tag) {
        1 -> {
            var v4: String = (v1 as UH5_Cons).f0
            var v5: UH5 = (v1 as UH5_Cons).f1
            var v6: String = ""
            v6 = ""
            var v7: Boolean = false
            v7 = (v2 == v6)
            if (v7) {
                return sp_wrapped_123(v0, v5, v4, v3)
            } else {
                var v9: Int = 0
                v9 = spiralStringLength(v2)
                var v10: Int = 0
                v10 = (v9 + 1)
                var v11: Int = 0
                v11 = spiralStringLength(v4)
                var v12: Int = 0
                v12 = (v10 + v11)
                var v13: Boolean = false
                v13 = (v12 > v0)
                if (v13) {
                    var v14: UH5 = UH5_Nil
                    v14 = UH5_Cons(v2, v3)
                    return sp_wrapped_123(v0, v5, v4, v14)
                } else {
                    var v16: String = ""
                    v16 = " "
                    var v17: String = ""
                    v17 = v2 + v16 
                    var v18: String = ""
                    v18 = v17 + v4 
                    return sp_wrapped_123(v0, v5, v18, v3)
                }
            }
        }
        0 -> {
            var v22: String = ""
            v22 = ""
            var v23: Boolean = false
            v23 = (v2 == v22)
            if (v23) {
                var v24: UH5 = UH5_Nil
                v24 = UH5_Nil
                return sp_method3(v3, v24)
            } else {
                var v26: UH5 = UH5_Nil
                v26 = UH5_Nil
                var v27: UH5 = UH5_Nil
                v27 = UH5_Cons(v2, v26)
                return sp_method3(v3, v27)
            }
        }
        else -> throw RuntimeException()
    }
}
fun sp_wrap_122(p0: Int, p1: String): UH5 {
    var v0: Int = p0
    var v1: String = p1
    var v2: String = ""
    v2 = " "
    var v3: UH5 = UH5_Nil
    v3 = sp_split_list_2(v2, v1)
    var v4: String = ""
    v4 = ""
    var v5: UH5 = UH5_Nil
    v5 = UH5_Nil
    return sp_wrapped_123(v0, v3, v4, v5)
}
fun sp_method125(p0: UH5, p1: Int): Int {
    var v0: UH5 = p0
    var v1: Int = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH5_Cons).f0
            var v3: UH5 = (v0 as UH5_Cons).f1
            var v4: Int = 0
            v4 = spiralStringLength(v2)
            var v5: Boolean = false
            v5 = (v4 > v1)
            var v6: Int = 0
            if (v5) {
                v6 = v4
            } else {
                v6 = v1
            }
            return sp_method125(v3, v6)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_longest_124(p0: UH5): Int {
    var v0: UH5 = p0
    var v1: Int = 0
    v1 = 0
    return sp_method125(v0, v1)
}
fun sp_method126(p0: UH5, p1: Int): Int {
    var v0: UH5 = p0
    var v1: Int = p1
    when (v0.tag) {
        1 -> {
            var v2: String = (v0 as UH5_Cons).f0
            var v3: UH5 = (v0 as UH5_Cons).f1
            var v4: Int = 0
            v4 = (v1 + 1)
            return sp_method126(v3, v4)
        }
        0 -> {
            return v1
        }
        else -> throw RuntimeException()
    }
}
fun sp_sized_box_121(p0: String, p1: String, p2: String, p3: US12, p4: String, p5: String, p6: US13, p7: Int, p8: String, p9: Int): SpiralTuple2 {
    var v0: String = p0
    var v1: String = p1
    var v2: String = p2
    var v3: US12 = p3
    var v4: String = p4
    var v5: String = p5
    var v6: US13 = p6
    var v7: Int = p7
    var v8: String = p8
    var v9: Int = p9
    var v10: String = ""
    v10 = ""
    var v11: Boolean = false
    v11 = (v2 == v10)
    var v14: UH5 = UH5_Nil
    if (v11) {
        v14 = UH5_Nil
    } else {
        v14 = sp_wrap_122(v9, v2)
    }
    var v15: Int = 0
    v15 = spiralStringLength(v1)
    var v16: Int = 0
    v16 = (v15 * 11)
    var v17: Int = 0
    v17 = (v16 / 10)
    var v18: Int = 0
    v18 = sp_longest_124(v14)
    var v19: Boolean = false
    v19 = (v17 > v18)
    var v21: Int = 0
    if (v19) {
        v21 = v17
    } else {
        v21 = sp_longest_124(v14)
    }
    var v22: Int = 0
    v22 = (v21 * 69)
    var v23: Int = 0
    v23 = (v22 / 10)
    var v24: Int = 0
    v24 = (v23 + 30)
    var v25: Int = 0
    v25 = 0
    var v26: Int = 0
    v26 = sp_method126(v14, v25)
    var v27: Int = 0
    v27 = (v26 + 1)
    var v28: Int = 0
    v28 = (v27 * 155)
    var v29: Int = 0
    v29 = (v28 / 10)
    var v30: Int = 0
    v30 = (v29 + 16)
    var v39: Int = 0
    when (v3.tag) {
        5 -> {
            var v34: Int = 0
            v34 = (v24 * 5)
            var v35: Int = 0
            v35 = (v34 / 4)
            v39 = v35
        }
        2 -> {
            var v31: Int = 0
            v31 = (v24 + 34)
            v39 = v31
        }
        3 -> {
            var v32: Int = 0
            v32 = (v24 + 34)
            v39 = v32
        }
        4 -> {
            var v33: Int = 0
            v33 = (v24 + 30)
            v39 = v33
        }
        else -> {
            v39 = v24
        }
    }
    var v42: Int = 0
    when (v3.tag) {
        5 -> {
            var v40: Int = 0
            v40 = (v30 * 13)
            var v41: Int = 0
            v41 = (v40 / 10)
            v42 = v41
        }
        else -> {
            v42 = v30
        }
    }
    return SpiralTuple2(v0, v1, v14, v3, v4, v5, v6, v7, v8, v39, v42, 12, true)
}
fun sp_box_of_119(p0: US24): SpiralTuple2 {
    var v0: US24 = p0
    var v1: String = ""
    var v2: US48 = US48_RWhole
    var v3: US49 = US49_Entry
    var v4: String = ""
    var v5: String = ""
    val spiral_tuple199 = sp_info_of_part_77(v0)
    v1 = spiral_tuple199.v0
    v2 = spiral_tuple199.v1
    v3 = spiral_tuple199.v2
    v4 = spiral_tuple199.v3
    v5 = spiral_tuple199.v4
    var v6: Int = 0
    when (v3.tag) {
        7 -> {
            v6 = 52
        }
        else -> {
            v6 = 40
        }
    }
    var v7: US51 = US51_SingleFlight
    v7 = sp_build_of_120(v2)
    var v8: US12 = US12_Rectangle
    var v9: String = ""
    var v10: String = ""
    var v11: US13 = US13_Solid
    var v12: Int = 0
    var v13: String = ""
    val spiral_tuple753 = sp_kind_style_in_105(v3, v7)
    v8 = spiral_tuple753.v0
    v9 = spiral_tuple753.v1
    v10 = spiral_tuple753.v2
    v11 = spiral_tuple753.v3
    v12 = spiral_tuple753.v4
    v13 = spiral_tuple753.v5
    var v14: String = ""
    var v15: String = ""
    var v16: UH5 = UH5_Nil
    var v17: US12 = US12_Rectangle
    var v18: String = ""
    var v19: String = ""
    var v20: US13 = US13_Solid
    var v21: Int = 0
    var v22: String = ""
    var v23: Int = 0
    var v24: Int = 0
    var v25: Int = 0
    var v26: Boolean = false
    val spiral_tuple1311 = sp_sized_box_121(v1, v4, v5, v8, v9, v10, v11, v12, v13, v6)
    v14 = spiral_tuple1311.v0
    v15 = spiral_tuple1311.v1
    v16 = spiral_tuple1311.v2
    v17 = spiral_tuple1311.v3
    v18 = spiral_tuple1311.v4
    v19 = spiral_tuple1311.v5
    v20 = spiral_tuple1311.v6
    v21 = spiral_tuple1311.v7
    v22 = spiral_tuple1311.v8
    v23 = spiral_tuple1311.v9
    v24 = spiral_tuple1311.v10
    v25 = spiral_tuple1311.v11
    v26 = spiral_tuple1311.v12
    return SpiralTuple2(v14, v15, v16, v17, v18, v19, v20, v21, v22, v23, v24, v25, v26)
}
fun sp_method118(p0: US48, p1: UH50, p2: UH15): UH15 {
    var v0: US48 = p0
    var v1: UH50 = p1
    var v2: UH15 = p2
    when (v1.tag) {
        1 -> {
            var v3: US24 = (v1 as UH50_Cons).f0
            var v4: UH50 = (v1 as UH50_Cons).f1
            var v5: String = ""
            var v6: US48 = US48_RWhole
            var v7: US49 = US49_Entry
            var v8: String = ""
            var v9: String = ""
            val spiral_tuple425 = sp_info_of_part_77(v3)
            v5 = spiral_tuple425.v0
            v6 = spiral_tuple425.v1
            v7 = spiral_tuple425.v2
            v8 = spiral_tuple425.v3
            v9 = spiral_tuple425.v4
            var v10: Boolean = false
            v10 = sp_same_region_112(v6, v0)
            var v26: UH15 = UH15_Nil
            if (v10) {
                var v11: String = ""
                var v12: String = ""
                var v13: UH5 = UH5_Nil
                var v14: US12 = US12_Rectangle
                var v15: String = ""
                var v16: String = ""
                var v17: US13 = US13_Solid
                var v18: Int = 0
                var v19: String = ""
                var v20: Int = 0
                var v21: Int = 0
                var v22: Int = 0
                var v23: Boolean = false
                val spiral_tuple1291 = sp_box_of_119(v3)
                v11 = spiral_tuple1291.v0
                v12 = spiral_tuple1291.v1
                v13 = spiral_tuple1291.v2
                v14 = spiral_tuple1291.v3
                v15 = spiral_tuple1291.v4
                v16 = spiral_tuple1291.v5
                v17 = spiral_tuple1291.v6
                v18 = spiral_tuple1291.v7
                v19 = spiral_tuple1291.v8
                v20 = spiral_tuple1291.v9
                v21 = spiral_tuple1291.v10
                v22 = spiral_tuple1291.v11
                v23 = spiral_tuple1291.v12
                var v24: UH16 = UH16_Leaf("", "", UH5_Nil, US12_Rectangle, "", "", US13_Solid, 0, "", 0, 0, 0, false)
                v24 = UH16_Leaf(v11, v12, v13, v14, v15, v16, v17, v18, v19, v20, v21, v22, v23)
                v26 = UH15_Cons(v24, v2)
            } else {
                v26 = v2
            }
            return sp_method118(v0, v4, v26)
        }
        0 -> {
            return v2
        }
        else -> throw RuntimeException()
    }
}
fun sp_members_of_106(p0: US48): UH15 {
    var v0: US48 = p0
    var v1: UH52 = UH52_Nil
    v1 = sp_regions_108()
    var v2: UH15 = UH15_Nil
    v2 = UH15_Nil
    var v3: UH15 = UH15_Nil
    v3 = sp_method109(v0, v1, v2)
    var v4: UH15 = UH15_Nil
    v4 = UH15_Nil
    var v5: UH15 = UH15_Nil
    v5 = sp_method117(v3, v4)
    var v6: UH50 = UH50_Nil
    v6 = sp_parts_101()
    var v7: UH15 = UH15_Nil
    v7 = UH15_Nil
    var v8: UH15 = UH15_Nil
    v8 = sp_method118(v0, v6, v7)
    var v9: UH15 = UH15_Nil
    v9 = UH15_Nil
    var v10: UH15 = UH15_Nil
    v10 = sp_method117(v8, v9)
    return sp_method107(v5, v10)
}
fun sp_compiler_diagram_73(): SpiralTuple1 {
    var v0: UH23 = UH23_Nil
    v0 = sp_relations_74()
    var v1: UH17 = UH17_Nil
    v1 = UH17_Nil
    var v2: UH17 = UH17_Nil
    v2 = sp_method75(v0, v1)
    var v3: UH50 = UH50_Nil
    v3 = sp_parts_101()
    var v4: Int = 0
    v4 = 0
    var v5: Int = 0
    v5 = sp_method102(v3, v4)
    var v6: String = ""
    v6 = (v5).toString()
    var v7: String = ""
    v7 = "spiral_compiler.fs - single-flight vs hopacsota partial evaluation, and the compiler around it ("
    var v8: String = ""
    v8 = v7 + v6 
    var v9: String = ""
    v9 = " elements, "
    var v10: String = ""
    v10 = v8 + v9 
    var v11: Int = 0
    v11 = 0
    var v12: Int = 0
    v12 = sp_method29(v2, v11)
    var v13: String = ""
    v13 = (v12).toString()
    var v14: String = ""
    v14 = v10 + v13 
    var v15: String = ""
    v15 = " relations)"
    var v16: String = ""
    v16 = v14 + v15 
    var v17: UH10 = UH10_Nil
    v17 = sp_legend_103()
    var v18: US48 = US48_RWhole
    v18 = US48_RWhole
    var v19: UH15 = UH15_Nil
    v19 = sp_members_of_106(v18)
    var v20: String = ""
    v20 = "Layout: yEd Incremental Hierarchic, left to right, generic edge labeling (headless yFiles). Every box cites its line in spiral/apps/compiler/spiral_compiler.fs."
    return SpiralTuple1(v16, v20, v17, v19, v2)
}
fun spiral_main(): Int {
    var v0: String = ""
    v0 = "GRAPHS_REPO"
    var v1: String = ""
    v1 = "."
    var v2: String = ""
    v2 = (System.getenv(v0) ?: v1)
    var v3: String = ""
    v3 = "/apps/graphs/out"
    var v4: String = ""
    v4 = v2 + v3 
    var v5: String = ""
    v5 = "GRAPHS_OUT"
    var v6: String = ""
    v6 = (System.getenv(v5) ?: v4)
    java.io.File(v6).mkdirs()
    var v7: String = ""
    v7 = "/docs/backlog/state/agile.spi"
    var v8: String = ""
    v8 = v2 + v7 
    var v9: String = ""
    v9 = java.io.File(v8).readText(Charsets.UTF_8)
    var v10: UH0 = UH0_Nil
    v10 = sp_parse_items_0(v9)
    var v11: Int = 0
    v11 = 0
    var v12: Int = 0
    v12 = sp_method21(v10, v11)
    var v13: String = ""
    v13 = (v12).toString()
    var v14: String = ""
    v14 = "backlog items "
    var v15: String = ""
    v15 = v14 + v13 
    println(v15)
    var v16: Int = 0
    v16 = 12
    var v17: Int = 0
    v17 = 10
    var v18: US8 = US8_Hierarchic(US9_TopToBottom)
    v18 = US8_CompactOrthogonal(v16, v17)
    var v19: String = ""
    var v20: String = ""
    var v21: UH10 = UH10_Nil
    var v22: UH15 = UH15_Nil
    var v23: UH17 = UH17_Nil
    val spiral_tuple1188 = sp_backlog_diagram_22(v10)
    v19 = spiral_tuple1188.v0
    v20 = spiral_tuple1188.v1
    v21 = spiral_tuple1188.v2
    v22 = spiral_tuple1188.v3
    v23 = spiral_tuple1188.v4
    var v24: US18 = US18_Modeled("", "", UH10_Nil, UH15_Nil, UH17_Nil)
    v24 = US18_Modeled(v19, v20, v21, v22, v23)
    var v25: US19 = US19_LaidOut("", "", UH10_Nil, UH15_Nil, UH17_Nil, US20_Handle(0))
    v25 = sp_lay_out_37(v18, v24)
    var v26: String = ""
    v26 = "/backlog"
    var v27: String = ""
    v27 = v6 + v26 
    sp_publish_55(v25, v27)
    var v28: US9 = US9_TopToBottom
    v28 = US9_LeftToRight
    var v29: US8 = US8_Hierarchic(US9_TopToBottom)
    v29 = US8_Hierarchic(v28)
    var v30: String = ""
    var v31: String = ""
    var v32: UH10 = UH10_Nil
    var v33: UH15 = UH15_Nil
    var v34: UH17 = UH17_Nil
    val spiral_tuple2030 = sp_compiler_diagram_73()
    v30 = spiral_tuple2030.v0
    v31 = spiral_tuple2030.v1
    v32 = spiral_tuple2030.v2
    v33 = spiral_tuple2030.v3
    v34 = spiral_tuple2030.v4
    var v35: US18 = US18_Modeled("", "", UH10_Nil, UH15_Nil, UH17_Nil)
    v35 = US18_Modeled(v30, v31, v32, v33, v34)
    var v36: US19 = US19_LaidOut("", "", UH10_Nil, UH15_Nil, UH17_Nil, US20_Handle(0))
    v36 = sp_lay_out_37(v29, v35)
    var v37: String = ""
    v37 = "/spiral-compiler"
    var v38: String = ""
    v38 = v6 + v37 
    sp_publish_55(v36, v38)
    var v39: String = ""
    v39 = "graphs done"
    println(v39)
    return 0
}
fun main() {
    var spiral_exit_code = 0
    val spiral_thread = Thread(null, { spiral_exit_code = spiral_main() }, "spiral_main", 1L shl 30)
    spiral_thread.start()
    spiral_thread.join()
    System.exit(spiral_exit_code)
}
