package com.example.myvision

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myvision.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.util.*


class MainActivity : AppCompatActivity(),TextToSpeech.OnInitListener {
    override fun onInit(p0: Int) {
    }

    lateinit var labels:List<String>
    lateinit var mTTs: TextToSpeech
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var textureView: TextureView
    lateinit var cameraManager: CameraManager
    lateinit var model: SsdMobilenetV11Metadata1

    //for drawing box
    val paint = Paint()
    var colors = listOf<Int>(
        Color.BLUE,Color.CYAN,Color.GREEN,Color.BLACK,Color.RED,Color.YELLOW
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        labels = FileUtil.loadLabels(this,"labels.txt")
        //defining the model
        val model = SsdMobilenetV11Metadata1.newInstance(this)

        //defining the image processor to reduce bitmap to 300x300 pixels needed by TFlite
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()//can use nearest neighbour instead of bilinear

        //initializing tts engine
        mTTs = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                mTTs.language = Locale.ENGLISH
            }
        }

        //Thread handler for camera
        val handlerThread = HandlerThread("video")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView=findViewById(R.id.imageView)

        textureView=findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, width: Int, height: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap=textureView.bitmap!!     //if null creates nullpoint exception error

// Creates inputs for reference. then reduce it using processor object
                var image = TensorImage.fromBitmap(bitmap)
                image=imageProcessor.process(image)

// Runs model inference and gets result.
                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                //drawing the rectangle on the prediction
                // need mutable bitmap
                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888,true)
                val canvas = Canvas(mutable)

                //scaling 0 to 1 value from locations to height and width by multiplying
                val h = mutable.height
                val w = mutable.width

                paint.textSize = h/15f
                paint.strokeWidth=h/85f
                var x = 0
                //confidence with each class
                scores.forEachIndexed { index, fl ->
                    x=index                                 //iterating for each class
                    x*=4
                    if(fl>0.7){                             //confidence above 70%
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(locations.get(x+1)*w,locations.get(x)*h,locations.get(x+3)*w,locations.get(x+2)*h),paint)
                        paint.style=Paint.Style.FILL
                        val labelText = labels[classes[index].toInt()]                                                          //TO FIX FOR REPEATED NARRATION
                        canvas.drawText(labelText,locations.get(x+1)*w,locations.get(x)*h,paint)
                        //mTTs.speak(labelText, TextToSpeech.QUEUE_ADD, null, null)

                       // val speechRate = 1.0f
                        //mTTs.setSpeechRate(speechRate)
                        val toSpeak2 = "No trained object detected"
                         if(labelText==""){
                            mTTs.speak(toSpeak2,TextToSpeech.QUEUE_FLUSH,null,null)
                        }else{
                            mTTs.speak(labelText,TextToSpeech.QUEUE_ADD,null,null)
                        }
                        /*val utteranceId = "WordUtterance"

// Set an utterance progress listener
                        mTTs.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                // Speech synthesis started
                            }

                            override fun onDone(utteranceId: String?) {
                                // Speech synthesis completed
                                // Delay before speaking the next word
                                Handler().postDelayed({
                                    // Call the method to speak the next word in the queue
                                    speakNextWord()
                                }, 1000) // Adjust the delay duration as needed
                            }

                            override fun onError(utteranceId: String?) {
                                // Speech synthesis error occurred
                            }
                        })

                        // Method to speak the next word in the queue
                        fun speakNextWord() {
                            // Get the next word from the queue and speak it
                            // Example: val nextWord = getNextWordFromQueue()
                            mTTs.speak(nextWord, TextToSpeech.QUEUE_ADD, null, utteranceId)
                        }
                        if (toSpeak.isBlank()) {
                            mTTs.speak(toSpeak2, TextToSpeech.QUEUE_ADD, null, null)
                        } else {
                            // Start speaking the words by calling the speakNextWord() method
                            // to enqueue each word in the queue
                            speakNextWord()
                        }*/
                    }
                }
                imageView.setImageBitmap(mutable)
            }
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    // Releases model resources if no longer used.
    //closing model when app is destroyed not after single prediction
    override fun onDestroy() {
        super.onDestroy()
        model.close()
        mTTs.shutdown()
    }
    override fun onPause() {
        super.onPause()
        mTTs.stop()
        mTTs.shutdown()
    }


    @SuppressLint("MissingPermission")
    fun open_camera(){
        cameraManager.openCamera(cameraManager.cameraIdList[0],object : CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                var surfaceTexture = textureView.surfaceTexture
                var surface = Surface(surfaceTexture)
                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface),object :CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(),null,null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {

                    }
                },handler)

            }
            override fun onDisconnected(camera: CameraDevice) {

            }

            override fun onError(camera: CameraDevice, error: Int) {

            }},handler)
    }
    @SuppressLint("NewApi")
    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA),101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0]!=PackageManager.PERMISSION_GRANTED)
            get_permission()
    }


}