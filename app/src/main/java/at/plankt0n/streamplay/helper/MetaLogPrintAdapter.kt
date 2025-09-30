package at.plankt0n.streamplay.helper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.data.MetaLogEntry
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MetaLogPrintAdapter(
    private val context: Context,
    private val logs: List<MetaLogEntry>
) : PrintDocumentAdapter() {

    private lateinit var pdfDocument: PrintedPdfDocument
    private var pageWidth: Int = 0
    private var pageHeight: Int = 0
    private var contentWidth: Int = 0
    private var headerHeight: Float = 0f
    private var footerHeight: Float = 0f
    private var headerBottom: Float = 0f
    private var contentBottom: Float = 0f
    private var totalPages: Int = 0

    private val generatedAt = Date()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
    }
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f
        textAlign = Paint.Align.RIGHT
    }

    private val lineSpacing = 4f
    private val entrySpacing = 12f
    private val headerSpacing = 6f
    private val headerBottomSpacing = 12f
    private val horizontalMargin = 48f
    private val topMargin = 48f
    private val bottomMargin = 48f

    private val headerTitle: String by lazy { context.getString(R.string.print_logs_header) }
    private val headerSubtitle: String by lazy {
        context.getString(R.string.print_logs_subtitle, dateFormatter.format(generatedAt))
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: android.os.CancellationSignal?,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        if (logs.isEmpty()) {
            callback.onLayoutFailed(context.getString(R.string.print_logs_empty))
            return
        }

        pdfDocument = PrintedPdfDocument(context, newAttributes)
        computeLayoutMeasurements(newAttributes)
        totalPages = calculatePageCount()

        if (totalPages <= 0) {
            pdfDocument.close()
            callback.onLayoutFailed(context.getString(R.string.print_logs_empty))
            return
        }

        val info = PrintDocumentInfo.Builder("metalog.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(totalPages)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pageRanges: Array<out android.print.PageRange>,
        destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            var currentEntryIndex = 0
            var pageNumber = 0

            while (currentEntryIndex < logs.size) {
                if (cancellationSignal?.isCanceled == true) {
                    pdfDocument.close()
                    callback.onWriteCancelled()
                    return
                }

                val page = pdfDocument.startPage(pageNumber)
                val nextIndex = drawPage(page.canvas, page.info, pageNumber, currentEntryIndex)
                currentEntryIndex = nextIndex
                pdfDocument.finishPage(page)
                pageNumber++
            }

            FileOutputStream(destination.fileDescriptor).use { output ->
                pdfDocument.writeTo(output)
            }
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (ioe: IOException) {
            callback.onWriteFailed(ioe.message)
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawPage(
        canvas: Canvas,
        pageInfo: android.graphics.pdf.PdfDocument.PageInfo,
        pageNumber: Int,
        startEntryIndex: Int
    ): Int {
        val startX = horizontalMargin
        val headerOffset = drawHeader(canvas, startX, topMargin)
        var cursorY = topMargin + headerOffset
        val maxContentY = pageInfo.pageHeight - bottomMargin - footerHeight
        var currentIndex = startEntryIndex

        while (currentIndex < logs.size) {
            val entryHeight = measureEntryHeight(logs[currentIndex])
            val isFirstOnPage = cursorY <= headerBottom + 0.5f
            if (cursorY + entryHeight > maxContentY && !isFirstOnPage) {
                break
            }
            val consumedHeight = drawEntry(canvas, logs[currentIndex], startX, cursorY)
            cursorY += consumedHeight
            currentIndex++
            if (cursorY > maxContentY) {
                break
            }
        }

        drawFooter(canvas, pageInfo, pageNumber)
        return currentIndex
    }

    private fun computeLayoutMeasurements(attributes: PrintAttributes) {
        val mediaSize = attributes.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
        pageWidth = (mediaSize.widthMils / 1000f * 72f).roundToInt()
        pageHeight = (mediaSize.heightMils / 1000f * 72f).roundToInt()
        contentWidth = (pageWidth - 2 * horizontalMargin).roundToInt()
        headerHeight = measureHeaderHeight()
        footerHeight = footerPaint.fontSpacing
        headerBottom = topMargin + headerHeight
        contentBottom = pageHeight - bottomMargin - footerHeight
    }

    private fun calculatePageCount(): Int {
        var pages = 1
        var cursorY = headerBottom
        val availableHeight = contentBottom

        logs.forEach { entry ->
            val entryHeight = measureEntryHeight(entry)
            val isFirstOnPage = cursorY <= headerBottom + 0.5f
            if (cursorY + entryHeight > availableHeight && !isFirstOnPage) {
                pages++
                cursorY = headerBottom
            }
            cursorY += entryHeight
        }

        return pages
    }

    private fun measureHeaderHeight(): Float {
        var height = 0f
        height += measureTextBlock(headerTitle, titlePaint)
        height += headerSpacing
        height += measureTextBlock(headerSubtitle, subtitlePaint)
        height += headerBottomSpacing
        return height
    }

    private fun drawHeader(canvas: Canvas, startX: Float, startY: Float): Float {
        var cursorY = startY
        cursorY += drawTextBlock(headerTitle, canvas, startX, cursorY, titlePaint)
        cursorY += headerSpacing
        cursorY += drawTextBlock(headerSubtitle, canvas, startX, cursorY, subtitlePaint)
        cursorY += headerBottomSpacing
        return cursorY - startY
    }

    private fun drawFooter(canvas: Canvas, pageInfo: android.graphics.pdf.PdfDocument.PageInfo, pageNumber: Int) {
        val footerText = context.getString(R.string.print_logs_page_footer, pageNumber + 1, totalPages)
        val baseline = pageInfo.pageHeight - bottomMargin - footerPaint.descent()
        canvas.drawText(footerText, pageInfo.pageWidth - horizontalMargin, baseline, footerPaint)
    }

    private fun measureEntryHeight(entry: MetaLogEntry): Float {
        var height = 0f
        height += lineSpacing
        height += measureTextBlock(context.getString(R.string.print_logs_time_label, entry.formattedTime()), bodyPaint)
        height += lineSpacing
        height += measureTextBlock(context.getString(R.string.print_logs_station_label, entry.station), bodyPaint)
        height += lineSpacing
        val track = if (entry.artist.isBlank()) entry.title else "${entry.title} – ${entry.artist}"
        height += measureTextBlock(context.getString(R.string.print_logs_track_label, track), bodyPaint)
        if (entry.manual) {
            height += lineSpacing
            height += measureTextBlock(context.getString(R.string.print_logs_manual_flag), bodyPaint)
        }
        height += entrySpacing
        return height
    }

    private fun drawEntry(canvas: Canvas, entry: MetaLogEntry, startX: Float, startY: Float): Float {
        var cursorY = startY
        cursorY += lineSpacing
        cursorY += drawTextBlock(
            context.getString(R.string.print_logs_time_label, entry.formattedTime()),
            canvas,
            startX,
            cursorY,
            bodyPaint
        )
        cursorY += lineSpacing
        cursorY += drawTextBlock(
            context.getString(R.string.print_logs_station_label, entry.station),
            canvas,
            startX,
            cursorY,
            bodyPaint
        )
        cursorY += lineSpacing
        val track = if (entry.artist.isBlank()) entry.title else "${entry.title} – ${entry.artist}"
        cursorY += drawTextBlock(
            context.getString(R.string.print_logs_track_label, track),
            canvas,
            startX,
            cursorY,
            bodyPaint
        )
        if (entry.manual) {
            cursorY += lineSpacing
            cursorY += drawTextBlock(
                context.getString(R.string.print_logs_manual_flag),
                canvas,
                startX,
                cursorY,
                bodyPaint
            )
        }
        cursorY += entrySpacing
        return cursorY - startY
    }

    private fun measureTextBlock(text: String, paint: TextPaint): Float {
        if (text.isBlank()) return 0f
        return buildStaticLayout(text, paint).height.toFloat()
    }

    private fun drawTextBlock(
        text: String,
        canvas: Canvas,
        startX: Float,
        startY: Float,
        paint: TextPaint
    ): Float {
        if (text.isBlank()) return 0f
        val layout = buildStaticLayout(text, paint)
        canvas.save()
        canvas.translate(startX, startY)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun buildStaticLayout(text: String, paint: TextPaint): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, contentWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }
    }
}
