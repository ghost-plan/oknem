package com.jamesfchen.vpn

import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.jamesfchen.vpn.Constants.TAG
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.internal.threadFactory
import okio.ByteString
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.ArrayDeque
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Copyright ® $ 2017
 * All right reserved.
 *
 * @author: hawks.jamesf
 * @since: Dec/20/2020  Sun
 */
const val C_TAG = "${Constants.TAG}/cli"

interface Connection {
    companion object {
        fun createAndConnect(ip: String, port: Int, aioSocket: Boolean): Connection {
            if (aioSocket) {
                val asynSocketChannel: AsynchronousSocketChannel = AsynchronousSocketChannel.open()
                asynSocketChannel.connect(InetSocketAddress(ip, port)).get()
                return AioSocketConnection(asynSocketChannel)
            } else {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port))
                socket.keepAlive =true
                return BioSocketConnection(socket)
            }
        }

        fun <A> createAndConnect(
            ip: String,
            port: Int,
            handler: CompletionHandler<Void, A?>,
            attachment: A? = null
        ): Connection {
            val asynSocketChannel: AsynchronousSocketChannel = AsynchronousSocketChannel.open()
            asynSocketChannel.connect(InetSocketAddress(ip, port), attachment, handler)
            return AioSocketConnection(asynSocketChannel)

        }

    }

    // -- asynchronous operations --

    val remoteAddress: InetSocketAddress?
    val localAddress: InetSocketAddress?
    fun send(reqBuffer: ByteBuffer, block: (respBuffer: ByteBuffer) -> Unit)
    fun <A> send(
        reqBuffer: ByteBuffer,
        handler: CompletionHandler<Int, A?>,
        attachment: A? = null
    ) {
    }
}

class BioSocketConnection(val socket: Socket) : Connection {
    val outputStream = socket.getOutputStream()
    val inputStream = socket.getInputStream()
    override var remoteAddress: InetSocketAddress? =
        socket.remoteSocketAddress as InetSocketAddress?
    override var localAddress: InetSocketAddress? = socket.localSocketAddress as InetSocketAddress?

    companion object {
        const val BIO_TAG = "${C_TAG}/BioSocket"
    }

    override fun send(reqBuffer: ByteBuffer, block: (respBuffer: ByteBuffer) -> Unit) {
        try {
            outputStream.write(reqBuffer.array())
            val ba = ByteArray(com.jamesfchen.vpn.protocol.BUFFER_SIZE)
            val len = inputStream.read(ba)
            val byteBuffer = if (len > 0) {
                val wrap = ByteBuffer.wrap(ba)
                wrap.limit(len)
                wrap
            } else {
                ByteBuffer.wrap("".toByteArray())
            }
            block(byteBuffer)
        } catch (e: Exception) {
            Log.d(BIO_TAG, Log.getStackTraceString(e))
        }
    }

}

class AioSocketConnection(
    val asynSocketChannel: AsynchronousSocketChannel
) : Connection {
    companion object {
        const val AIO_TAG = "${C_TAG}/AioSocket"
    }

    override var remoteAddress: InetSocketAddress? =
        asynSocketChannel.remoteAddress as InetSocketAddress?
    override var localAddress: InetSocketAddress? =
        asynSocketChannel.localAddress as InetSocketAddress?

    @WorkerThread
    override fun send(reqBuffer: ByteBuffer, block: (respBuffer: ByteBuffer) -> Unit) {

        try {
            asynSocketChannel.write(reqBuffer).get()
            val byteBuffer: ByteBuffer =
                ByteBuffer.allocate(com.jamesfchen.vpn.protocol.BUFFER_SIZE)
            asynSocketChannel.read(byteBuffer).get()
            byteBuffer.flip()
            block(byteBuffer)
        } catch (e: Exception) {
            Log.d(AIO_TAG, Log.getStackTraceString(e))
        }
    }

    @MainThread
    override fun <A> send(
        reqBuffer: ByteBuffer,
        handler: CompletionHandler<Int, A?>,
        attachment: A?
    ) {

        try {
            asynSocketChannel.write(reqBuffer, attachment, handler)
            val byteBuffer: ByteBuffer =
                ByteBuffer.allocate(com.jamesfchen.vpn.protocol.BUFFER_SIZE)
            asynSocketChannel.read(byteBuffer, attachment, handler)
            byteBuffer.flip()
        } catch (e: Exception) {
            Log.d(AIO_TAG, Log.getStackTraceString(e))
        }
    }
}

class MyWebSocketListener : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
//        ByteBuffer
//        IntBuffer
//        StringBuffer
        Log.d(C_TAG, "onMessage bytes:$bytes")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        Log.d(C_TAG, "onMessage text:$text")
    }
}

class ConnectionPool() {
    companion object {
        private val executer = ThreadPoolExecutor(
            0,
            Int.MAX_VALUE,
            60L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            threadFactory("vpn connection pool", true)
        )
    }

    private val connections = ArrayDeque<Connection>()

    fun put(conn: Connection) {
        connections.add(conn)
    }

    fun get(key: String): Connection {
        for (c in connections) {
            if (c.remoteAddress != null && "${c.remoteAddress!!.address}:${c.remoteAddress!!.port}" == key) {
                Log.d(
                    TAG,
                    "socket remote:${c.remoteAddress} local:${c.localAddress}"
                )
                return c
            }

        }
        val (destIp,destPort) = key.split(":")
        val myClient = Connection.createAndConnect(
            destIp,
            destPort.toInt(),
            aioSocket = true
        )
        Log.d(
            TAG,
            "socket remote:${myClient.remoteAddress} local:${myClient.localAddress}"
        )
        connections.add(myClient)
        return myClient
    }
}