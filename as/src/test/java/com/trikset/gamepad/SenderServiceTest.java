package com.trikset.gamepad

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
class SenderServiceTest {
    @Test
    @Throws(InterruptedException::class)
    fun senderServiceShouldConnectToServerSuccessfullyAfterSendingCommand() {
        val server = DummyServer(1, 4)
        val client = SenderService()
        client.setTarget(DummyServer.Companion.IP, DummyServer.Companion.DEFAULT_PORT + 4)
        client.send("")
        Thread.sleep(200)
        Assert.assertTrue(server.isConnected)
    }

    @Test
    @Throws(InterruptedException::class)
    fun senderServiceShouldSendSingleCommandCorrectly() {
        val server = DummyServer(1, 0)
        val client = SenderService()
        client.setTarget(DummyServer.Companion.IP, DummyServer.Companion.DEFAULT_PORT)
        client.setKeepaliveTimeout(10000000) // to disable keep-alive messages
        client.send("Test; check")
        Thread.sleep(100)
        Assert.assertEquals("Test; check", server.lastCommand)
    }

    @Test
    @Throws(InterruptedException::class)
    fun senderServiceShouldSendMultipleCommandsCorrectly() {
        val server = DummyServer(5, 1)
        val client = SenderService()
        client.setTarget(DummyServer.Companion.IP, DummyServer.Companion.DEFAULT_PORT + 1)
        client.setKeepaliveTimeout(10000000) // to disable keep-alive messages
        for (i in 0..4) {
            client.send(String.format("%d checking", i))
            Thread.sleep(100)
        }
        Assert.assertEquals("4 checking", server.lastCommand)
    }

    @Test
    fun setTargetShouldSetServerSuccessfully() {
        val client = SenderService()
        client.setTarget("someaddr-test", 12345)
        Assert.assertEquals("someaddr-test", client.hostAddr)
    }

    @Test
    fun senderServiceShouldReturnCorrectKeepaliveTimeout() {
        val client = SenderService()
        client.setKeepaliveTimeout(3453)
        Assert.assertEquals(3453, client.getKeepaliveTimeout().toLong())
        client.setKeepaliveTimeout(1234)
        Assert.assertEquals(1234, client.getKeepaliveTimeout().toLong())
    }

    private inner class DummyServer internal constructor(cmdNumber: Int, portShift: Int) {
        val port: Int
        var isConnected = false

        var lastCommand: String? = null

        companion object {
            const val IP = "localhost"
            const val DEFAULT_PORT = 12345
        }

        init {
            port = Companion.DEFAULT_PORT + portShift
            val serverThread = Thread(Runnable {
                try {
                    ServerSocket(port).use { server ->
                        val client = server.accept()
                        isConnected = true
                        val clientInput = BufferedReader(InputStreamReader(client.getInputStream()))
                        for (i in 0 until cmdNumber) {
                            lastCommand = clientInput.readLine()
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            })
            serverThread.start()
        }
    }
}