package com.task.servicediscovery.model

import java.net.InetAddress

data class NsdInfoModel(
    val name: String = "Ter 1",
    val type: String = "Type 1",
    val reply: String = "Reply 1",
    val connected: Boolean = false,
    val ip: InetAddress = InetAddress.getLocalHost(),
    val port: Int = 0
)