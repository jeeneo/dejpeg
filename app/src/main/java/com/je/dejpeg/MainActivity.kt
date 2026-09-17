/*
 * SPDX-FileCopyrightText: 2025 - 2026 dryerlint <https://codeberg.org/dryerlint>
 * SPDX-License-Identifier: GNU Affero General Public License v3.0 or later
 */

package com.je.dejpeg

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.je.dejpeg.data.AppPreferences
import com.je.dejpeg.data.AppState
import com.je.dejpeg.data.HapticPatterns
import com.je.dejpeg.data.ImageRepository
import com.je.dejpeg.ui.components.ActivitySnackySnackbarController
import com.je.dejpeg.ui.components.RecoveryDialog
import com.je.dejpeg.ui.components.SnackBarBox
import com.je.dejpeg.ui.components.SnackbarController
import com.je.dejpeg.ui.components.SnackySnackbarHostState
import com.je.dejpeg.ui.screens.BRISQUEScreen
import com.je.dejpeg.ui.screens.ImageScreen
import com.je.dejpeg.ui.screens.ProcessingScreen
import com.je.dejpeg.ui.theme.AppTheme
import com.je.dejpeg.ui.viewmodel.ProcessingViewModel
import com.je.dejpeg.ui.viewmodel.SettingsViewModel
import com.je.dejpeg.utils.ModelManager

class App : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var ctx: Context
            private set

        val prefs: AppPreferences by lazy {
            val prefs = AppPreferences()
            HapticPatterns.appHapticsEnabled = prefs.loadHapticFeedbackEnabled()
            prefs
        }

        val state: AppState by lazy {
            AppState(prefs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
    }
}

class MainActivity : ComponentActivity() {
    private var handledIntentHash: Int? = null
    private val imageRepository by lazy { ImageRepository.getInstance() }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        @Suppress("ControlFlowWithEmptyBody") if (isGranted) {
            // granted
        } else {
            // denied - nothing shown cause its annoying
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val modelManager = ModelManager.create(this)
        modelManager.initializeStarterModel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        handleShareIntent(intent)

        // https://stackoverflow.com/a/79267436
        setContent {
            AppTheme {
                ScreenController()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val hash = System.identityHashCode(intent)
        if (hash == handledIntentHash) return
        handledIntentHash = hash
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { addSharedUri(it) }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        ?: emptyList()
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM) ?: emptyList()
                }
                uris.forEach { addSharedUri(it) }
            }
        }
    }

    private fun addSharedUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION and Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        imageRepository.addSharedUri(uri)
    }
}

@Composable
fun ScreenController() {
    val viewModel: ProcessingViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val imageRepository = remember { ImageRepository.getInstance() }
    val sharedUris by imageRepository.sharedUris.collectAsState()
    val snackbarHostState = remember { SnackySnackbarHostState() }
    val snackbarController = remember { ActivitySnackySnackbarController() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, snackbarController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                SnackbarController.bind(snackbarController)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        SnackbarController.bind(snackbarController)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SnackbarController.unbind(snackbarController)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.imageRepository = imageRepository
        viewModel.settingsViewModel = settingsViewModel
        settingsViewModel.initialize()
    }
    val context = App.ctx
    RecoveryDialog(imageRepository = imageRepository)
    SnackBarBox(snackbarHostState = snackbarHostState, controller = snackbarController) {
        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top)
        ) { paddingValues ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                ProcessingScreen(
                    processingViewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    imageRepository = imageRepository,
                    onNavigateToBeforeAfter = { id ->
                        context.startActivity(
                            Intent(context, BeforeAfterActivity::class.java).putExtra(
                                "imageId", id
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onNavigateToBrisque = { id ->
                        context.startActivity(
                            Intent(context, BrisqueActivity::class.java).putExtra("imageId", id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onNavigateToCompare = { idA, idB ->
                        context.startActivity(
                            Intent(context, CompareActivity::class.java).putExtra(
                                "imageIdA", idA
                            ).putExtra("imageIdB", idB).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    isActive = true,
                    initialSharedUris = sharedUris,
                    onRemoveSharedUri = { })
            }
        }
    }
}

private const val IMAGE_ID = "imageId"

@Composable
private fun ActivityContent(
    screen: @Composable (imageRepository: ImageRepository) -> Unit
) {
    AppTheme {
        val imageRepository = remember { ImageRepository.getInstance() }
        val snackbarHostState = remember { SnackySnackbarHostState() }
        val snackbarController = remember { ActivitySnackySnackbarController() }
        DisposableEffect(snackbarController) {
            SnackbarController.bind(snackbarController)
            onDispose {
                SnackbarController.unbind(
                    snackbarController
                )
            }
        }
        SnackBarBox(
            snackbarHostState = snackbarHostState, controller = snackbarController
        ) {
            screen(imageRepository)
        }
    }
}

class BrisqueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageId = intent.getStringExtra(IMAGE_ID) ?: return finish()
        setContent {
            ActivityContent { imageRepository ->
                BRISQUEScreen(
                    imageRepository = imageRepository, imageId = imageId, onBack = { finish() })
            }
        }
    }
}

class BeforeAfterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageId = intent.getStringExtra(IMAGE_ID) ?: return finish()
        setContent {
            ActivityContent { imageRepository ->
                val viewModel: ProcessingViewModel = viewModel()
                LaunchedEffect(Unit) {
                    viewModel.imageRepository = imageRepository
                    viewModel.initialize(this@BeforeAfterActivity)
                }
                ImageScreen(
                    viewModel = viewModel,
                    imageRepository = imageRepository,
                    imageId = imageId,
                    onBack = { finish() })
            }
        }
    }
}

class CompareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageIdA = intent.getStringExtra("imageIdA") ?: return finish()
        val imageIdB = intent.getStringExtra("imageIdB") ?: return finish()
        setContent {
            ActivityContent { imageRepository ->
                val viewModel: ProcessingViewModel = viewModel()
                LaunchedEffect(Unit) {
                    viewModel.imageRepository = imageRepository
                    viewModel.initialize(this@CompareActivity)
                }
                ImageScreen(
                    viewModel = viewModel,
                    imageRepository = imageRepository,
                    imageId = imageIdA,
                    compareImageId = imageIdB,
                    onBack = { finish() })
            }
        }
    }
}
