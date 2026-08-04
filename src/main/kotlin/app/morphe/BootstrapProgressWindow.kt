/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe

import app.morphe.engine.BootstrapProgressListener
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.util.concurrent.CountDownLatch
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

// Cyberdeck palette (to match our design philosophy).
private val VOID = Color(0x10, 0x10, 0x10)
private val BORDER = Color(0x2A, 0x2A, 0x2A)
private val TRACK = Color(0x24, 0x24, 0x24)
private val TEXT = Color(0xE3, 0xE3, 0xE3)
private val MUTED = Color(0x80, 0x80, 0x80)
private val NEON = Color(0x3B, 0x7B, 0xF7)
private val ERROR = Color(0xCF, 0x66, 0x79)

private val monoRegular: Font by lazy { loadBootstrapFont("fonts/JetBrainsMono-Regular.ttf") }
private val monoBold: Font by lazy { loadBootstrapFont("fonts/JetBrainsMono-Bold.ttf") }

private fun loadBootstrapFont(resourcePath: String): Font = try {
    BootstrapProgressWindow::class.java.classLoader.getResourceAsStream(resourcePath)!!.use { stream ->
        Font.createFont(Font.TRUETYPE_FONT, stream)
    }
} catch (e: Exception) {
    Font(Font.MONOSPACED, Font.PLAIN, 12)
}

/**
 * The first-run setup window shown while [app.morphe.engine.BootstrapDownloader]
 * fetches the GUI dependencies, so a user who double-clicked the jar is not left
 * staring at nothing. It has to be Swing (AWT), not Compose, because Skiko is one
 * of the very components being downloaded here.
 */
class BootstrapProgressWindow : BootstrapProgressListener {

    private var frame: JFrame? = null
    private var bar: NeonBar? = null
    private var status: JLabel? = null

    override fun onStart(componentCount: Int) {
        SwingUtilities.invokeAndWait {
            val f = JFrame("Morphe")
            f.isUndecorated = true
            f.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            f.isResizable = false
            f.isAlwaysOnTop = true

            val root = JPanel(BorderLayout(0, 18))
            root.background = VOID
            root.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(24, 28, 24, 28),
            )

            val header = JPanel(GridLayout(0, 1, 0, 7))
            header.isOpaque = false
            header.add(label("MORPHE", monoFont(17f, bold = true, tracking = 0.22f), TEXT))
            header.add(label("SETTING UP // FETCHING COMPONENTS", monoFont(10.5f, bold = false, tracking = 0.14f), MUTED))

            val progressBar = NeonBar()
            val st = label(" ", monoFont(10.5f, bold = false, tracking = 0.1f), MUTED)

            root.add(header, BorderLayout.NORTH)
            root.add(progressBar, BorderLayout.CENTER)
            root.add(st, BorderLayout.SOUTH)

            f.contentPane = root
            f.pack()
            f.size = Dimension(420, f.height)
            f.setLocationRelativeTo(null)
            f.isVisible = true

            frame = f
            bar = progressBar
            status = st
        }
    }

    override fun onProgress(index: Int, componentCount: Int, displayName: String, bytesDownloaded: Long, totalBytes: Long) {
        val fraction = if (totalBytes > 0) {
            (index + bytesDownloaded.toDouble() / totalBytes) / componentCount
        } else {
            index.toDouble() / componentCount
        }
        SwingUtilities.invokeLater {
            bar?.let {
                it.indeterminate = totalBytes <= 0
                it.progress = fraction
            }
            status?.text = buildString {
                append("COMPONENT ${index + 1}/$componentCount")
                if (totalBytes > 0) append("   ${mb(bytesDownloaded)} / ${mb(totalBytes)} MB")
            }
        }
    }

    override fun onComplete() {
        val b = bar ?: return
        SwingUtilities.invokeLater { b.progress = 1.0 }
    }

    override fun onError(message: String) {
        val f = frame ?: return
        // Swap the window into an on-brand error state and block until the user
        // acknowledges, so the launcher can then exit.
        val acknowledged = CountDownLatch(1)
        SwingUtilities.invokeLater {
            f.isAlwaysOnTop = false
            val root = JPanel(BorderLayout(0, 16))
            root.background = VOID
            root.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ERROR, 1),
                BorderFactory.createEmptyBorder(24, 28, 24, 28),
            )
            val header = JPanel(GridLayout(0, 1, 0, 7))
            header.isOpaque = false
            header.add(label("SETUP FAILED", monoFont(15f, bold = true, tracking = 0.2f), ERROR))
            header.add(label(message, monoFont(10.5f, bold = false, tracking = 0.08f), MUTED))
            header.add(label("Check your connection and try again.", monoFont(10.5f, bold = false, tracking = 0.08f), MUTED))

            val quit = label("[ QUIT ]", monoFont(11f, bold = true, tracking = 0.18f), ERROR)
            quit.horizontalAlignment = SwingConstants.CENTER
            quit.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ERROR, 1),
                BorderFactory.createEmptyBorder(9, 16, 9, 16),
            )
            quit.cursor = Cursor(Cursor.HAND_CURSOR)
            quit.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = acknowledged.countDown()
            })
            val south = JPanel()
            south.isOpaque = false
            south.add(quit)

            root.add(header, BorderLayout.NORTH)
            root.add(south, BorderLayout.SOUTH)
            f.contentPane = root
            f.revalidate()
            f.repaint()
        }
        acknowledged.await()
        SwingUtilities.invokeLater { f.dispose() }
        frame = null
    }

    fun close() {
        val f = frame ?: return
        frame = null
        SwingUtilities.invokeLater { f.dispose() }
    }

    // --- Styling helpers -------------------------

    private fun label(text: String, font: Font, color: Color): JLabel =
        JLabel(text).apply {
            this.font = font
            foreground = color
        }

    private fun monoFont(size: Float, bold: Boolean, tracking: Float): Font {
        val base = if (bold) monoBold else monoRegular
        val attrs = HashMap<TextAttribute, Any>()
        attrs[TextAttribute.SIZE] = size
        attrs[TextAttribute.TRACKING] = tracking
        return base.deriveFont(attrs)
    }

    private fun mb(bytes: Long): String = "%.1f".format(bytes / 1_048_576.0)

    private class NeonBar : JComponent() {
        var progress: Double = 0.0
            set(value) {
                field = value.coerceIn(0.0, 1.0)
                repaint()
            }
        var indeterminate: Boolean = false
            set(value) {
                field = value
                repaint()
            }

        override fun getPreferredSize(): Dimension = Dimension(360, 6)

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val w = width
            val h = height
            g2.color = TRACK
            g2.fillRect(0, 0, w, h)
            g2.color = NEON
            if (indeterminate) {
                g2.color = Color(NEON.red, NEON.green, NEON.blue, 120)
                g2.fillRect(0, 0, w, h)
            } else {
                g2.fillRect(0, 0, (w * progress).toInt(), h)
            }
        }
    }

}
