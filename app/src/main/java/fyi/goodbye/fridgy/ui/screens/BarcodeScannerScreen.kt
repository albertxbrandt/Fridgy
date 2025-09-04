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
import java.util.concurrent.Executors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onBarcodeScanned: (String) -> Unit, // Callback when a barcode is found
    onBackClick: () -> Unit // Callback to go back
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // State to hold the last scanned barcode, used to prevent continuous scanning
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }

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
        containerColor = Color.Black // Camera preview background
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
                    val cameraExecutor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        // ML Kit Barcode Scanner options (only scan UPC-A and EAN-13)
                        val options = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_UPC_A, Barcode.FORMAT_EAN_13)
                            .build()
                        val barcodeScanner = BarcodeScanning.getClient(options)

                        val imageAnalyzer = ImageAnalysis.Builder()
                            // Use STRATEGY_BLOCK_PRODUCER as STRATEGY_KEEP_LATEST is deprecated/removed
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER) // <--- CORRECTED LINE
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(barcodeScanner) { barcodeValue ->
                                    if (lastScannedBarcode == null || lastScannedBarcode != barcodeValue) {
                                        lastScannedBarcode = barcodeValue // Store to prevent duplicates
                                        Log.d("BarcodeScanner", "Scanned: $barcodeValue")
                                        onBarcodeScanned(barcodeValue) // Trigger callback
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

            // Optional: Overlay for scanning area guidance
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                // A semi-transparent overlay to highlight the scanning area
                // Can be refined with a custom shape or graphic
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

// ImageAnalysis.Analyzer implementation for ML Kit
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
                    if (barcodes.isNotEmpty()) {
                        barcodes.firstOrNull()?.rawValue?.let { barcodeValue ->
                            onBarcodeScanned(barcodeValue)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeAnalyzer", "Barcode scanning failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close() // Always close the image proxy!
                }
        } else {
            imageProxy.close() // Close even if mediaImage is null
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