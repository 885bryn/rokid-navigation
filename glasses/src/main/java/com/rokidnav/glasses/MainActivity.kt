package com.rokidnav.glasses

import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.*
import com.rokidnav.glasses.input.*
import com.rokidnav.glasses.ui.HudView
import kotlinx.coroutines.launch

class MainActivity:AppCompatActivity() {
    private lateinit var vm:HudViewModel
    private lateinit var hud:HudView
    private val keyReceiver=KeyReceiver { vm.onKey(it) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the glasses compositor choose its native orientation. Forced landscape rotates the HUD.
        if(BuildConfig.DEBUG && intent.getBooleanExtra("phonePreview",false))
            requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        WindowCompat.getInsetsController(window,window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        vm=ViewModelProvider(this)[HudViewModel::class.java]
        vm.initialize(intent.getBooleanExtra("demo",intent.hasCategory(android.content.Intent.CATEGORY_LAUNCHER)))
        if(BuildConfig.DEBUG && intent.getBooleanExtra("demo",false) && intent.getBooleanExtra("previewExpanded",false))vm.onKey(GlassesKey.TAP)
        hud=HudView(this)
        val container=FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val previewWidth=if(BuildConfig.DEBUG)intent.getIntExtra("previewWidth",0) else 0
        val previewHeight=if(BuildConfig.DEBUG)intent.getIntExtra("previewHeight",0) else 0
        container.addView(hud,FrameLayout.LayoutParams(if(previewWidth>0)previewWidth else -1,if(previewHeight>0)previewHeight else -1,Gravity.CENTER))
        setContentView(container)
        ContextCompat.registerReceiver(this,keyReceiver,IntentFilter().apply {KeyType.entries.forEach { addAction(it.action) }},ContextCompat.RECEIVER_EXPORTED)
        onBackPressedDispatcher.addCallback(this){vm.onKey(GlassesKey.BACK)}
        val gestures=GestureDetector(this,object:GestureDetector.SimpleOnGestureListener(){
            override fun onDown(e:MotionEvent)=true
            override fun onSingleTapConfirmed(e:MotionEvent):Boolean {vm.onKey(GlassesKey.TAP);return true}
            override fun onDoubleTap(e:MotionEvent):Boolean {vm.onKey(GlassesKey.DOUBLE_TAP);return true}
            override fun onLongPress(e:MotionEvent){vm.onKey(GlassesKey.LONG_PRESS)}
            override fun onFling(e1:MotionEvent?,e2:MotionEvent,velocityX:Float,velocityY:Float):Boolean {
                val start=e1 ?: return false
                if(kotlin.math.abs(e2.x-start.x)<hud.width*.1f)return false
                vm.onKey(if(e2.x<start.x)GlassesKey.SWIPE_FORWARD else GlassesKey.SWIPE_BACK);return true
            }
        })
        hud.setOnTouchListener { _,event -> gestures.onTouchEvent(event) }
        hud.post {vm.onDisplayReady(hud.width,hud.height)}
        lifecycleScope.launch {repeatOnLifecycle(Lifecycle.State.STARTED){vm.store.state.collect(hud::render)}}
        if(BuildConfig.DEBUG && intent.getBooleanExtra("capture",false))hud.postDelayed({
            hud.render(vm.store.state.value)
            val bitmap=Bitmap.createBitmap(hud.width,hud.height,Bitmap.Config.ARGB_8888)
            hud.draw(Canvas(bitmap))
            java.io.File(filesDir,"hud-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        },intent.getIntExtra("captureDelayMs",8000).toLong())
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent?):Boolean {
        android.util.Log.d("RokidNavKeys","keyCode=$keyCode")
        val key=GlassesKey.fromKeyCode(keyCode) ?: return super.onKeyDown(keyCode,event)
        vm.onKey(key);return true
    }
    override fun onDestroy(){unregisterReceiver(keyReceiver);super.onDestroy()}
}
