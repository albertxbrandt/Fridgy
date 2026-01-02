package fyi.goodbye.fridgy.ui.screens

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fyi.goodbye.fridgy.ui.theme.FridgyDarkBlue
import fyi.goodbye.fridgy.ui.theme.FridgyTheme
import fyi.goodbye.fridgy.ui.theme.FridgyWhite
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Screen that integrates CameraX and Google ML Kit to scan product barcodes.
 *
 * PERFORMANCE OPTIMIZATIONS:
 * 1. Used STRATEGY_KEEP_ONLY_LATEST to prevent camera preview lag.
 * 2. Implemented proper resource management (ExecutorService & BarcodeScanner closure).
 * 3. Added an atomic 'isScanningActive' flag to prevent multiple rapid scans of the same item.
 * 4. Memoized scanning resources to avoid re-allocation during recomposition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // OPTIMIZATION: Lifecycle-aware resource management
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_EAN_13)
            .build()
        BarcodeScanning.getClient(options)
    }

    // Flag to ensure we only process one barcode per session
    var isScanningActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan Barcode",
                        color = FridgyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = FridgyWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FridgyDarkBlue
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // OPTIMIZATION: STRATEGY_KEEP_ONLY_LATEST ensures the UI stays responsive
                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(barcodeScanner) { barcodeValue ->
                                    if (isScanningActive) {
                                        // Set flag to false immediately to block further analysis
                                        isScanningActive = false
                                        Log.d("Performance", "Barcode successfully captured: $barcodeValue")
                                        onBarcodeScanned(barcodeValue)
                                    }
                                })
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalyzer
                            )
                        } catch (exc: Exception) {
                            Log.e("BarcodeScanner", "Use case binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(200.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .wrapContentSize(Alignment.Center)
                ) {
                    Text(
                        text = "Align barcode within the box",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Custom [ImageAnalysis.Analyzer] that processes camera frames for barcodes using ML Kit.
 */
private class BarcodeAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val onBarcodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val firstBarcode = barcodes.firstOrNull()?.rawValue
                    if (firstBarcode != null) {
                        onBarcodeScanned(firstBarcode)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeAnalyzer", "Scanning error", e)
                }
                .addOnCompleteListener {
                    // CRITICAL: Close the ImageProxy to allow next frame processing
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PreviewBarcodeScannerScreen() {
    FridgyTheme {
        BarcodeScannerScreen(
            onBarcodeScanned = { barcode -> Log.d("Preview", "Scanned: $barcode") },
            onBackClick = { Log.d("Preview", "Back clicked") }
        )
    }
}
