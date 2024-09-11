package com.task.servicediscovery.main

import android.net.nsd.NsdServiceInfo
import com.task.servicediscovery.model.NsdInfoModel

sealed interface MainIntent {

    data object StartService : MainIntent

    data object StopService : MainIntent

    data class ConnectService(val service: NsdInfoModel) : MainIntent

    data object SendMessageToServices : MainIntent

    data class EnterText(val text: String) : MainIntent

}