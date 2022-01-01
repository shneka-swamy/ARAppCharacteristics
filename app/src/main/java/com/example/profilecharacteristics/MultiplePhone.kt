package com.example.profilecharacteristics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.*

// This class is used when more than two phones are required to get characteristics
class MultiplePhone: AppCompatActivity() {
    lateinit var file_name: TextView
    lateinit var ip_address: TextView
    lateinit var host_num: TextView
    lateinit var spinner: Spinner
    lateinit var server: Button
    lateinit var client: Button
    lateinit var selection: String

    var mlbm: LocalBroadcastManager? = null
    var mbr: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Constants.ACTION.CLOSE_ACTION) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.muliple_phone_activity)
        // Setting broadcast listener
        mlbm = LocalBroadcastManager.getInstance(this)
        val mif = IntentFilter()
        mif.addAction(Constants.ACTION.CLOSE_ACTION)

        // Declaration and handling of the text boxes
        file_name = findViewById(R.id.textView6)
        ip_address = findViewById(R.id.textView4)
        host_num = findViewById(R.id.textView7)
        server = findViewById(R.id.button4)
        client = findViewById(R.id.button5)
        spinner = findViewById(R.id.spinner)

        // Set a default Host number and IP address
        val localhost = "127.0.0.1"
        val host_default = "5555"
        ip_address.setText(localhost)
        host_num.setText(host_default)

        // Set default and handles the spinner
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.selection, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.setAdapter(adapter)
        spinner.setVisibility(View.VISIBLE)
        spinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
                selection = adapterView.getItemAtPosition(i) as String
                Log.v(MainActivity.TAG, "item selected$selection")
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                selection = "Discover"
            }
        })

        // Get the name of the file
        val sdf = SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault())
        val date_time = sdf.format(Date())
        val default_name = "Trace$date_time.txt"
        file_name.setText(default_name)

        // This handles a device connecting as a server
        server.setOnClickListener(View.OnClickListener {
            Log.v(MainActivity.TAG, "Intent for starting Server")
            val mIntent = Intent(this@MultiplePhone, YourService::class.java)
            mIntent.action = Constants.ACTION.START_SERVER
            mIntent.putExtra("file_name", "" + file_name.getText())
            mIntent.putExtra("selection", selection)
            mIntent.putExtra("ip_address", "" + ip_address.getText())
            mIntent.putExtra("host_number", "" + host_num.getText())
            startService(mIntent)
            mlbm!!.registerReceiver(mbr, mif)
        })

        // This handles a device connecting as a client
        client.setOnClickListener(View.OnClickListener {
            Log.v(MainActivity.TAG, "Intent for starting Client")
            val mIntent = Intent(this@MultiplePhone, YourService::class.java)
            mIntent.action = Constants.ACTION.START_CLIENT
            mIntent.putExtra("file_name", "" + file_name.getText())
            mIntent.putExtra("selection", selection)
            mIntent.putExtra("ip_address", "" + ip_address.getText())
            mIntent.putExtra("host_number", "" + host_num.getText())
            startService(mIntent)
            mlbm!!.registerReceiver(mbr, mif)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mlbm!!.unregisterReceiver(mbr)
    }
    companion object {
        const val TAG = "MultiplePhone"
    }

}
