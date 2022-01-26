// The socket communication part of the code uses components from the example provided by Brendan Innis
package com.example.profilecharacteristics

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.os.*
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.lang.RuntimeException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.UnknownHostException
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.text.SimpleDateFormat
import java.util.*

// TODO : Add wifi Manager if the phone is pixel only
class YourService : Service() {
    var gv: GetValues<*>? = null
    lateinit var ip_bytes: ByteArray
    var port_number = 0
    var handler: Handler? = null
    var runnable: Runnable? = null
    var package_name: String = "com.example.p2pconnection"
    var serverThread: Thread? = null
    var clientThread: Thread? = null
    var singleThread: Thread? = null
    var socketChannel: SocketChannel? = null
    var serverChannel: SocketChannel? = null
    var client: Client? = null
    var server: Server? = null
    var nsdManager: NsdManager? = null
    var registrationListener: RegistrationListener? = null
    var serviceName: String? = null
    var discoveryListener: DiscoveryListener? = null
    var resolveListener: NsdManager.ResolveListener? = null
    var mService: NsdServiceInfo? = null
    var wifi: WifiManager? = null
    var multicastLock: MulticastLock? = null
    var networkQueue: NetworkQueue? = null // only used for server to send to Getvalues;

    @Throws(IOException::class)
    fun initializeServerSocket() {
        server = createServer(port_number)
        serverThread!!.start()
        Log.v(TAG, "localPort set to $port_number")
    }

