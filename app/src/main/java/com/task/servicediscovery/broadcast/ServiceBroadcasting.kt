package com.task.servicediscovery.broadcast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class ServiceBroadcasting(
    val context: Context,
) {

    fun registerService(port: Int, name: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        (context.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    fun stopService() {

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

    companion object {
        private val SERVICE_TYPE = "_flight_tech._tcp"
    }

}