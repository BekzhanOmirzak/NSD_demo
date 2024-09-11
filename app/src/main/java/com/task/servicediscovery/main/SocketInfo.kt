package com.task.servicediscovery.main

import java.io.BufferedReader
import java.io.PrintWriter
import java.net.Socket

class SocketInfo(
    val socket: Socket,
    val writer: PrintWriter,
    val reader: BufferedReader
)