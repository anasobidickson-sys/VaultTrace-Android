package com.vaulttrace.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.*
import android.graphics.Color
import androidx.core.app.ActivityCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.DataOutputStream

class MainActivity : Activity() {
    private val contactRequest = 301
    private val locationRequest = 302
    private val createContactsFile = 401
    private val createLocationFile = 402
    private val pickEvidenceFile = 501
    private var pendingCsv = ""
    private var pendingLocation = ""
    private var selectedFileUri: android.net.Uri? = null
    private var token: String? = null
    private lateinit var status: TextView
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var caseId: EditText
    private lateinit var consentRef: EditText
    private lateinit var evidenceType: Spinner
    private val apiBase = "https://lightgoldenrodyellow-magpie-546365.hostingersite.com/api.php"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,38,24,24);setBackgroundColor(Color.rgb(8,17,31)) }
        fun label(t:String,size:Float,color:Int=Color.WHITE)=TextView(this).apply{text=t;textSize=size;setTextColor(color);setPadding(0,9,0,9)}
        fun field(hintText:String, secret:Boolean=false)=EditText(this).apply{hint=hintText;setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setSingleLine(true);if(secret)inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD}
        fun button(t:String, action:()->Unit){root.addView(Button(this).apply{text=t;setOnClickListener{action()}})}
        root.addView(label("◈ VAULTTRACE",14f,Color.rgb(112,224,192)))
        root.addView(label("Evidence workspace",26f))
        root.addView(label("Wallet demonstration · No real funds held",12f,Color.LTGRAY))
        email=field("Investigator email");password=field("Password",true)
        root.addView(email);root.addView(password)
        button("Sign in securely") { login() }
        status=label("Sign in to connect to your VaultTrace server.",12f,Color.rgb(112,224,192));root.addView(status)
        val consent=CheckBox(this).apply{text="I own this device or have documented authority and consent for this collection.";setTextColor(Color.WHITE)}
        root.addView(consent)
        button("Export contacts to a file") { if(consent.isChecked)beginContacts() else toast("Confirm device authority first.") }
        button("Record current location once") { if(consent.isChecked)beginLocation() else toast("Confirm device authority first.") }
        button("Select owner-exported SMS / call-log file") { if(consent.isChecked)pickEvidenceFile() else toast("Confirm device authority first.") }
        root.addView(label("Upload selected evidence",18f))
        caseId=field("Case ID (numeric)")
        consentRef=field("Consent / authority reference")
        root.addView(caseId);root.addView(consentRef)
        evidenceType=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("file","contact_export","location_record","message_export","call_log_export","other"))}
        root.addView(evidenceType)
        button("Upload selected file to investigator case") { if(consent.isChecked)uploadSelected() else toast("Confirm device authority first.") }
        root.addView(label("No background collection. Upload occurs only after you select a file and tap Upload. SMS and call-log data must be exported by the device owner using supported tools.",11f,Color.LTGRAY))
        setContentView(ScrollView(this).apply{addView(root)})
    }

    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun setStatus(s:String){runOnUiThread{status.text=s}}
    private fun login(){
        val em=email.text.toString().trim();val pw=password.text.toString()
        if(em.isBlank()||pw.isBlank()){toast("Enter email and password.");return}
        Thread {
            try{
                val conn=(URL("$apiBase?action=login").openConnection() as HttpURLConnection)
                conn.requestMethod="POST";conn.connectTimeout=15000;conn.readTimeout=15000;conn.doOutput=true
                conn.setRequestProperty("Content-Type","application/json")
                conn.outputStream.use{it.write(JSONObject().put("email",em).put("password",pw).toString().toByteArray())}
                val response=readResponse(conn)
                val obj=JSONObject(response)
                if(conn.responseCode in 200..299){token=obj.getString("token");setStatus("Connected as ${obj.getJSONObject("user").getString("name")}. Token expires in 12 hours.")}
                else setStatus("Sign-in failed: ${obj.optString("error","Server error")}")
                conn.disconnect()
            }catch(e:Exception){setStatus("Connection error: ${e.message}")}
        }.start()
    }
    private fun readResponse(c:HttpURLConnection):String=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}

    private fun beginContacts(){
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.READ_CONTACTS),contactRequest) else exportContacts()
    }
    private fun exportContacts(){
        val rows=StringBuilder("display_name,phone_number\n")
        val cursor=contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER),null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")
        cursor?.use{c->while(c.moveToNext())rows.append(csv(c.getString(0)?: "")).append(",").append(csv(c.getString(1)?: "")).append("\n")}
        pendingCsv=rows.toString();startSave("text/csv","vaulttrace_contacts.csv",createContactsFile)
    }
    private fun beginLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),locationRequest);return}
        val lm=getSystemService(LOCATION_SERVICE) as LocationManager
        val loc:Location?=try{lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?:lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)}catch(_:SecurityException){null}
        if(loc==null){toast("No recent location available. Enable location and try again.");return}
        pendingLocation="latitude,longitude,accuracy_m,recorded_at\n${loc.latitude},${loc.longitude},${loc.accuracy},${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(Date())}\n"
        startSave("text/csv","vaulttrace_location.csv",createLocationFile)
    }
    private fun startSave(mime:String,name:String,request:Int){val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=mime;putExtra(Intent.EXTRA_TITLE,name)};startActivityForResult(i,request)}
    private fun pickEvidenceFile(){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"};startActivityForResult(i,pickEvidenceFile)}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(grantResults.isNotEmpty()&&grantResults[0]==PackageManager.PERMISSION_GRANTED){if(requestCode==contactRequest)exportContacts() else if(requestCode==locationRequest)beginLocation()}else toast("Permission not granted. Nothing collected.")}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data?.data==null)return
        if(requestCode==createContactsFile||requestCode==createLocationFile){val content=if(requestCode==createContactsFile)pendingCsv else pendingLocation;contentResolver.openOutputStream(data.data!!)?.use{it.write(content.toByteArray())};selectedFileUri=data.data;setStatus("File saved and selected. Sign in, enter case details and upload when ready.");}
        else if(requestCode==pickEvidenceFile){selectedFileUri=data.data;setStatus("Owner-exported file selected. Review and upload when ready.");}
    }
    private fun uploadSelected(){
        val authToken=token;if(authToken.isNullOrBlank()){toast("Sign in first.");return}
        val uri=selectedFileUri;if(uri==null){toast("Export or select an evidence file first.");return}
        val cId=caseId.text.toString().trim();val ref=consentRef.text.toString().trim()
        if(cId.isBlank()||ref.isBlank()){toast("Enter case ID and consent reference.");return}
        setStatus("Uploading selected evidence…")
        Thread{
            var conn:HttpURLConnection?=null
            try{
                val boundary="----VaultTrace${System.currentTimeMillis()}"
                conn=URL("$apiBase?action=upload").openConnection() as HttpURLConnection
                conn.requestMethod="POST";conn.connectTimeout=20000;conn.readTimeout=30000;conn.doOutput=true
                conn.setRequestProperty("Authorization","Bearer $authToken")
                conn.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
                DataOutputStream(conn.outputStream).use{out->
                    fun field(name:String,value:String){out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")}
                    field("case_id",cId);field("consent_reference",ref);field("evidence_type",evidenceType.selectedItem.toString())
                    val name="evidence_file";val fileName="evidence_upload"
                    out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                    contentResolver.openInputStream(uri)!!.use{input->input.copyTo(out)}
                    out.writeBytes("\r\n--$boundary--\r\n")
                }
                val response=readResponse(conn);val obj=JSONObject(response)
                if(conn.responseCode in 200..299)setStatus("Upload successful. SHA-256: ${obj.optString("sha256")}")
                else setStatus("Upload failed: ${obj.optString("error","Server error")}")
            }catch(e:Exception){setStatus("Upload error: ${e.message}")}finally{conn?.disconnect()}
        }.start()
    }
    private fun csv(s:String)="\""+s.replace("\"","\"\"")+"\""
}
