package com.lashla.chillitest

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Looper
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectivityTest {
    @Test
    fun unvalidatedCallbackQueuedDuringRegistrationWinsOverInitialOnlineSample() {
        val capabilities = NetworkCapabilities()
        assertTransition(initial = true, expected = false) { callback ->
            callback.onCapabilitiesChanged(network(), capabilities)
        }
    }

    @Test
    fun lossQueuedDuringRegistrationWinsOverInitialOnlineSample() {
        assertTransition(initial = true, expected = false) { callback ->
            callback.onLost(network())
        }
    }

    private fun assertTransition(
        initial: Boolean,
        expected: Boolean,
        notify: (ConnectivityManager.NetworkCallback) -> Unit,
    ) = runBlocking {
        var unregisterCalls = 0
        var registered: ConnectivityManager.NetworkCallback? = null
        val states = withTimeout(TEST_TIMEOUT_MILLIS) {
            withContext(Dispatchers.Default) {
                connectivityState(
                    register = { callback, handler ->
                        assertSame(Looper.getMainLooper(), Looper.myLooper())
                        assertSame(Looper.getMainLooper(), handler.looper)
                        registered = callback
                        handler.post { notify(callback) }
                    },
                    unregister = { callback ->
                        assertSame(Looper.getMainLooper(), Looper.myLooper())
                        assertSame(registered, callback)
                        unregisterCalls++
                    },
                    isOnline = {
                        assertSame(Looper.getMainLooper(), Looper.myLooper())
                        initial
                    },
                ).take(2).toList()
            }
        }
        assertEquals(listOf(initial, expected), states)
        assertEquals(1, unregisterCalls)
    }

    private fun network(): Network {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeInt(1)
            parcel.setDataPosition(0)
            Network.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 5000L
    }
}
