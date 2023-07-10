package com.example.createrealtimetextinimagerecognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.createrealtimetextinimagerecognition.ui.theme.CreateRealTimeTextInImageRecognitionTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionsRequired
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CreateRealTimeTextInImageRecognitionTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val permissionState = rememberMultiplePermissionsState(
                        permissions = listOf(
                            Manifest.permission.CAMERA,
                        )
                    )
                    LaunchedEffect(Unit) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                    PermissionsRequired(
                        multiplePermissionsState = permissionState,
                        permissionsNotGrantedContent = {

                        },
                        permissionsNotAvailableContent = {

                        },
                    ) {

                    }

                    Greeting("Android")
                    SimpleCameraPreview()
                }
            }
        }
    }
}

@Composable
fun SimpleCameraPreview() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()

                    cameraProvider.unbindAll()

                    val imageAnalyzer by lazy {
                        ImageAnalysis.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    executor,
                                    TextReaderAnalyzer(::onTextFound)
                                )
                            }
                    }

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview, imageAnalyzer
                    )
                }, executor)
                previewView
            },
        )
        Box(
            modifier = Modifier
                .padding(16.dp)
                .size(344.dp, 226.dp)
                .border(4.dp, Color.Green, shape = RoundedCornerShape(16.dp))
                .background(Color.Transparent)

        ) {
            Box(modifier = Modifier
                .size(100.dp, 100.dp)
                .background(Color.Transparent)) {

            }

        }
    }


}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CreateRealTimeTextInImageRecognitionTheme {
        Greeting("Android")
    }
}

var count = 0
private fun onTextFound(foundText: List<String>) {
    count++
    Log.d("TTT", "We got new text $count: $foundText")
    var card: String? = null
    var date: String? = null
    foundText.onEach {
        if (isCardNumber(it)) {
            card = it
        }
        if (isExpiredDate(it)) {
            date = it
        }
    }
    if (card != null && date != null) {
        Log.d("TTT", "Card Number: $card")
        Log.d("TTT", "Card Expired date $date")
    }
}

private fun isExpiredDate(str: String): Boolean {
    val dateRegex = "^\\d{2}\\/\\d{2}\$"
    val p: Pattern = Pattern.compile(dateRegex)
    val m = p.matcher(str)
    return m.matches()
}

private fun isCardNumber(str: String): Boolean {
    val regex = "^[0-9]{16}$"
    val p: Pattern = Pattern.compile(regex)
    val m = p.matcher(str)
    return m.matches()
}

class TextReaderAnalyzer(
    private val textFoundListener: (List<String>) -> Unit
) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.image?.let { process(it, imageProxy) }
    }

    private fun process(image: Image, imageProxy: ImageProxy) {
        try {
            readTextFromImage(InputImage.fromMediaImage(image, 90), imageProxy)
        } catch (e: IOException) {
            Log.d("TTT", "Failed to load the image")
            e.printStackTrace()
        }
    }

    private fun readTextFromImage(fromMediaImage: InputImage, imageProxy: ImageProxy) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(fromMediaImage)
            .addOnSuccessListener { visionText ->
                processTextFromImage(visionText, imageProxy)
                imageProxy.close()
            }
            .addOnFailureListener { error ->
                Log.d("TTT", "Failed to process the image")
                error.printStackTrace()
                imageProxy.close()
            }
    }

    private fun processTextFromImage(visionText: Text, imageProxy: ImageProxy) {
        val blockList = ArrayList<String>()
        for (block in visionText.textBlocks) {
            // You can access whole block of text using block.text
            for (line in block.lines) {
                // You can access whole line of text using line.text
                val txt = StringBuffer()
                for (element in line.elements) {
                    txt.append(element.text)
                }
                blockList.add(txt.toString())
            }
        }
        if (blockList.isNotEmpty()) {
            textFoundListener(blockList)
        }
    }
}