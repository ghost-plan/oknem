package com.jamesfchen.vpn.protocol

import android.os.*
import android.util.Log
import com.jamesfchen.vpn.*
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * Copyright ® $ 2017
 * All right reserved.
 *
 * @author: hawks.jamesf
 * @since: Dec/19/2020  Sat
 */
const val T_TAG = "${Constants.TAG}/tdp"
const val TCP_HEADER_SIZE = 20
const val TCP_OPTION_HEADER_SIZE = 40

class TcpHandlerThread() : HandlerThread("tcp_thread") {

}

class TcpHandler(looper: Looper) : Handler(looper) {
    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        Log.d(T_TAG, "tcp message")
//        val packet = msg.obj as Packet
//        val myClient = AioSocketClient()
//        val destIp = packet.ipHeader.destinationAddresses.hostAddress
//        val destPort = (packet.tlHeader as TcpHeader).destinationPort
//        Log.d(T_TAG, "remote connect:${destIp}:${destPort}")
//        myClient.connect(destIp, destPort)
//        myClient.send(packet.buffer) { respBuffer ->
//            Log.d(T_TAG, "buffer size:${respBuffer.remaining()}")
//                                        pWriter.writePacket(Packet(header, respBuffer))
//        }
    }

}

data class ControlBit(val byteValue: Int) {
    /*
    Control Bits:  6 bits (from left to right):

    URG:  Urgent Pointer field significant
    ACK:  Acknowledgment field significant
    PSH:  Push Function
    RST:  Reset the connection
    SYN:  Synchronize sequence numbers
    FIN:  No more data from sender
     */
    val hasURG: Boolean = ((byteValue and 0b100000) == 0b100000)
    val hasACK: Boolean = ((byteValue and 0b010000) == 0b010000)
    val hasPSH: Boolean = ((byteValue and 0b001000) == 0b001000)
    val hasRST: Boolean = ((byteValue and 0b000100) == 0b000100)
    val hasSYN: Boolean = ((byteValue and 0b000010) == 0b000010)
    val hasFIN: Boolean = ((byteValue and 0b000001) == 0b000001)
    override fun toString(): String {
        return "ControlBit(hasURG=$hasURG, hasACK=$hasACK, hasPSH=$hasPSH, hasRST=$hasRST, hasSYN=$hasSYN, hasFIN=$hasFIN)"
    }


}

fun ByteBuffer.getTcpHeader(): TcpHeader {
    val buffer = this
    val sourcePort = buffer.getUShort()
    val destinationPort = buffer.getUShort()
    val sequenceNo = buffer.getUInt()
    val acknowledgmentNo = buffer.getUInt()
    val dataOffsetAndControlBitAndReserved = buffer.getUShort()
    val dataOffset = (dataOffsetAndControlBitAndReserved shr 12).toInt() * 4
    val controlBit = ControlBit(dataOffsetAndControlBitAndReserved and 0b0000000000_111111)
    val window = buffer.getUShort()
    val checksum = buffer.getUShort()
    val urgentPointer = buffer.getUShort()

    val tcph = TcpHeader(
        sourcePort,
        destinationPort,
        sequenceNo,
        acknowledgmentNo,
        dataOffset,
        controlBit,
        window,
        checksum,
        urgentPointer
    )
    val optionSize = dataOffset - TCP_HEADER_SIZE
    if (optionSize > 0) {
        //options
        val optionsAndPadding = ByteArray(optionSize)
        buffer.get(optionsAndPadding, 0, optionSize)
        tcph.optionsAndPadding = optionsAndPadding

    }
    return tcph
}

data class TcpHeader(
    val sourcePort: Int,
    val destinationPort: Int,
    val sequenceNo: Long,
    val acknowledgmentNo: Long,
    val dataOffset: Int,
    //    val reserved: Int
    val controlBit: ControlBit,
    val window: Int,
    val checksum: Int,
    val urgentPointer: Int
) : TransportLayerHeader {
    /*
       0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |          Source Port          |       Destination Port        |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                        Sequence Number                        |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                    Acknowledgment Number                      |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |  Data |           |U|A|P|R|S|F|                               |
       | Offset| Reserved  |R|C|S|S|Y|I|            Window             |
       |       |           |G|K|H|T|N|N|                               |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |           Checksum            |         Urgent Pointer        |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                    Options                    |    Padding    |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                             data                              |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    var optionsAndPadding: ByteArray? = null
    override fun toString(): String {
        return "TcpHeader(sourcePort=$sourcePort, destinationPort=$destinationPort, sequenceNo=$sequenceNo, acknowledgmentNo=$acknowledgmentNo, dataOffset=$dataOffset, controlBit=$controlBit, window=$window, checksum=$checksum, urgentPointer=$urgentPointer,optionsAndPadding=${optionsAndPadding?.size})"
    }

    override fun toByteBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(dataOffset)
        buffer.putUShort(sourcePort)
        buffer.putUShort(destinationPort)
        buffer.putUInt(sequenceNo)
        buffer.putUInt(acknowledgmentNo)
        val dataOffsetAndControlBitAndReserved =(dataOffset/4) shl 12 or controlBit.byteValue
        buffer.putUShort(dataOffsetAndControlBitAndReserved)
        buffer.putUShort(window)
        buffer.putUShort(checksum)
        buffer.putUShort(urgentPointer)
        buffer.put(ByteArray(20))
        buffer.flip()
        return buffer
    }
}