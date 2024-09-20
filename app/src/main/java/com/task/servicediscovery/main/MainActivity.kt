package com.task.servicediscovery.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification.Action
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.task.servicediscovery.receiver.WiFiDirectBroadcastReceiver
import com.task.servicediscovery.ui.theme.ServiceDiscoveryTheme
import com.task.servicediscovery.utils.getCurrentYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest


class MainActivity : ComponentActivity(), ConnectionInfoListener {

    private val SERVICE_TYPE = "_flight_tech._tcp"
    private val TAG = MainActivity::class.java.name

    private var _viewState = MutableStateFlow(MainState())
    private var viewState = _viewState.asStateFlow()

    private var startingPort = 10_000
    private var endingPort = 50_000

    private var SERVER_PORT = 48_321

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private lateinit var intentFilter: IntentFilter

    private var record: HashMap<String, String>? = null
    private var serviceInfo: WifiP2pDnsSdServiceInfo? = null

    private var recordListener: WifiP2pManager.DnsSdTxtRecordListener? = null
    private var serviceResponseListener: WifiP2pManager.DnsSdServiceResponseListener? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null

    private var mServiceBroadcastingHandler = Handler(Looper.getMainLooper())
    private var mServiceDiscovering: Handler? = null

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check the results of each requested permission
            permissions.entries.forEach {
                when (it.key) {
                    Manifest.permission.CHANGE_WIFI_STATE -> {
                        if (it.value) {
                            // Permission granted
                            Toast.makeText(this, "CHANGE_WIFI_STATE granted", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            // Permission denied
                            Toast.makeText(this, "CHANGE_WIFI_STATE denied", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                    Manifest.permission.ACCESS_WIFI_STATE -> {
                        if (it.value) {
                            Toast.makeText(this, "ACCESS_WIFI_STATE granted", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(this, "ACCESS_WIFI_STATE denied", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                    Manifest.permission.ACCESS_FINE_LOCATION -> {
                        if (it.value) {
                            Toast.makeText(this, "ACCESS_FINE_LOCATION granted", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(this, "ACCESS_FINE_LOCATION denied", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                    Manifest.permission.NEARBY_WIFI_DEVICES -> {
                        if (it.value) {
                            Toast.makeText(this, "NEARBY_WIFI_DEVICES granted", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(this, "NEARBY_WIFI_DEVICES denied", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        }

    private fun requestPermissionsIfNecessary() {
        // List of required permissions
        val requiredPermissions = mutableListOf(
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // NEARBY_WIFI_DEVICES is required for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        // Filter out permissions that are already granted
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        // Request missing permissions
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Toast.makeText(this, "All permissions are already granted", Toast.LENGTH_SHORT).show()
        }
    }


    private fun initializeWiFiP2P() {
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apiLevel = Build.VERSION.SDK_INT
        println("Api Level now is : $apiLevel")
        setContent {
            ServiceDiscoveryTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    val state by viewState.collectAsStateWithLifecycle()

                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        state = state,
                        onIntent = {
                            when (it) {
                                is MainIntent.StartService -> {
                                    _viewState.update {
                                        it.copy(serviceStarted = true)
                                    }
                                    registerLocalService()
                                }

                                is MainIntent.StopService -> {
                                    _viewState.update {
                                        it.copy(serviceStarted = false)
                                    }
                                    unregisterLocalService()
                                }

                                is MainIntent.ConnectService -> {
                                }

                                is MainIntent.SendMessageToServices -> {
                                }

                                is MainIntent.EnterMessage -> {
                                    _viewState.update { state ->
                                        state.copy(messageToSend = it.text)
                                    }
                                }

                                is MainIntent.EnterMySN -> {
                                    val serviceName = getHashSHAFromSN(it.str)
                                    _viewState.update { state ->
                                        state.copy(
                                            mySN = it.str, serviceName = serviceName
                                        )
                                    }
                                }

                                is MainIntent.EnterSearchSN -> {
                                    _viewState.update { state ->
                                        state.copy(
                                            searchSN = it.str
                                        )
                                    }
                                }

                                MainIntent.SearchingClick -> {
                                    val searching = !_viewState.value.searching
                                    _viewState.update {
                                        it.copy(searching = searching)
                                    }
                                    if (searching) {
                                        mServiceDiscovering = Handler(Looper.getMainLooper())
                                        discoverServiceStart()
                                    } else mServiceDiscovering = null
                                }
                            }
                        })
                }
            }
        }

        initializeWiFiP2P()
        requestPermissionsIfNecessary()
    }

    @SuppressLint("MissingPermission")
    private fun registerLocalService() {
        //  Create a string map containing information about your service.
        record = hashMapOf(
            "listenport" to SERVER_PORT.toString(),
            "buddyname" to "Second${(Math.random() * 1000).toInt()}",
            "available" to "visible"
        )

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.


        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        manager.clearLocalServices(channel, object : ActionListener {
            override fun onSuccess() {

                println("onSuccess on clearing Local Services")

                serviceInfo =
                    WifiP2pDnsSdServiceInfo.newInstance("_secondt", "_presence2._tcp", record)

                manager.addLocalService(channel, serviceInfo, object : ActionListener {
                    override fun onSuccess() {
                        // Command successful! Code isn't necessarily needed here,
                        // Unless you want to update the UI or add logging statements.
                        println("On Success registration the service")
                        mServiceBroadcastingHandler.postDelayed(
                            mServiceBroadcastingRunnable, 12_000L
                        )
                    }

                    override fun onFailure(arg0: Int) {
                        // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
                        println("On Failure registration the service : $arg0")
                    }
                })
            }

            override fun onFailure(reason: Int) {
                println("onFailure on clearing Local Services")
            }
        })
    }

    private val mServiceBroadcastingRunnable: Runnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            manager.discoverPeers(channel, object : ActionListener {
                override fun onSuccess() {
                    println("Discovering peers called successfully")
                }

                override fun onFailure(error: Int) {
                    println("Discovering peers called with error: $error")
                }
            })
            mServiceBroadcastingHandler.postDelayed(this, 3000L)
        }
    }

    @SuppressLint("MissingPermission")
    private fun unregisterLocalService() {
        serviceInfo?.let {
            manager.removeLocalService(channel, serviceInfo, object : ActionListener {
                override fun onSuccess() {
                    println("On Success removing the service")
                }

                override fun onFailure(arg0: Int) {
                    println("On Failure removing the service : $arg0")
                }
            })
        }
    }

    private val buddies = mutableMapOf<String, String>()

    @SuppressLint("MissingPermission")
    private fun discoverServiceStart() {
        // Create a DNS-SD TXT record listener
        recordListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
            println("DnsSdTxtRecord available - $record")

            // Extract the buddy name from the TXT record and associate it with the device address
            record["buddyname"]?.let {
                buddies[device.deviceAddress] = it
            }
        }

        // Create a DNS-SD service response listener
        serviceResponseListener =
            WifiP2pManager.DnsSdServiceResponseListener { instanceName: String,
                                                          registrationType: String,
                                                          resourceType: WifiP2pDevice ->
                println(
                    "onBonjourServiceAvailable - $instanceName, device: ${resourceType.deviceName}"
                )

                // Use the buddy name from the TXT record, if available
                resourceType.deviceName =
                    buddies[resourceType.deviceAddress] ?: resourceType.deviceName
                connectToService(resourceType)
            }

        manager.setDnsSdResponseListeners(channel, serviceResponseListener, recordListener)

        manager.clearServiceRequests(channel, object : ActionListener {
            override fun onSuccess() {
                println("Service request removed successfully")

                // Create a service request
                val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()

                // Add the service request and start discovering services
                manager.addServiceRequest(channel, serviceRequest, object : ActionListener {
                    override fun onSuccess() {
                        println("Service request added successfully")

                        // Start service discovery
                        manager.discoverServices(channel, object : ActionListener {
                            override fun onSuccess() {
                                println("Service discovery initiated")
                                mServiceDiscovering?.postDelayed(
                                    {
                                        discoverServiceStart()
                                    }, 10_000L
                                )
                            }

                            override fun onFailure(reason: Int) {
                                println("Service discovery failed: $reason")
                            }
                        })

                    }

                    override fun onFailure(reason: Int) {
                        println("Failed to add service request: $reason")
                    }
                })
            }

            override fun onFailure(reason: Int) {
                println("On Failure clear services requests: $reason")
            }

        })

    }

    @SuppressLint("MissingPermission")
    private fun connectToService(device: WifiP2pDevice) {
        // Create WifiP2pConfig object to initiate the connection
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC  // You can change to WpsInfo.KEYPAD if required
        }

        // Initiate connection with the discovered device
        manager.connect(channel, config, object : ActionListener {
            override fun onSuccess() {
                println("Connection initiated with ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                println("Connection failed: $reason")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }


    private fun getHashSHAFromSN(sn: String): String {
        val name = "POS" + getCurrentYearMonth() + sn
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(name.toByteArray())
        val hexString = StringBuilder()
        for (b in hash) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        val min = Math.max(0, hexString.length - 6)
        return hexString.substring(min).toString()
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        println("onConnectionInfoAvailable object : $info")
        val isGroupOwner = info?.isGroupOwner ?: return

        // Get the group owner's IP address
        val groupOwnerAddress = info.groupOwnerAddress?.hostAddress ?: return
        _viewState.update { it.copy(mySN = "IsGroup Owner : $isGroupOwner") }
        if (isGroupOwner) {
            println("I am the group owner. Waiting for a connection.")
            // Start a server to listen for incoming connections
            startServerSocket()
        } else {
            println("I am not the group owner. Connecting to the group owner at $groupOwnerAddress.")
            // Connect to the group owner's server
            connectToServer(groupOwnerAddress)
        }
    }

    private fun startServerSocket() {
        Thread {
            try {
                // Create a server socket to listen for incoming connections
                val serverSocket = ServerSocket(8800)
                println("Server: Socket opened, waiting for connection...")

                // Accept an incoming connection
                val clientSocket = serverSocket.accept()
                println("Server: Connection established with a peer.")

                // Receive data from the peer
                val inputStream = clientSocket.getInputStream()
                val receivedData = ByteArray(1024)
                inputStream.read(receivedData)
                println("Received data: ${String(receivedData)}")

                // Optionally, send data back to the peer
                val outputStream = clientSocket.getOutputStream()
                val message = "Hello from server!"
                outputStream.write(message.toByteArray())

                // Close the socket
                clientSocket.close()
                serverSocket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun connectToServer(groupOwnerAddress: String) {
        Thread {
            try {
                // Create a socket and connect to the group owner (server)
                val socket = Socket()
                socket.connect(InetSocketAddress(groupOwnerAddress, 8800), 5000)
                println("Client: Connected to server.")

                // Send data to the server
                val outputStream = socket.getOutputStream()
                val message = "Hello from client!"
                outputStream.write(message.toByteArray())

                // Receive data from the server
                val inputStream = socket.getInputStream()
                val receivedData = ByteArray(1024)
                inputStream.read(receivedData)
                println("Received data: ${String(receivedData)}")

                // Close the socket
                socket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.start()
    }

}