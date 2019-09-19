package com.rendersoncs.cameraxapp

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class LuminosityAnalyzer : ImageAnalysis.Analyzer {
    private var lastAnalyzedTimestamp = 0L

    /**
     * Função de extensão auxiliar usada para extrair uma matriz de bytes de um
     * buffer do plano de imagem
     */

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind() // Rebobina o buffer para zero
        val data = ByteArray(remaining())
        get(data) // Copia o buffer em uma matriz de bytes
        return data // Retorna a matriz de bytes
    }

    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        val currentTimestamp = System.currentTimeMillis()
        // Calculate the average luma no more often than every second
        if (currentTimestamp - lastAnalyzedTimestamp >=
            TimeUnit.SECONDS.toMillis(1)) {
            // Como o formato no ImageAnalysis é YUV, image.planes [0]
            // contém o plano Y (luminância)
            val buffer = image.planes[0].buffer
            // Extrai dados da imagem do objeto de retorno de chamada
            val data = buffer.toByteArray()
            // Converte os dados em uma matriz de valores de pixel
            val pixels = data.map { it.toInt() and 0xFF }
            // Calcula a luminância média da imagem
            val luma = pixels.average()
            // Registra o novo valor luma
            Log.d("CameraXApp", "Average luminosity: $luma")
            // Atualizar registro de data e hora do último quadro analisado
            lastAnalyzedTimestamp = currentTimestamp
        }
    }
}