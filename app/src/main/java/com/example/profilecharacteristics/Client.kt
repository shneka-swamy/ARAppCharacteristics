package com.example.profilecharacteristics

import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class Client(var socketChannel: SocketChannel?) {
    var buffer = ByteBuffer.allocate(1024)
    val TAG = "CLIENT"
    @Throws(IOException::class)
    fun sendData(message: String) {
        if (message != "exit") {
            buffer.put(message.toByteArray())
            buffer.flip()
            socketChannel!!.write(buffer)
            buffer.clear()
        } else {
            socketChannel!!.close()
        }
    }

    @Throws(IOException::class)
    fun sendData(latencyTimeTuple: LatencyTimeTuple?) {
        if (latencyTimeTuple == null) {
            Log.e(TAG, "closing socketChannel")
            socketChannel!!.close()
            return
        }
        assert(socketChannel != null)
        assert(latencyTimeTuple.isValid)
        val bytebuffer: ByteBuffer = latencyTimeTuple.toBytes()
        bytebuffer.rewind()
        Log.d(TAG, "sending data to server plz get it, plz")
        socketChannel!!.write(bytebuffer)

        //bytebuffer.rewind();
        // Attempting again for verification
        //this.socketChannel.write(bytebuffer);
        bytebuffer.clear()
    }
}