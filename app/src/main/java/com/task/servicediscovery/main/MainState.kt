package com.task.servicediscovery.main

import com.task.servicediscovery.model.NsdInfoModel

data class MainState(
    val services: List<NsdInfoModel> = emptyList(),
    val randomDeviceName: String = "3d1c",
    val randomAvaiPort: Int = 0,
    val serviceStarted: Boolean = false,
    val receivedMessage: String = "",
    val messageToSend: String = "From Client",
)