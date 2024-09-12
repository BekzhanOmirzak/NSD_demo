package com.task.servicediscovery.main

import com.task.servicediscovery.model.NsdInfoModel

sealed interface MainIntent {

    data object StartService : MainIntent

    data object StopService : MainIntent

    data class ConnectService(val service: NsdInfoModel) : MainIntent

    data object SendMessageToServices : MainIntent

    data class EnterMessage(val text: String) : MainIntent

    data class EnterMySN(val str: String) : MainIntent

    data class EnterSearchSN(val str: String) : MainIntent

}