package com.example.profilecharacteristics

import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class Server(var socketChannel: SocketChannel?, networkQueue: NetworkQueue) {
    val TAG = "Server"
    var byteBuffer = ByteBuffer.allocate(LatencyTimeTuple.capacity())
    var line = 0
    val networkQueue: NetworkQueue
    @Throws(IOException::class)
    fun readLine() {
        line = socketChannel!!.read(byteBuffer)
        while (line != -1) {
            if (line != 0) {
                Log.d(TAG, "Got it, thanx")
                val latencyTimeTuple = LatencyTimeTuple(byteBuffer)
                Log.d(TAG, "got $latencyTimeTuple")
                if (!latencyTimeTuple.isValid) {
                    Log.d(TAG, "Data is invalid")
                    break
                }
                Log.v(TAG, "adding to networkQueue: $latencyTimeTuple")
                networkQueue.queue.add(latencyTimeTuple)
            }
            byteBuffer.clear()
            line = socketChannel!!.read(byteBuffer)
        }
        Log.v(TAG, "Closing the server socket")
        socketChannel!!.close()
    }

    init {
        assert(socketChannel != null)
        this.networkQueue = networkQueue
    }
}