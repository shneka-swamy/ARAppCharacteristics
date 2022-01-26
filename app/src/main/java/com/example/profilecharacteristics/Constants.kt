package com.example.profilecharacteristics

object Constants {
    const val SERVICE_TYPE = "_http._tcp."
    const val SERVICE_NAME = "GetData"

    interface ACTION {
        companion object {
            const val STOP_ACTION = "com.example.profilecharacteristics.action.stop"
            const val START_ACTION = "com.example.profilecharacteristics.action.start"
            const val CLOSE_ACTION = "com.example.profilecharacteristics.action.close"
            const val START_SERVER = "com.example.profilecharacteristics.action.start_server"
            const val START_CLIENT = "com.example.profilecharacteristics.action.start_client"
            const val START_SINGLE = "com.example.profilecharacteristics.action.start_single"
        }
    }

    enum class Choice {
        NSD, USERDATA
    }

    enum class Type {
        CLIENT, SERVER, SINGLE
    }
}