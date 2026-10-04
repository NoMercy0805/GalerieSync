package de.galeriesync.gallery

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
class GalleryVideoTranscoder(private val context:Context){
    companion object{
        private const val TARGET_HEIGHT=720
        private const val VIDEO_BITRATE=1_200_000
        private const val AUDIO_BITRATE=96_000
    }

    suspend fun transcode(input:File,output:File,onProgress:(Int)->Unit={}):Boolean = withContext(Dispatchers.Main.immediate){
        if(output.exists())output.delete()
        suspendCancellableCoroutine{continuation->
            val videoEffects=mutableListOf<Effect>()
            val height=runCatching{
                val retriever=MediaMetadataRetriever()
                try{
                    retriever.setDataSource(input.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?:0
                }finally{retriever.release()}
            }.getOrDefault(0)
            if(height>TARGET_HEIGHT)videoEffects+=Presentation.createForHeight(TARGET_HEIGHT)

            val edited=EditedMediaItem.Builder(MediaItem.fromUri(input.toUri()))
                .setEffects(Effects(emptyList(),videoEffects))
                .build()
            val encoderFactory=DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(AUDIO_BITRATE).build())
                .build()
            lateinit var transformer:Transformer
            val handler=Handler(Looper.getMainLooper())
            val progressHolder=ProgressHolder()
            val progressTicker=object:Runnable{
                override fun run(){
                    if(!continuation.isActive)return
                    runCatching{
                        if(transformer.getProgress(progressHolder)==Transformer.PROGRESS_STATE_AVAILABLE){
                            onProgress(progressHolder.progress.coerceIn(0,100))
                        }
                    }
                    handler.postDelayed(this,750L)
                }
            }
            val listener=object:Transformer.Listener{
                override fun onCompleted(composition:Composition,exportResult:ExportResult){
                    handler.removeCallbacks(progressTicker)
                    onProgress(100)
                    if(continuation.isActive)continuation.resume(output.exists()&&output.length()>0L)
                }
                override fun onError(composition:Composition,exportResult:ExportResult,exportException:ExportException){
                    handler.removeCallbacks(progressTicker)
                    if(output.exists())output.delete()
                    if(continuation.isActive)continuation.resume(false)
                }
            }
            transformer=Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()
            continuation.invokeOnCancellation{
                handler.removeCallbacks(progressTicker)
                handler.post{
                    runCatching{transformer.cancel()}
                    if(output.exists())output.delete()
                }
            }
            try{
                transformer.start(edited,output.absolutePath)
                handler.post(progressTicker)
            }catch(_:Throwable){
                handler.removeCallbacks(progressTicker)
                if(output.exists())output.delete()
                if(continuation.isActive)continuation.resume(false)
            }
        }
    }
}
