package com.example.profilecharacteristics

import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.experimental.and

class LatencyTimeTuple {
    val startTime: Long
    val endTime: Long

    constructor() {
        startTime = 0
        endTime = 0
    }

    constructor(startTime: Long, endTime: Long) {
        this.startTime = startTime
        this.endTime = endTime
    }

    constructor(buffer: ByteBuffer?) {
        assert(buffer != null)
        assert(buffer!!.capacity() == capacity())
        val b = buffer.array()
        var result: Long = 0
        for (i in 0 until java.lang.Long.BYTES) {
            result = result shl java.lang.Byte.SIZE
            result = result or (b[i] and 0xFF.toByte()).toLong()
        }
        startTime = result
        result = 0
        for (i in 0 until java.lang.Long.BYTES) {
            result = result shl java.lang.Byte.SIZE
            result = result or (b[java.lang.Long.BYTES + i] and 0xFF.toByte()).toLong()
        }
        endTime = result
    }

    val isValid: Boolean
        get() = startTime != 0L && endTime != 0L

    fun toBytes(): ByteBuffer {
        val buffer = ByteBuffer.allocate(capacity())
        val buf = ByteArray(capacity())
        var l = startTime
        for (i in java.lang.Long.BYTES - 1 downTo 0) {
            buf[i] = (l and 0xFF).toByte()
            l = l shr java.lang.Byte.SIZE
        }
        l = endTime
        for (i in java.lang.Long.BYTES - 1 downTo 0) {
            buf[java.lang.Long.BYTES + i] = (l and 0xFF).toByte()
            l = l shr java.lang.Byte.SIZE
        }
        buffer.put(buf)
        return buffer
    }

    override fun toString(): String {
        return "Start: " + stringTime(startTime) + " End: " + stringTime(endTime)
    }

    companion object {
        fun stringTime(timeRecv: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(timeRecv)
        }

        fun capacity(): Int {
            return java.lang.Long.BYTES * 2
        }
    }
}