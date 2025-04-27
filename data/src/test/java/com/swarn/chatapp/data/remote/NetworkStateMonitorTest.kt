package com.swarn.chatapp.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkStateMonitorTest {
    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var network: Network
    private lateinit var networkCapabilities: NetworkCapabilities
    private lateinit var networkStateMonitor: NetworkStateMonitorImpl
    private val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
    private lateinit var networkRequestBuilder: NetworkRequest.Builder
    private lateinit var networkRequest: NetworkRequest

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        network = mockk(relaxed = true)
        networkCapabilities = mockk(relaxed = true)
        networkRequest = mockk(relaxed = true)
        networkRequestBuilder = mockk(relaxed = true)

        mockkConstructor(NetworkRequest.Builder::class)
        every { anyConstructed<NetworkRequest.Builder>().addCapability(any()) } returns networkRequestBuilder
        every { anyConstructed<NetworkRequest.Builder>().build() } returns networkRequest

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns networkCapabilities
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        every { connectivityManager.registerNetworkCallback(any(), capture(callbackSlot)) } returns Unit

        networkStateMonitor = NetworkStateMonitorImpl(context)
    }

    @Test
    fun `when network is available, should emit true`() = runTest {
        // Given initial state is true
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        // When
        val initialState = networkStateMonitor.observe().first()

        // Then
        assertEquals(true, initialState)

        // When network becomes available again
        callbackSlot.captured.onAvailable(network)

        // Then
        assertEquals(true, networkStateMonitor.observe().first())
    }

    @Test
    fun `when network is lost, should emit false`() = runTest {
        // Given initial state is true
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        
        // When network is lost
        callbackSlot.captured.onLost(network)

        // Then
        assertEquals(false, networkStateMonitor.observe().first())
    }

    @Test
    fun `when network capabilities change, should emit based on internet capability`() = runTest {
        // Given
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true

        // When capabilities change with internet
        callbackSlot.captured.onCapabilitiesChanged(network, networkCapabilities)

        // Then
        assertEquals(true, networkStateMonitor.observe().first())
    }

    @Test
    fun `when network capabilities change without internet, should emit false`() = runTest {
        // Given
        every { networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false

        // When capabilities change without internet
        callbackSlot.captured.onCapabilitiesChanged(network, networkCapabilities)

        // Then
        assertEquals(false, networkStateMonitor.observe().first())
    }
} 