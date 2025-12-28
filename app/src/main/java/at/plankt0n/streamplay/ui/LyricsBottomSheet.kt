package at.plankt0n.streamplay.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.helper.LyricsHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LyricsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "LyricsBottomSheet"
        private const val ARG_ARTIST = "artist"
        private const val ARG_TITLE = "title"

        fun newInstance(artist: String, title: String): LyricsBottomSheet {
            return LyricsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ARTIST, artist)
                    putString(ARG_TITLE, title)
                }
            }
        }
    }

    private var artist: String = ""
    private var title: String = ""
    private var fetchJob: Job? = null

    private lateinit var trackTitleView: TextView
    private lateinit var artistView: TextView
    private lateinit var loadingView: ProgressBar
    private lateinit var messageView: TextView
    private lateinit var lyricsCard: CardView
    private lateinit var lyricsScroll: ScrollView
    private lateinit var lyricsText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            artist = it.getString(ARG_ARTIST, "")
            title = it.getString(ARG_TITLE, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_lyrics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        trackTitleView = view.findViewById(R.id.lyrics_track_title)
        artistView = view.findViewById(R.id.lyrics_artist)
        loadingView = view.findViewById(R.id.lyrics_loading)
        messageView = view.findViewById(R.id.lyrics_message)
        lyricsCard = view.findViewById(R.id.lyrics_card)
        lyricsScroll = view.findViewById(R.id.lyrics_scroll)
        lyricsText = view.findViewById(R.id.lyrics_text)

        // Set track info
        if (title.isNotBlank()) {
            trackTitleView.text = title
        }
        if (artist.isNotBlank()) {
            artistView.text = artist
            artistView.visibility = View.VISIBLE
        }

        // Check if we have valid track info
        if (artist.isBlank() && title.isBlank()) {
            showMessage(getString(R.string.lyrics_no_track))
            return
        }

        // Fetch lyrics
        fetchLyrics()
    }

    private fun fetchLyrics() {
        showLoading()

        fetchJob = lifecycleScope.launch {
            val result = LyricsHelper.fetchLyrics(artist, title)

            when {
                result.error != null && !result.hasAnyLyrics -> {
                    showMessage(result.error)
                }
                result.instrumental -> {
                    showMessage(getString(R.string.lyrics_instrumental))
                }
                result.hasSyncedLyrics -> {
                    val formatted = formatSyncedLyrics(result.syncedLyrics!!)
                    when {
                        formatted.isNotBlank() -> showLyrics(formatted)
                        result.hasPlainLyrics -> showLyrics(result.plainLyrics!!)
                        else -> showMessage(getString(R.string.lyrics_not_found))
                    }
                }
                result.hasPlainLyrics -> {
                    val lyrics = result.plainLyrics!!
                    if (lyrics.isNotBlank()) {
                        showLyrics(lyrics)
                    } else {
                        showMessage(getString(R.string.lyrics_not_found))
                    }
                }
                else -> {
                    showMessage(getString(R.string.lyrics_not_found))
                }
            }
        }
    }

    private fun formatSyncedLyrics(lrcText: String): String {
        // Parse LRC and return just the text without timestamps
        val lines = LyricsHelper.parseLrcLyrics(lrcText)
        return lines.joinToString("\n") { it.text }
    }

    private fun showLoading() {
        loadingView.visibility = View.VISIBLE
        messageView.visibility = View.GONE
        lyricsCard.visibility = View.GONE
    }

    private fun showMessage(message: String) {
        loadingView.visibility = View.GONE
        messageView.visibility = View.VISIBLE
        messageView.text = message
        lyricsCard.visibility = View.GONE
    }

    private fun showLyrics(lyrics: String) {
        loadingView.visibility = View.GONE
        messageView.visibility = View.GONE
        lyricsCard.visibility = View.VISIBLE
        lyricsText.text = lyrics
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        fetchJob?.cancel()
        fetchJob = null
        super.onDismiss(dialog)
    }
}