    fun initializeRegistrationListener() {
        registrationListener = object : RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                serviceName = NsdServiceInfo.serviceName
                Log.v(TAG, "In Service registered$serviceName")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.v(TAG, "Nsd Registration Failed")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.v(TAG, "In service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.v(TAG, "Nsd UnRegistration Failed")
            }
        }
    }

    fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo()
        serviceInfo.serviceName = Constants.SERVICE_NAME
        serviceInfo.serviceType = Constants.SERVICE_TYPE
        serviceInfo.port = port
        nsdManager = getSystemService(NSD_SERVICE) as NsdManager
        nsdManager!!.registerService(
            serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener
        )
    }

    @Throws(IOException::class)
    fun createServerSocketChannel(port_number: Int): SocketChannel {
        val serverSocket: ServerSocketChannel
        val serverClient: SocketChannel
        serverSocket = ServerSocketChannel.open()
        serverSocket.socket().bind(InetSocketAddress(port_number))
        serverClient = serverSocket.accept()
        serverClient.configureBlocking(false)
        return serverClient
    }

    @Throws(IOException::class)
    fun createServer(port_number: Int): Server {
        serverChannel = createServerSocketChannel(port_number)
        assert(serverChannel != null)
        Log.v(TAG, "Server Channel is set")
        return Server(serverChannel, networkQueue!!)
    }

    @Throws(IOException::class)
    fun serverCodeToRun(user_selection: String?) {
        if (user_selection == "Network Discovery") {
            Log.v(TAG, "Started Network Discovery with Server")
            initializeRegistrationListener()
            registerService(port_number)
            initializeServerSocket()
        } else {
            server = createServer(port_number)
            Log.v(TAG, "Server object is created")
            serverThread!!.start()
        }
    }

    fun initializeResolveListener() {
        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.v(TAG, "Resolve Succeeded. $serviceInfo")
                if (serviceInfo.serviceName == serviceName) {
                    Log.d(TAG, "Same IP.")
                    return
                }
                mService = serviceInfo
                client = createClient(mService!!.host, mService!!.port)
            }
        }
    }

    fun initializeDiscoveryListener() {
        // Instantiate a new DiscoveryListener
        discoveryListener = object : DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success$service")
                if (service.serviceType != Constants.SERVICE_TYPE) {
                    Log.d(TAG, "Unknown Service Type: " + service.serviceType)
                } else if (service.serviceName == serviceName) {
                    Log.d(TAG, "Same machine: $serviceName")
                } else if (service.serviceName.contains(Constants.SERVICE_NAME)) {
                    nsdManager!!.resolveService(service, resolveListener)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "service lost: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
                if (multicastLock != null) {
                    multicastLock!!.release()
                    multicastLock = null
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(
                    TAG,
                    "Discovery failed: Error code:$errorCode"
                )
                nsdManager!!.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(
                    TAG,
                    "Discovery failed: Error code:$errorCode"
                )
                nsdManager!!.stopServiceDiscovery(this)
            }
        }
    }

    // This code creates the socket channel that sets up communication
    fun createChannel(ip_address: InetAddress?, port_number: Int): SocketChannel? {
        try {
            socketChannel = SocketChannel.open()
            socketChannel!!.configureBlocking(false)
            val socketAddress: SocketAddress = InetSocketAddress(ip_address, port_number)
            socketChannel!!.connect(socketAddress)
            while (!socketChannel!!.finishConnect()) {
            }
            return socketChannel
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Exception by Socket Address")
            e.printStackTrace()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown by Socket Channel")
            e.printStackTrace()
        }
        return null
    }

    fun createClient(hostIPAddress: InetAddress?, port_number: Int): Client {
        socketChannel = createChannel(hostIPAddress, port_number)
        assert(socketChannel != null)
        Log.v(TAG, "Socket Channel is set")
        client = Client(socketChannel)
        return client!!
    }

    @Throws(UnknownHostException::class)
    fun clientCodeToRun(user_selection: String?, ip_address: String?) {
        if (user_selection == "Network Discovery") {
            Log.v(TAG, "Started Network Discovery with Client")
            initializeResolveListener()
            // These three lines are included for proper working of pixel only.
            wifi = this.getSystemService(WIFI_SERVICE) as WifiManager
            multicastLock = wifi!!.createMulticastLock("multicast lock")
            multicastLock!!.setReferenceCounted(true)
            initializeDiscoveryListener()
            nsdManager = getSystemService(NSD_SERVICE) as NsdManager
            multicastLock!!.acquire()
            nsdManager!!.discoverServices(
                Constants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener
            )
        } else {
            ip_bytes = convertBytes(ip_address)
            client = createClient(InetAddress.getByAddress(ip_bytes), port_number)
            Log.v(TAG, "Client created")
        }
    }

    fun convertBytes(ip_address: String?): ByteArray {
        val array = ip_address!!.split("\\.").toTypedArray()
        val result = ByteArray(array.size)
        for (i in array.indices) {
            result[i] = array[i].toInt().toByte()
        }
        return result
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        handler = Handler(Looper.getMainLooper())
        Log.v(TAG,"Start command called")
        if (intent != null) {
            if (intent.action == Constants.ACTION.START_SINGLE){
                Log.v(TAG, "Starting single phone activity")
                val file_name = intent.extras!!.getString("file_name")

                singleThread = Thread {// Call the method to get the required values
                    runnable = object : Runnable {
                        override fun run() {
                            Log.v(TAG, "putting data inside")
                            gv!!.values(false)
                            if (handler != null) handler!!.postDelayed(
                                this,
                                SET_TIMER.toLong()
                            )
                        }
                    }
                    gv = GetValues<Any?>(
                        this@YourService, file_name,
                        Constants.Type.SERVER, null
                    )
                    runnable!!.run()
                }
                singleThread!!.start()
                val policy = ThreadPolicy.Builder().permitAll().build()
                StrictMode.setThreadPolicy(policy)
                val lbm = LocalBroadcastManager.getInstance(this)
                lbm.sendBroadcast(Intent(Constants.ACTION.CLOSE_ACTION))
                startMyOwnForeground()
            }
            if (intent.action == Constants.ACTION.START_SERVER || intent.action == Constants.ACTION.START_CLIENT) {
                Log.v(TAG, "Start action called")
                val file_name = intent.extras!!.getString("file_name")
                val user_selection = intent.extras!!.getString("selection")
                val ip_address = intent.extras!!.getString("ip_address")
                port_number = intent.extras!!.getString("host_number")!!.toInt()
                if (intent.action == Constants.ACTION.START_SERVER) networkQueue = NetworkQueue()
                serverThread = Thread {
                    while (server != null) {
                        try {
                            server!!.readLine()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        try {
                            Thread.sleep(100000)
                        } catch (interruptedException: InterruptedException) {
                            interruptedException.printStackTrace()

                        }
                    }
                }
                clientThread = Thread {
                    runnable = object : Runnable {
                        override fun run() {
                            val latencyTimeTuple = gv!!.values()
                            if (client != null) {
                                if (latencyTimeTuple != null) {
                                    try {
                                        client!!.sendData(latencyTimeTuple)
                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                    }
                                }
                                Log.v(TAG, "Data Sent")
                            }
                            if (handler != null) handler!!.postDelayed(
                                this,
                                SET_TIMER.toLong()
                            )
                        }
                    }
                    var type =
                        Constants.Type.SERVER
                    if (intent.action == Constants.ACTION.START_SERVER) {
                        try {
                            serverCodeToRun(user_selection)
                        } catch (ignored: ClosedByInterruptException) {
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    } else {
                        type = Constants.Type.CLIENT
                        try {
                            clientCodeToRun(user_selection, ip_address)
                        } catch (e: UnknownHostException) {
                            e.printStackTrace()
                        }
                    }

                    gv = GetValues<Any?>(
                        this@YourService, file_name,
                        type, networkQueue
                    )
                    runnable!!.run()
                }
                clientThread!!.start()
                val policy = ThreadPolicy.Builder().permitAll().build()
                StrictMode.setThreadPolicy(policy)
                val lbm = LocalBroadcastManager.getInstance(this)
                lbm.sendBroadcast(Intent(Constants.ACTION.CLOSE_ACTION))
                startMyOwnForeground()
            } else if (intent.action == Constants.ACTION.STOP_ACTION) {
                Log.v(TAG, "Stopping Foreground Service and removing notification")
                try {
                    stopForeGroundService()
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
    }

    // This function is the foreground that must run
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun startMyOwnForeground() {
        val NOTIFICATION_CHANNEL_ID = "com.example.simpleapp"
        val channelName = "My Background Service"
        val chan = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        val stopIntent = Intent(this, YourService::class.java)
        stopIntent.action = Constants.ACTION.STOP_ACTION
        val pstopIntent = PendingIntent.getService(
            this, 0,
            stopIntent, 0
        )
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("App is running in background")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_stop, "STOP", pstopIntent)
            .build()
        startForeground(2, notification)
        val launchIntent = packageManager.getLaunchIntentForPackage(package_name)
            ?: throw RuntimeException("package $package_name should be in queries")
        val packageManager = this.packageManager
        if (launchIntent.resolveActivity(packageManager) != null) {
            startActivity(launchIntent)
        } else {
            Log.d(TAG, "The package provided is not launchable")
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun stopForeGroundService() {
        if (handler != null) {
            handler!!.removeCallbacks(runnable!!)
            handler = null
        }
        if (clientThread != null) {
            clientThread!!.interrupt()
            clientThread!!.join()
        }
        if (socketChannel != null) {
            socketChannel!!.close()
        }
        if (serverThread != null) {
            serverThread!!.interrupt()
            serverThread!!.join()
        }
        if (serverChannel != null) {
            serverChannel!!.close()
        }
        stopForeground(true)
        client = null
        server = null
        stopSelf()
    }

    companion object {
        private const val TAG = "YourService"
        const val SET_TIMER = 1000
    }
}