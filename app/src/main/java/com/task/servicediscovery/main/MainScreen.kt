package com.task.servicediscovery.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.task.servicediscovery.model.NsdInfoModel

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    state: MainState = MainState(),
    onIntent: (MainIntent) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
    ) {
        Column(
            modifier = modifier
                .background(color = Color.White)
                .fillMaxSize()
                .align(Alignment.TopStart)
        ) {
            NearbyServices(state = state, onIntent = onIntent)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(alignment = Alignment.BottomEnd),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (state.serviceStarted) {
                            onIntent(MainIntent.StopService)
                        } else
                            onIntent(MainIntent.StartService)
                    }
                ) {
                    val btnText = if (state.serviceStarted) "Stop service" else "Start service"
                    Text(text = btnText)
                }

                Text(text = "Name : ${state.randomDeviceName} at Port : ${state.randomAvaiPort}")
            }
            Text(
                text = "Received message : ${state.receivedMessage}",
                modifier = Modifier.padding(
                    top = 10.dp,
                    bottom = 10.dp
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextField(
                    value = state.messageToSend,
                    onValueChange = {
                        onIntent(MainIntent.EnterText(it))
                    },
                    label = {
                        Text(text = "Message")
                    }
                )

                Button(
                    onClick = {
                        onIntent(MainIntent.SendMessageToServices)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Send")
                }
            }
        }
    }
}

@Composable
fun NearbyServices(state: MainState, onIntent: (MainIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "Nearby Services", fontSize = 18.sp, color = Color.Black)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Name")

            Text(text = "Status")

            Text(text = "Replies")
        }

        Column(
            modifier = Modifier.padding(top = 10.dp)
        ) {
            state.services.forEach { service ->
                ServiceItem(service = service, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun ServiceItem(
    service: NsdInfoModel,
    onIntent: (MainIntent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onIntent(MainIntent.ConnectService(service))
            },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = service.name)

        val connected = if (service.connected) "connected" else "not connected"
        Text(text = connected)

        Text(text = service.reply)
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    MainScreen(
        state = MainState(
            services = listOf(
                NsdInfoModel(),
                NsdInfoModel(),
            )
        )
    )
}