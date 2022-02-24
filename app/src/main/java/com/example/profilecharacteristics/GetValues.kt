// The CPU utilisation code is taken from souch -- github
// The other functionalities are implemented by using the suggestion provided by stack overflow.

package com.example.profilecharacteristics

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.apache.commons.net.ntp.NTPUDPClient
import org.apache.commons.net.ntp.TimeInfo
import java.io.*
import java.lang.Exception
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

// TODO: Must remove the NTP time from the list
// TODO: Must try to remove all warnings.
class GetValues<NTPTime> @RequiresApi(api = Build.VERSION_CODES.M) constructor(
    private var context: Context, filename: String?, service: Constants.Type,
    networkQueue: NetworkQueue?
) {
    private var bm: BatteryManager
    private var cm: ConnectivityManager
    private var nc: NetworkCapabilities?
    private var n: Network?
    private var batteryStatus: Intent?
    private var outputStream: FileOutputStream? = null
    private var writer: PrintWriter? = null
    var file: File
    var downstreamPrelim: Long
    var upstreamPrelim: Long
    val networkQueue: NetworkQueue?

    // These variables are created for detecting the bandwidth gap in latency.
    var startTime = Stack<Long>()
    var endTime = Stack<Long>()
    private var reqdQueue: MutableList<String> = ArrayList()
    private var low_limit = 0f
    private var up_limit = 0f
    var maximum = 0f
    var movingUp = false
    var movingDown = false
    private var service: Constants.Type
    private var value: Long = 0

    // These are for calculating the difference in time in latency
    private var diffTime: Long
    private var prevTime: Long
    private var currentTime: Long
    private lateinit var output: IntArray
    private lateinit var minimumValue: IntArray
    private lateinit var maximumValue: IntArray
    private var nCores: Int = 0

    // Writes to a file
    fun writeToFile(data: String) {
        try {
            outputStream = FileOutputStream(file, true)
            writer = PrintWriter(outputStream)
            writer!!.print(
                """
                    $data
                    
                    """.trimIndent()
            )
            writer!!.flush()
            writer!!.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }

    // Finds the current battery level
    fun batteryLevel(): Int{
            // This does not change the battery value all time.
            //val level = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            //val scale = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            //val batteryPct = (level * 100) / scale.toFloat()
            val batteryPct:Int = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            writeToFile("Battery Level $batteryPct")
            Log.v(TAG, "Battery Level is $batteryPct")
            return batteryPct
        }

    @SuppressLint("PrivateApi")
    fun getTotalCapacity(context: Context?): Double {
        val PowerProfile: Any
        var batteryCapacity = 0.toDouble()
        val POWER_PROFILE_STRING = "com.android.internal.os.PowerProfile"
        try {
            PowerProfile = Class.forName(POWER_PROFILE_STRING).getConstructor(Context::class.java)
                .newInstance(context)
            batteryCapacity = Class.forName(POWER_PROFILE_STRING).getMethod("getBatteryCapacity")
                .invoke(PowerProfile) as Double
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return batteryCapacity
    }

    val chargingType: String
        get() {
            val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            var typecharging = " "
            if (status == BatteryManager.BATTERY_PLUGGED_USB) typecharging += "USB Charging" else if (status == BatteryManager.BATTERY_PLUGGED_AC) typecharging += "AC Charging" else if (status == BatteryManager.BATTERY_PLUGGED_WIRELESS) typecharging =
                "Wireless Charging"
            return typecharging
        }
    val isCharging: Unit
        get() {
            val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging: String
            charging = if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            ) {
                "May be charging $chargingType"
            } else "Battery not charging"
            writeToFile(charging)
        }

    // Gets the remaining energy level
    fun getEnergyLevel(batteryLevel: Int) {
        val energy_left = (getTotalCapacity(context) * (batteryLevel / 100)).toInt()
        // Log.v(TAG,"Battery Capacity - actual"+ energy_left);
        writeToFile("Energy left in mAh $energy_left")
    }

    fun convert_format(bandwidth: Double): String {
        return if (bandwidth < KB) String.format(
            Locale.getDefault(),
            "%.1f B/s",
            bandwidth
        ) else if (bandwidth < MB) String.format(
            Locale.getDefault(), "%.1f KB/s", bandwidth / KB
        ) else if (bandwidth < GB) String.format(
            Locale.getDefault(), "%.1f MB/s", bandwidth / MB
        ) else String.format(Locale.getDefault(), "%.1f GB/s", bandwidth / GB)
    }//if(this.service == Constants.Type.CLIENT)

    // Finds the download bandwidth
    val bandwidthDown: Unit
        get() {
            val down_bandwidth = TrafficStats.getTotalRxBytes()
            val down_inter_bandwidth: Long =
                (down_bandwidth - downstreamPrelim) / (YourService.SET_TIMER / SECONDS_IN_MS)
            writeToFile("Download Bandwidth " + convert_format(down_inter_bandwidth.toDouble()))
           // Log.v(
             //   "GetValues",
               // "Download Bandwidth " + down_inter_bandwidth + " converted to " + convert_format(
                //    down_inter_bandwidth.toDouble()
                //)
           //
            // )
            downstreamPrelim = down_bandwidth
            //if(this.service == Constants.Type.CLIENT)
            reqdQueue.add(convert_format(down_inter_bandwidth.toDouble()))
        }//if(this.service == Constants.Type.SERVER)
    //    reqdQueue.add(convert_format(up_inter_bandwidth));

    // Finds the upload bandwidth
    val bandwidthUp: Unit
        get() {
            val up_bandwidth = TrafficStats.getTotalTxBytes()
            val up_inter_bandwidth: Long =
                (up_bandwidth - upstreamPrelim) / (YourService.SET_TIMER / SECONDS_IN_MS)
            writeToFile("Upload Bandwidth " + convert_format(up_inter_bandwidth.toDouble()))
            //Log.v(
              //  "GetValues",
                //"Upload Bandwidth " + up_inter_bandwidth + " converted to " + convert_format(
                  //  up_inter_bandwidth.toDouble()
               // )
            //)
            upstreamPrelim = up_bandwidth
            //if(this.service == Constants.Type.SERVER)
            //    reqdQueue.add(convert_format(up_inter_bandwidth));
        }

    // These values are set from experimental results for "Just a line" - Can be changed.
    fun setValues() {
        if (service === Constants.Type.SERVER) {
            up_limit = 30f
            low_limit = 18.5f
        } else {
            up_limit = 30f
            low_limit = 18.5f
        }
    }// Store it to a stack

    // This function is used to get the latency
    // TODO: Must also remove the extra lines after the minima is reached
    val latency: LatencyTimeTuple?
        get() {
            var latencyTimeTuple: LatencyTimeTuple? = null
            if (reqdQueue.size <= 1) {
                value = currentTime + diffTime
            } else {
                val newElement = reqdQueue[1].split(" ").toTypedArray()
                val prevElement = reqdQueue[0].split(" ").toTypedArray()
                val newValue = newElement[0].toFloat()
                val oldValue = prevElement[0].toFloat()
                if (newValue > oldValue) {
                    if (!movingUp) {
                        movingUp = true
                        value = currentTime + diffTime
                    } else if (movingDown) {
                        if (maximum > low_limit && maximum < up_limit) {
                            Log.v(TAG, "Put in stack")
                            startTime.add(value)
                            endTime.add(prevTime)
                            if (service === Constants.Type.CLIENT) {
                                latencyTimeTuple =
                                    LatencyTimeTuple(startTime.peek(), endTime.peek())
                                val timeString = "Time value " + latencyTimeTuple.toString()
                                Log.v(TAG, timeString)
                                val byteBuffer: ByteBuffer = latencyTimeTuple.toBytes()
                                byteBuffer.rewind()
                                val convertback = LatencyTimeTuple(byteBuffer)
                                Log.v(TAG, "convertBack is " + convertback.toString())

                                // Store it to a stack
                                writeToFile(timeString)
                                startTime.pop()
                                endTime.pop()
                            }
                        }
                        value = currentTime + diffTime
                        movingDown = false
                        maximum = oldValue
                        //Log.v(TAG, "max is set to$maximum" )
                    }
                    if (prevElement[1] == "KB/s" && newElement[1] == "KB/s") {
                        if (newValue > maximum) {
                            maximum = newValue
                        }
                    }
                } else {
                    if (!movingDown && movingUp) {
                        movingDown = true
                    }
                }
                reqdQueue.removeAt(0)
                prevTime = currentTime
            }
            return latencyTimeTuple
        }//ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

    // Returns the type of network connection
    val connectionType: Unit
        get() {
            //ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            var type_connection = ""
            if (nc!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                type_connection += "WIFI"
            }
            if (nc!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                type_connection += "CELLULAR"
            }
            if (nc!!.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                type_connection += "BLUETOOTH"
            }
            if (nc!!.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                type_connection += "ETHERNET"
            }
            writeToFile("Type of connection $type_connection")
        }// null for client


    // return 0 if any pb occurs
    private fun readSystemFileAsInt(path: String): Int {
        var ret = 0
        try {
            val reader = RandomAccessFile(path, "r")
            ret = try {
                val line = reader.readLine()
                line.toInt()
            } finally {
                reader.close()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return ret
    }

    // This function returns the total number of cores.
    private fun getNumCores(): Int {
        //Private Class to display only CPU devices in the directory listing
        class CpuFilter : FileFilter {
            override fun accept(pathname: File): Boolean {
                //Check if filename is "cpu", followed by a single digit number
                return Pattern.matches("cpu[0-9]+", pathname.name)
            }
        }
        return try {
            //Get directory containing CPU info
            val dir = File("/sys/devices/system/cpu/")
            //Filter to only list the devices we care about
            val files: Array<File> = dir.listFiles(CpuFilter())
            //Return the number of cores (virtual CPU devices)
            files.size
        } catch (e: Exception) {
            //Default to return 1 core
            1
        }
    }

    @Synchronized
    fun getCoresUsageGuessFromFreq(){
        // Gets the number of cores, minimum and maximum frequencies.
        nCores = getNumCores()
        getMinMaxFreq()
        getCPUCurrentFrequency()

        var value = 0
        var totalUsage = 0.0f
        // Calculate the average usage
        for (i in 0 until nCores) {
            if (maximumValue[i] > 0 && output[i] > 0 && maximumValue[i] - minimumValue[i] > 0)
                value = (output[i] - minimumValue[i]) * 100 / (maximumValue[i] - minimumValue[i])
                totalUsage += value
                writeToFile("CPU $i usage $value")
               //Log.v(TAG, "Usage of CPU $i is $value")
        }

        writeToFile("Total CPU utilised ${totalUsage / nCores}")
        Log.v(TAG, "Total CPU utilised ${totalUsage / nCores}")
    }

    // This function returns the current, minimum and maximum frequencies of the CPU.
    @Throws(Exception::class)
    private fun getCPUCurrentFrequency(){

        output = IntArray(getNumCores())
        for (i in 0 until getNumCores()) {
            output[i] =
                readSystemFileAsInt("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
        }
    }

    private fun getMinMaxFreq(){
        minimumValue = IntArray(getNumCores())
        maximumValue = IntArray(getNumCores())
        for (i in 0 until getNumCores()){
            minimumValue[i] =
                readSystemFileAsInt("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
            maximumValue[i] =
                readSystemFileAsInt("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
        }
    }


    // Get battery level must always be placed before energy.
    // Can return the stack to the system
    fun values(multiplePhones: Boolean = true):LatencyTimeTuple? {
        var battery = batteryLevel()
        getEnergyLevel(battery)
        isCharging
        connectionType
        getCoresUsageGuessFromFreq()

        if (multiplePhones) {
            currentTime = systemTime()
            //Log.v(TAG, "Current time : " + stringTime(currentTime))
            bandwidthUp
            bandwidthDown
            // Can return the stack to the system
            val latencyTimeTuple: LatencyTimeTuple? = latency
            if (service === Constants.Type.SERVER) {
                assert(latencyTimeTuple == null)
                var lastLatencyTime: LatencyTimeTuple? = null
                if (networkQueue != null && networkQueue.queue.size > 0) { // null for client
                    lastLatencyTime = networkQueue.queue.peek()
                    networkQueue.queue.remove()
                    assert(lastLatencyTime != null)
                    Log.v(TAG, "GetValues got : " + lastLatencyTime.toString())
                    if (startTime.size > 0 && endTime.size > 0) {
                        val localStartTime = startTime.peek()
                        startTime.pop()
                        val localEndTime = endTime.peek()
                        endTime.pop()
                        val latency: Long = (lastLatencyTime.startTime - localStartTime
                                + (lastLatencyTime.endTime - localEndTime)) / 2
                        Log.v(
                            TAG,
                            "Latency Value: $latency"
                        )
                        writeToFile("Latency Value : $latency")
                    }
                }
            }
            return latencyTimeTuple
        }
        return null
    }

    companion object {
        var TAG = "Get Values"
        const val TIME_SERVER = "time-a.nist.gov"
        private const val SECONDS_IN_MS = 1000
        fun stringTime(timeRecv: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(timeRecv)
        }

        // This function returns NTP time
        fun NTPTime(): Long {
            val timeClient = NTPUDPClient()
            var returnTime: Long = 0
            try {
                val inetAddress = InetAddress.getByName(TIME_SERVER)
                val timeInfo: TimeInfo = timeClient.getTime(inetAddress)
                returnTime = timeInfo.getMessage().getTransmitTimeStamp().getTime()
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Inet address not found")
                e.printStackTrace()
            } catch (e: IOException) {
                Log.e(TAG, "IOException reached")
                e.printStackTrace()
            }
            timeClient.close()
            return returnTime
        }

        // This function returns the system time
        fun systemTime(): Long {
            val returnTime: Long
            returnTime = System.currentTimeMillis()
            return returnTime
        }

        private const val B: Long = 1
        private const val KB = B * 1024
        private const val MB = KB * 1024
        private const val GB = MB * 1024
    }

    init {
        bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        batteryStatus = context.registerReceiver(null, ifilter)
        cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        n = cm.activeNetwork
        nc = cm.getNetworkCapabilities(n)
        downstreamPrelim = TrafficStats.getTotalRxBytes()
        upstreamPrelim = TrafficStats.getTotalTxBytes()
        this.service = service
        setValues()
        val path = context.getExternalFilesDir(null)
        file = File(path, filename)
        if (!file.exists()) {
            try {
                val createdFile = file.createNewFile()
                assert(createdFile)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // Set up the initial time
        prevTime = systemTime()
        diffTime = NTPTime() - prevTime
        currentTime = systemTime()
        this.networkQueue = if (service === Constants.Type.SERVER) networkQueue else null
    }
}