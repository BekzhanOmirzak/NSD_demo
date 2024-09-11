package com.task.servicediscovery.mapper

import android.net.nsd.NsdServiceInfo
import com.task.servicediscovery.model.NsdInfoModel
import java.net.InetAddress

fun NsdServiceInfo.toNsdServiceModel(): NsdInfoModel {
    return NsdInfoModel(
        name = this.serviceName,
        type = this.serviceType,
        reply = "",
        connected = false,
        ip = this.host,
        port = this.port
    )
}