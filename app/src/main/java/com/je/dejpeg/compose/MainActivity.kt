package com.je.dejpeg

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.je.dejpeg.compose.utils.CacheManager
import com.je.dejpeg.ui.MainScreen
import com.je.dejpeg.ui.theme.DeJPEGTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val sharedUrisState = androidx.compose.runtime.mutableStateListOf<Uri>()
    private val hasRecoveryImagesState = androidx.compose.runtime.mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // granted
        } else {
            // denied - nothing shown cause its annoying 
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        hasRecoveryImagesState.value = CacheManager.getRecoveryImages(this).isNotEmpty()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        handleShareIntent(intent)

        // https://stackoverflow.com/a/79267436
        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            SideEffect {
                if (!isDarkTheme) {
                    val lightTransparentStyle = SystemBarStyle.light(
                        scrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT
                    )
                    enableEdgeToEdge(
                        statusBarStyle = lightTransparentStyle,
                        navigationBarStyle = lightTransparentStyle
                    )
                } else {
                    val darkTransparentStyle = SystemBarStyle.dark(
                        scrim = android.graphics.Color.TRANSPARENT
                    )
                    enableEdgeToEdge(
                        statusBarStyle = darkTransparentStyle,
                        navigationBarStyle = darkTransparentStyle
                    )
                }
            }
            DeJPEGTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(sharedUris = sharedUrisState, hasRecoveryImages = hasRecoveryImagesState.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { addSharedUri(it, intent.flags) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
                }
                uris.forEach { addSharedUri(it, intent.flags) }
            }
        }
    }

    private fun addSharedUri(uri: Uri, flags: Int) {
        if (sharedUrisState.any { it == uri }) return
        try {
            val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { /* o */ }
        sharedUrisState.add(uri)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        CacheManager.cleanEntireCacheSync(this)
    }
}
