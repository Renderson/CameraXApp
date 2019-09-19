package com.rendersoncs.cameraxapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

// Este é um número arbitrário que estamos usando para manter a guia da permissão
// solicitação. Onde um aplicativo possui vários contextos para solicitar permissão,
// isso pode ajudar a diferenciar os diferentes contextos
private const val REQUEST_CODE_PERMISSIONS = 10

// Esta é uma matriz de toda a permissão especificada no manifesto
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

class MainActivity : AppCompatActivity(), LifecycleOwner {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.view_finder)

        // Solicitar permissões da câmera
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Sempre que a visualização da textura fornecida for alterada, recalcula o layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    private lateinit var viewFinder: TextureView

    private fun startCamera() {

        // Criar objeto de configuração para o caso de uso do visor
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(Rational(1, 1))
            setTargetResolution(Size(640, 640))
        }.build()

        // Construa o caso de uso do visor
        val preview = Preview(previewConfig)

        // Sempre que o visor é atualizado, recalcula o layout
        preview.setOnPreviewOutputUpdateListener {

            // Para atualizar o SurfaceTexture, precisamos removê-lo e adicioná-lo novamente
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // Criar objeto de configuração para o caso de uso de captura de imagem
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                setTargetAspectRatio(Rational(1, 1))
                // Não definimos uma resolução para captura de imagem; em vez disso, nós
                // seleciona um modo de captura que infere o apropriado
                // resolução baseada na proporção e no modo solicitado
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            }.build()

        // Construa o caso de uso de captura de imagem e conecte o ouvinte de clique no botão
        val imageCapture = ImageCapture(imageCaptureConfig)
        findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
            val file = File(
                externalMediaDirs.first(),
                "${System.currentTimeMillis()}.jpg"
            )
            imageCapture.takePicture(file,
                object : ImageCapture.OnImageSavedListener {
                    override fun onError(
                        imageCaptureError: ImageCapture.ImageCaptureError,
                        message: String,
                        cause: Throwable?
                    ) {
                        val msg = "Photo capture failed: $message"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        Log.e("CameraXApp", msg)
                    }

                    override fun onImageSaved(file: File) {
                        val msg = "Photo capture succeeded: ${file.absolutePath}"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        Log.d("CameraXApp", msg)
                    }
                })
        }

        // Configurar o pipeline de análise de imagem que calcula a luminância média de pixels
        val analysisConfig = ImageAnalysisConfig.Builder().apply {
            // Use um thread de trabalho para análise de imagem para evitar falhas
            val analyzerThread = HandlerThread(
                "LuminosityAnalysis").apply { start() }
            setCallbackHandler(Handler(analyzerThread.looper))
            // Em nossa análise, nos preocupamos mais com a imagem mais recente do que
            // analisando * toda * imagem
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()

        // Construa o caso de uso da análise de imagem e instancie nosso analisador
        val analyzerUseCase = ImageAnalysis(analysisConfig).apply {
            analyzer = LuminosityAnalyzer()
        }

        // Vincular casos de uso ao ciclo de vida
        // Se o Android Studio reclamar que "this" não é um LifecycleOwner
        // tente reconstruir o projeto ou atualizar a dependência do appcompat para
        // versão 1.1.0 ou superior.
        CameraX.bindToLifecycle(this, preview, imageCapture, analyzerUseCase)
    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Calcula o centro do visor
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Saída correta da visualização para levar em consideração a rotação da exibição
        val rotationDegress = when (viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }

        matrix.postRotate(-rotationDegress.toFloat(), centerX, centerY)

        // Finalmente, aplique transformações ao nosso TextureView
        viewFinder.setTransform(matrix)

    }


    /* 
    * O resultado do processo da caixa de diálogo de solicitação de permissão tem a solicitação
    * foi concedido? Se sim, inicie a câmera. Caso contrário, exibir uma torrada*/
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera() }
            } else {
                Toast.makeText(
                    this,
                    "Permission not granded by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    /**
     * Verifique se todas as permissões especificadas no manifesto foram concedidas
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }
}
