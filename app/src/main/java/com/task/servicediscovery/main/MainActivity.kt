package com.task.servicediscovery.main

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.ResolveListener
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.task.servicediscovery.mapper.toNsdServiceModel
import com.task.servicediscovery.model.NsdInfoModel
import com.task.servicediscovery.ui.theme.ServiceDiscoveryTheme
import com.task.servicediscovery.utils.generatePortInRange
import com.task.servicediscovery.utils.getCurrentYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID


class MainActivity : ComponentActivity() {

    private val SERVICE_TYPE = "_flight_tech._tcp"
    private val TAG = MainActivity::class.java.name

    private var _viewState = MutableStateFlow(MainState())
    private var viewState = _viewState.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var mapServices = hashMapOf<String, Socket>()
    private var startingPort = 10_000
    private var endingPort = 50_000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ServiceDiscoveryTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    val state by viewState.collectAsStateWithLifecycle()

                    MainScreen(modifier = Modifier.padding(innerPadding),
                        state = state,
                        onIntent = {
                            when (it) {
                                is MainIntent.StartService -> {
                                    _viewState.update {
                                        it.copy(serviceStarted = true)
                                    }
                                    startServiceBroadcasting()
                                }

                                is MainIntent.StopService -> {
                                    _viewState.update {
                                        it.copy(serviceStarted = false)
                                    }
                                    stopServiceBroadcasting()
                                }

                                is MainIntent.ConnectService -> {
                                    connectToService(it.service)
                                }

                                is MainIntent.SendMessageToServices -> {
                                    sendingMessageToServices()
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
                                            mySN = it.str,
                                            serviceName = serviceName
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
                                    if (searching)
                                        discoverNearbyServices()
                                    else
                                        stopServiceDiscovery()
                                }
                            }
                        }
                    )
                }
            }
        }
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

    private fun sendingMessageToServices() {
        val message = _viewState.value.messageToSend
        if (message.isEmpty()) return
        println("Sending message to services : $message")
        Thread {
            for ((serviceName, socket) in mapServices) {
                val outputStream = socket.getOutputStream()
                PrintWriter(outputStream, true).println(message)
                val inputStream = socket.getInputStream()
                val response =
                    BufferedReader(InputStreamReader(inputStream)).readLine() ?: "no response"
                _viewState.update {
                    it.copy(
                        services = it.services.map { model ->
                            if (model.name == serviceName) {
                                model.copy(
                                    reply = response
                                )
                            } else
                                model
                        }
                    )
                }
                socket.close()
            }

            _viewState.update { state ->
                state.copy(
                    messageToSend = "",
                    services = state.services.map { item ->
                        item.copy(connected = false)
                    })
            }

            mapServices.clear()
        }.start()
    }

    private fun discoverNearbyServices() {
        (getSystemService(Context.NSD_SERVICE) as NsdManager).discoverServices(
            SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener
        )
    }

    private fun startServiceBroadcasting() {
        val state = _viewState.value
        val randomPort = generatePortInRange(
            from = startingPort,
            to = endingPort
        )
        serverSocket = ServerSocket(randomPort)
        val port = serverSocket?.localPort
        _viewState.update { it.copy(randomAvaiPort = port ?: 0) }
        registerService(port ?: 0, state.serviceName)
        Thread {
            while (true) {
                try {
                    val clientSocket = serverSocket?.accept() // Ожидаем подключений
                    println("After accepting client accept method")
                    handleClient(clientSocket) // Обрабатываем подключение
                } catch (e: IOException) {
                    e.printStackTrace()
                    break
                }
            }
        }.start()
    }

    private fun handleClient(clientSocket: Socket?) {
        clientSocket?.let {
            try {
                val inputStream = it.getInputStream()
                val reader = BufferedReader(InputStreamReader(inputStream))
                val message = reader.readLine() // Читаем сообщение клиента
                println("Message is being read : $message")
                _viewState.update { it.copy(receivedMessage = message) }

                val outputStream = it.getOutputStream()
                val writer = PrintWriter(outputStream, true)
                writer.println("Received by : ${_viewState.value.serviceName}")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun registerService(port: Int, name: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        (getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    private val registrationListener = object : NsdManager.RegistrationListener {

        override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
            // Save the service name. Android may have changed it in order to
            // resolve a conflict, so update the name you initially requested
            // with the name Android actually used.
            println("On Service registered $nsdServiceInfo")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Registration failed! Put debugging code here to determine why.
            println("Service registration failed : $serviceInfo errorCode: $errorCode")
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            // Service has been unregistered. This only happens when you call
            // NsdManager.unregisterService() and pass in this listener.
            println("Service registration unregistered : $arg0")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Unregistration failed. Put debugging code here to determine why.
            println("Service UnRegistration failed : $serviceInfo  ErrorCode: $errorCode")
        }
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {

        // Called as soon as service discovery begins.
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            // A service was found! Do something with it.
            println("Found Service Object : $service")
            val searchServiceName = getHashSHAFromSN(_viewState.value.searchSN)
            println("Searching Service Hash Value : $searchServiceName")
            if (service.serviceName != searchServiceName) {
                println("On match for service : $service while searching : $searchServiceName")
                return
            }

            Log.d(TAG, "Service found $service")
            (getSystemService(Context.NSD_SERVICE) as NsdManager)
                .resolveService(
                    service,
                    object : ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {

                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val fPort = serviceInfo.port
                            if (fPort !in startingPort..endingPort) {
                                println("Port not in range : $fPort")
                                return
                            }

                            addService(serviceInfo.toNsdServiceModel())
                        }
                    }
                )
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            Log.e(TAG, "Service lost: $service")
            removeService(service.serviceName)
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Start Discovery failed: Error code:$errorCode")

        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Stop Discovery failed: Error code:$errorCode")
        }
    }

    private fun connectToService(service: NsdInfoModel) {
        Thread {
            try {
                val socket = Socket(service.ip, service.port)
//                val outputStream = socket.getOutputStream()
//                val writer = PrintWriter(outputStream, true)
//                val inputStream = socket.getInputStream()
//                val reader = BufferedReader(InputStreamReader(inputStream))
                mapServices[service.name] = socket
                _viewState.update {
                    it.copy(
                        services = it.services.map { model ->
                            if (model.name == service.name)
                                model.copy(connected = true)
                            else
                                model
                        }
                    )
                }
                println("After connecting to the service name : ${service.name}")
            } catch (e: IOException) {
                Log.e(TAG, "Error connecting to service: ${e.message}")
            }
        }.start()
    }

    private fun addService(service: NsdInfoModel) {
        val services = viewState.value.services.toMutableList()
        services.add(service)
        _viewState.update { it.copy(services = services) }
    }

    private fun removeService(serviceName: String) {
        val services = viewState.value.services.toMutableList()
        services.removeIf { it.name == serviceName }
        _viewState.update { it.copy(services = services) }
    }

    private fun stopServiceBroadcasting() {
        serverSocket?.close()
        (getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            removeService(_viewState.value.serviceName)
            unregisterService(registrationListener)
        }
    }

    private fun stopServiceDiscovery() {
        (getSystemService(Context.NSD_SERVICE) as NsdManager).stopServiceDiscovery(
            discoveryListener
        )
        _viewState.update { it.copy(services = emptyList()) }
    }

}