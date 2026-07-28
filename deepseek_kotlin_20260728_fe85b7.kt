package com.example.floatingimage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class FloatingWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var isLocked = false
    private var baseW = 0
    private var baseH = 0
    private var scaleFactor = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action") ?: "show"
        when (action) {
            "show" -> {
                val path = intent.getStringExtra("image_path") ?: return START_NOT_STICKY
                showFloatingWindow(path)
            }
            "unlock" -> unlock()
            "close" -> removeWindow()
        }
        return START_STICKY
    }

    private fun showFloatingWindow(imagePath: String) {
        floatView?.let { windowManager.removeView(it) }

        val bitmap = BitmapFactory.decodeFile(imagePath)
        val inflater = LayoutInflater.from(this)
        floatView = inflater.inflate(R.layout.float_window_layout, null)
        val imageView = floatView!!.findViewById<ImageView>(R.id.float_image)
        val controlLayout = floatView!!.findViewById<LinearLayout>(R.id.control_layout)
        val seekBar = floatView!!.findViewById<SeekBar>(R.id.seek_bar)
        val btnLock = floatView!!.findViewById<Button>(R.id.btn_lock)

        imageView.setImageBitmap(bitmap)
        val density = resources.displayMetrics.density
        baseW = (250 * density).toInt()
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        baseH = (baseW / ratio).toInt()
        imageView.layoutParams = FrameLayout.LayoutParams(baseW, baseH)
        imageView.scaleType = ImageView.ScaleType.FIT_XY
        scaleFactor = 1f

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 3f)
                val newW = (baseW * scaleFactor).toInt()
                val newH = (baseH * scaleFactor).toInt()
                imageView.layoutParams.width = newW
                imageView.layoutParams.height = newH
                imageView.requestLayout()
                windowManager.updateViewLayout(floatView, params)
                return true
            }
        })

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        imageView.setOnTouchListener { _, event ->
            if (isLocked) return@setOnTouchListener false
            scaleDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scaleDetector.isInProgress) return@setOnTouchListener true
                    params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatView, params)
                    true
                }
                else -> true
            }
        }

        seekBar.progress = 255
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                imageView.alpha = progress / 255f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnLock.setOnClickListener { lock() }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }
        windowManager.addView(floatView, params)
        isLocked = false
        controlLayout.visibility = View.VISIBLE

        startForeground(1, buildNotification())
    }

    private fun lock() {
        isLocked = true
        floatView?.findViewById<LinearLayout>(R.id.control_layout)?.visibility = View.GONE
        params?.flags = params!!.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        floatView?.let { windowManager.updateViewLayout(it, params) }
        Toast.makeText(this, "悬浮窗已锁定，点击穿透", Toast.LENGTH_SHORT).show()
    }

    private fun unlock() {
        isLocked = false
        floatView?.findViewById<LinearLayout>(R.id.control_layout)?.visibility = View.VISIBLE
        params?.flags = params!!.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        floatView?.let { windowManager.updateViewLayout(it, params) }
        Toast.makeText(this, "已解锁，可调整悬浮窗", Toast.LENGTH_SHORT).show()
    }

    private fun removeWindow() {
        floatView?.let { windowManager.removeView(it) }
        stopForeground(true)
        stopSelf()
    }

    private fun buildNotification() = NotificationCompat.Builder(this, "floating_channel")
        .setSmallIcon(android.R.drawable.ic_menu_gallery)
        .setContentTitle("悬浮窗运行中")
        .setContentText("点击返回控制")
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "floating_channel",
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        floatView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        super.onDestroy()
    }
}