package io.github.kazutoiris.aiwatermark

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresExtension
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : ComponentActivity() {

    @Composable
    fun AppTheme(
        isDarkTheme: Boolean = isSystemInDarkTheme(),
        isDynamicColor: Boolean = true,
        content: @Composable () -> Unit
    ) {
        val colorScheme = when {
            isDynamicColor && isDarkTheme -> {
                dynamicDarkColorScheme(LocalContext.current)
            }

            isDynamicColor && !isDarkTheme -> {
                dynamicLightColorScheme(LocalContext.current)
            }

            isDarkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }

        MaterialTheme(
            colorScheme = colorScheme, content = content
        )
    }

    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 2)
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                WatermarkApp()
            }
        }
    }
}


@RequiresExtension(extension = Build.VERSION_CODES.R, version = 2)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkApp() {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isProcessing = remember { false }
    val coroutineScope = rememberCoroutineScope()

    val watermarkBitmap = remember {
        try {
            val source =
                ImageDecoder.createSource(context.resources, R.drawable.doubao_ai_watermark)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                isProcessing = true

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        uris.forEachIndexed { index, uri ->
                            val source = ImageDecoder.createSource(context.contentResolver, uri)
                            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                            }

                            try {
                                val watermarked = addWatermark(bitmap, watermarkBitmap!!)
                                saveImage(watermarked, context)

                                withContext(Dispatchers.Main) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.processed,
                                            index + 1
                                        )
                                    )
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    R.string.exception,
                                    e.message
                                )
                            )
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                        }
                    }
                }
            }
        })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.TopStart
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ImageSelectorCard(icon = Icons.Default.AddCircle,
                        onClick = {
                            selectImageLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )

                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.processing))
                            }
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                ) {
                    Text(
                        text = "GitHub: kazutoiris",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "https://github.com/kazutoiris",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/kazutoiris")
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }
        })
}

@Composable
fun ImageSelectorCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.select_image),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

fun addWatermark(original: Bitmap, watermark: Bitmap): Bitmap {
    val result =
        Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).apply {
            density = Bitmap.DENSITY_NONE
        }
    val canvas = Canvas(result)
    canvas.drawBitmap(original, 0f, 0f, null)

    val scale = original.width / 864.0f
    val scaledWatermark = Bitmap.createScaledBitmap(
        watermark, (watermark.width * scale).toInt(), (watermark.height * scale).toInt(), true
    )

    val margin = 12
    val left = result.width - scaledWatermark.width - margin
    val top = result.height - scaledWatermark.height - margin
    canvas.drawBitmap(scaledWatermark, left.toFloat(), top.toFloat(), null)

    scaledWatermark.recycle()
    return result
}

fun saveImage(bitmap: Bitmap, context: android.content.Context): Uri? {
    val fileName = "${java.util.UUID.randomUUID()}.jpg"

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }

    val contentResolver = context.contentResolver
    val uri = contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
    )

    try {
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            return it
        }
    } catch (e: IOException) {
        e.printStackTrace()
        if (uri != null) {
            contentResolver.delete(uri, null, null)
        }
    }
    return null
}

@RequiresExtension(extension = Build.VERSION_CODES.R, version = 2)
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        WatermarkApp()
    }
}
