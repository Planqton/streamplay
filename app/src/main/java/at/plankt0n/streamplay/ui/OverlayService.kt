package at.plankt0n.streamplay.ui

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageButton
import android.widget.PopupWindow
import at.plankt0n.streamplay.R
import at.plankt0n.streamplay.helper.MediaServiceController
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayButton: View
    private var params: WindowManager.LayoutParams? = null
    private var menu: PopupWindow? = null
    private lateinit var mediaController: MediaServiceController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mediaController = MediaServiceController(this)
        mediaController.initializeAndConnect(
            onConnected = {},
            onPlaybackChanged = {},
            onStreamIndexChanged = {},
            onMetadataChanged = {},
            onTimelineChanged = {}
        )

        val inflater = LayoutInflater.from(this)
        overlayButton = inflater.inflate(R.layout.overlay_button, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        setTouchListener()
        windowManager.addView(overlayButton, params)
    }

    private fun setTouchListener() {
        overlayButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params!!.x = initialX + dx
                        params!!.y = initialY + dy
                        windowManager.updateViewLayout(overlayButton, params)
                        if (abs(dx) > 10 || abs(dy) > 10) isClick = false
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleMenu()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun toggleMenu() {
        if (menu?.isShowing == true) {
            menu?.dismiss()
            menu = null
        } else {
            val menuView = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null)
            menuView.findViewById<ImageButton>(R.id.btn_prev).setOnClickListener {
                mediaController.skipToPrevious(); menu?.dismiss()
            }
            menuView.findViewById<ImageButton>(R.id.btn_play_pause).setOnClickListener {
                mediaController.togglePlayPause(); menu?.dismiss()
            }
            menuView.findViewById<ImageButton>(R.id.btn_next).setOnClickListener {
                mediaController.skipToNext(); menu?.dismiss()
            }

            menu = PopupWindow(
                menuView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            menu?.showAtLocation(
                overlayButton,
                Gravity.NO_GRAVITY,
                params!!.x,
                params!!.y + overlayButton.height
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        menu?.dismiss()
        if (::overlayButton.isInitialized) {
            windowManager.removeView(overlayButton)
        }
        mediaController.disconnect()
    }
}
