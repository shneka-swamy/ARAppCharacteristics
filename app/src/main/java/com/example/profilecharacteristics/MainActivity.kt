package com.example.profilecharacteristics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File
import java.lang.RuntimeException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var number_files: TextView
    lateinit var number_phone: TextView
    lateinit var del_files: Button
    var count = 0
    lateinit var selection: Button
    lateinit var list: Array<File>
    var package_name: String = "com.example.p2pconnection"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The content view is set for file management purpose only
        setContentView(R.layout.activity_main)
        number_files = findViewById(R.id.textView)
        number_phone = findViewById(R.id.textView2)
        del_files = findViewById(R.id.button)
        selection = findViewById(R.id.button2)

        // Count the number of files that already exist
        val path = getExternalFilesDir(null)
        list = path!!.listFiles()
        for (f in list) {
            val name = f.name
            if (name.endsWith(".txt")) count++
        }
        number_files.setText(count.toString())

        // Delete the files upon user's request
        del_files.setOnClickListener(View.OnClickListener {
            if (count == 0) return@OnClickListener
            for (f in list) {
                val name = f.name
                if (name.endsWith(".txt")) {
                    f.delete()
                    count--
                }
            }
            number_files.setText(count.toString())
        })

        selection.setOnClickListener(View.OnClickListener {
            // When the characteristics needs more than one phone
            if (number_phone.text.toString().toInt() > 1) {
                startActivity(Intent(this, MultiplePhone::class.java))
            }
            // When the characteristics needs only one phone
            else {
                // Get the name of the file
                val clientThread = Thread {
                    val sdf = SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault())
                    val date_time = sdf.format(Date())
                    val default_name = "Trace$date_time.txt"
                    // Call the method to get the required values
                    val gv = GetValues<Any?>(
                        this@MainActivity,default_name,
                        Constants.Type.SERVER, null
                    )
                    val runnable = object: Runnable {
                        override fun run() {
                            gv.values(false)
                        }
                    }
                    runnable.run()
                }
                clientThread.start()
                // Launch the app on the back of which this must be run
                val launchIntent = packageManager.getLaunchIntentForPackage(package_name)
                    ?: throw RuntimeException("package $package_name should be in queries")
                val packageManager = this.packageManager
                if (launchIntent.resolveActivity(packageManager) != null) {
                    startActivity(launchIntent)
                } else {
                    Log.d(TAG, "The package provided is not launchable")
                }
            }
        })
    }

    // Handle this function
    fun setIsWifiP2pEnabled(b: Boolean) {
        assert(false)
    }

    companion object {
        const val TAG = "MainActivity"
    }
}