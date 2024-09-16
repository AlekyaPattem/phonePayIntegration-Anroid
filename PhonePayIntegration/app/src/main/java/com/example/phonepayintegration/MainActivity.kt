package com.example.phonepayintegration

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.phonepe.intent.sdk.api.B2BPGRequestBuilder
import com.phonepe.intent.sdk.api.PhonePe
import com.phonepe.intent.sdk.api.models.PhonePeEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.IOException
import org.json.JSONObject
import java.nio.charset.Charset
import java.security.MessageDigest


class MainActivity : AppCompatActivity() {

    val merchantID ="UATM22AQEUEZXMSV"
    val saltKey ="653e48f8-6447-4de4-8131-4d0475564af0"
    val saltIndex = 1
    private val B2B_PG_REQUEST_CODE = 777
    val merchantTransactionId = System.currentTimeMillis().toString()
    var xVerify = ""
    var base64Body = ""
    val apiEndPoint = "/pg/v1/pay"
    var checksum = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        PhonePe.init(this, PhonePeEnvironment.SANDBOX, "UATM22AQEUEZXMSV",null)

        initiateProcess()

        var textClick = findViewById<TextView>(R.id.payTv)
        textClick.setOnClickListener {
            payApi()
        }
    }

    fun initiateProcess(){
        val data = JSONObject()
        data.put("merchantTransactionId",merchantTransactionId)
        data.put("merchantId", merchantID)
        data.put("merchantUserId", merchantTransactionId)
        data.put("amount", 200)
        data.put("mobileNumber", "1234567890")
        data.put("callbackUrl", "https://webhook.site/d75afb38-aa7a-41b2-9eac-05d8effd4538")
        val mPaymentInstrument = JSONObject()
        mPaymentInstrument.put("type", "PAY_PAGE")
        data.put("paymentInstrument", mPaymentInstrument)
        base64Body = Base64.encodeToString(data.toString().toByteArray(Charset.defaultCharset()), Base64.NO_WRAP)

        checksum = calculateChecksum(base64Body, apiEndPoint, saltKey, saltIndex)

        xVerify = generateChecksum(merchantId = merchantID, merchantTransactionId, saltKey, saltIndex)

        Log.e("TAG", "payApi:Input details merchantId: $merchantID merchantTransactionId: $merchantTransactionId xVerify: $xVerify")
    }

    fun payApi(){
        val b2BPGRequest = B2BPGRequestBuilder()
            .setData(base64Body)
            .setChecksum(checksum)
            .setUrl(apiEndPoint)
            .build()
        try {
            PhonePe.getImplicitIntent(this, b2BPGRequest,null )
                ?.let { startActivityForResult(it,B2B_PG_REQUEST_CODE) };
        } catch (e: Exception) {
            println("failed pay api"+e.message)
        }
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun calculateChecksum(base64Body: String, apiEndPoint: String, salt: String, saltIndex: Int): String {
        val combinedString = base64Body + apiEndPoint + salt
        val hash = sha256(combinedString)
        return "$hash###$saltIndex"
    }

    fun generateChecksum(merchantId: String, merchantTransactionId: String, saltKey: String, saltIndex: Int): String {
        val concatenatedString = "/pg/v1/status/$merchantId/$merchantTransactionId" + saltKey
        val sha256Hash = sha256(concatenatedString)
        return "$sha256Hash###$saltIndex"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("TAG", "onActivityResult: result Code " + resultCode + "")
        if (requestCode == B2B_PG_REQUEST_CODE) {
            checkStatusCallApi()
            /*This callback indicates only about completion of UI flow.
            Inform your server to make the transaction
            status call to get the status. Update your app with the
            success/failure status.*/
        }

    }

    suspend fun makeApiCall() {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("https://api-preprod.phonepe.com/apis/pg-sandbox/pg/v1/status/$merchantID/$merchantTransactionId")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-VERIFY", xVerify)
            .addHeader("X-MERCHANT-ID", merchantID)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Log.e("TAG", "Response callStatus:: ${response.body?.string()}")
            } else {
                Log.e("TAG", "Error callStatus:: ${response.code}")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun checkStatusCallApi() {
        CoroutineScope(Dispatchers.IO).launch {
            makeApiCall()
        }
    }

    /*suspend fun webPayInitiateApi() {
        val client = OkHttpClient()

        // Prepare media type and request body
        val mediaType = "application/json".toMediaType()
        val body = """
        {
            "request":$base64Body"
        }
    """.trimIndent().toRequestBody(mediaType)

        // Build the request
        val request = Request.Builder()
            .url("https://api-preprod.phonepe.com/apis/pg-sandbox/pg/v1/pay")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-VERIFY", xVerify)
            .build()

        // Make the network call
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                var url = ""
                if (responseBody != null) {
                    Log.e("TAG", "webPayInitiateApi: Response: ${responseBody} ")
                    val obj: JSONObject = JSONObject(responseBody)
                    url = obj.getJSONObject("data").getJSONObject("instrumentResponse").getJSONObject("redirectInfo").getString("url")
                } else {
                    Log.e("TAG","Response body is null")
                }
                val intent = Intent(this, WebViewActivity::class.java)
                intent.putExtra("url", url)
                startActivity(intent)
                Log.e("TAG", "webPayInitiateApi: navigated to webUrl", )
            } else {
                // Handle error response
                println("Error: ${response.code}")
            }
        } catch (e: IOException) {
            // Handle network error
            e.printStackTrace()
        }
    }

    fun webInitiateApiCall() {
        GlobalScope.launch(Dispatchers.IO) {
            webPayInitiateApi()
        }
    }*/

}