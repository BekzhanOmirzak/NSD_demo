package com.task.servicediscovery.main

import com.task.servicediscovery.model.NsdInfoModel

data class MainState(
    val services: List<NsdInfoModel> = emptyList(),
    val serviceName: String = "",
    val randomAvaiPort: Int = 0,
    val serviceStarted: Boolean = false,
    val receivedMessage: String = "",
    val messageToSend: String = "From Client",
    val mySN: String = "P211199V00225",
    val searchSN: String = "P211199V00225",
    val searching: Boolean = false
)