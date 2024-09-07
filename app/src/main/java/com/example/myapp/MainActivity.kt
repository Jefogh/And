package com.example.captchaapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.*
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var addAccountButton: Button
    private lateinit var uploadBackgroundsButton: Button
    private lateinit var accountLayout: LinearLayout

    private val REQUEST_CODE_IMAGE_PICK = 100
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize OpenCV
        OpenCVLoader.initDebug()

        // Initialize UI elements
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        addAccountButton = findViewById(R.id.addAccountButton)
        uploadBackgroundsButton = findViewById(R.id.uploadBackgroundsButton)
        accountLayout = findViewById(R.id.accountLayout)

        // Handle Add Account button click
        addAccountButton.setOnClickListener {
            addAccount()
        }

        // Handle Upload Backgrounds button click
        uploadBackgroundsButton.setOnClickListener {
            uploadBackgrounds()
        }
    }

    // Function to add a new account to the layout
    private fun addAccount() {
        val username = usernameInput.text.toString()
        val password = passwordInput.text.toString()

        if (username.isNotEmpty() && password.isNotEmpty()) {
            Toast.makeText(this, "Account added: $username", Toast.LENGTH_SHORT).show()

            val accountBox = LinearLayout(this)
            accountBox.orientation = LinearLayout.HORIZONTAL

            val accountLabel = EditText(this)
            accountLabel.setText("Account: $username")
            accountBox.addView(accountLabel)

            accountLayout.addView(accountBox)

            // Attempt login
            Thread {
                login(username, password)
            }.start()
        } else {
            Toast.makeText(this, "Username or Password cannot be empty!", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to upload background images
    private fun uploadBackgrounds() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_IMAGE_PICK)
    }

    // Handle image selection result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_IMAGE_PICK && resultCode == RESULT_OK) {
            val selectedImageUri = data?.data

            selectedImageUri?.let {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                // Process the image with OpenCV
                val processedImage = processImageWithOpenCV(bitmap)

                // Send the image to API and process the response
                val captchaResult = processCaptcha(bitmap)
                sendCaptchaToApi(captchaResult, "https://api.ecsc.gov.sy:8080/rs/reserve")
            }
        }
    }

    // Function to login to API
    private fun login(username: String, password: String) {
        val url = "https://api.ecsc.gov.sy:8080/secure/auth/login"
        val userAgent = generateUserAgent()
        val requestBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/json")
            .header("Source", "WEB")
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://ecsc.gov.sy/")
            .header("Origin", "https://ecsc.gov.sy")
            .header("Connection", "keep-alive")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Login Failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Login Successful", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Login Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // Function to process the image using OpenCV
    private fun processImageWithOpenCV(bitmap: Bitmap): Bitmap {
        val mat = Mat()
        val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        org.opencv.android.Utils.bitmapToMat(bmp32, mat)

        // Apply image processing (e.g., resizing, grayscaling)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(mat, mat, Size(5.0, 5.0), 0.0)

        val processedBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(mat, processedBitmap)
        return processedBitmap
    }

    // Function to process the captcha image using Google ML Kit OCR
    private fun processCaptcha(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        var captchaResult = ""
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                for (block in visionText.textBlocks) {
                    captchaResult = block.text
                }
                Toast.makeText(this, "Recognized text: $captchaResult", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to recognize text: ${e.message}", Toast.LENGTH_LONG).show()
            }
        return captchaResult
    }

    // Function to send the captcha solution to an API
    private fun sendCaptchaToApi(captchaResult: String, apiUrl: String) {
        val requestBody = FormBody.Builder()
            .add("captcha_solution", captchaResult)
            .build()

        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", generateUserAgent())
            .header("Content-Type", "application/json")
            .header("Source", "WEB")
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://ecsc.gov.sy/")
            .header("Origin", "https://ecsc.gov.sy")
            .header("Connection", "keep-alive")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "API Request Failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@MainActivity, "Captcha Solved Successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to Solve Captcha", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // Function to generate random User-Agent
    private fun generateUserAgent(): String {
        val userAgentList = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv=89.0) Gecko/20100101 Firefox/89.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.1 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"
        )
        return userAgentList[Random.nextInt(userAgentList.size)]
    }
}
