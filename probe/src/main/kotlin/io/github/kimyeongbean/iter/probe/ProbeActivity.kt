package io.github.kimyeongbean.iter.probe

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Buttons for permissions and the service, and the log, refreshed while open. */
class ProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView

    private val refresh =
        object : Runnable {
            override fun run() {
                show()
                handler.postDelayed(this, 2_000L)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProbeLog.write(this, "ACTIVITY_OPEN")
        val pad = (16 * resources.displayMetrics.density).toInt()

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(
            label: String,
            action: () -> Unit,
        ) = buttons.addView(
            Button(this).apply {
                text = label
                setOnClickListener {
                    action()
                    show()
                }
            },
        )
        button("1. 권한") { requestPermissions(foregroundPermissions(), 1) }
        button("2. 백그라운드 위치") { requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2) }
        button("3. 서비스 시작") { ProbeService.start(this, from = "activity") }
        button("서비스 중지") { ProbeService.stop(this) }
        button("로그 지우기") { ProbeLog.clear(this) }

        log =
            TextView(this).apply {
                typeface = Typeface.MONOSPACE
                textSize = 12f
                setTextColor(Color.WHITE)
                setTextIsSelectable(true)
            }
        scroll = ScrollView(this).apply { addView(log) }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(20, 24, 22))
                setPadding(pad, pad, pad, pad)
                addView(
                    TextView(this@ProbeActivity).apply {
                        text = "#124 실차 확인: 1→2→3 순서로 누른 뒤 시동을 끄고 켜거나 앱을 최근 앱에서 밀어 닫아 보세요. 로그는 logcat 태그 IterProbe에도 남습니다."
                        setTextColor(Color.LTGRAY)
                    },
                )
                addView(HorizontalScrollView(this@ProbeActivity).apply { addView(buttons) }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            },
        )
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        val result = permissions.zip(grantResults.toTypedArray()).joinToString { (p, g) -> "${p.substringAfterLast('.')}=${g == 0}" }
        ProbeLog.write(this, "PERMISSION_RESULT $result")
        show()
    }

    private fun foregroundPermissions() = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add("android.car.permission.CAR_SPEED")
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun show() {
        log.text = ProbeLog.tail(this)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
