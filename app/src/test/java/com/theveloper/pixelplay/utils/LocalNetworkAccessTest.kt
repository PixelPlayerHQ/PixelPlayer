package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalNetworkAccessTest {

    @Test
    fun extractHost_handlesSchemesPortsPathsAndIpv6() {
        assertThat(LocalNetworkAccess.extractHost("http://192.168.1.10:4533")).isEqualTo("192.168.1.10")
        assertThat(LocalNetworkAccess.extractHost(" https://music.example.com/rest ")).isEqualTo("music.example.com")
        assertThat(LocalNetworkAccess.extractHost("192.168.1.10:8096")).isEqualTo("192.168.1.10")
        assertThat(LocalNetworkAccess.extractHost("http://[fd00::1]:4533")).isEqualTo("fd00::1")
        assertThat(LocalNetworkAccess.extractHost("")).isNull()
        assertThat(LocalNetworkAccess.extractHost(null)).isNull()
    }

    @Test
    fun isLocalNetworkAddress_acceptsPrivateLinkLocalAndUniqueLocal() {
        listOf("10.0.0.5", "172.16.3.4", "192.168.1.10", "169.254.10.20", "fe80::1", "fd12:3456::1")
            .forEach { assertThat(LocalNetworkAccess.isLocalNetworkAddress(InetAddress.getByName(it))).isTrue() }
    }

    @Test
    fun isLocalNetworkAddress_rejectsLoopbackAndPublicAddresses() {
        listOf("127.0.0.1", "::1", "0.0.0.0", "8.8.8.8", "2606:4700::1111", "172.32.0.1")
            .forEach { assertThat(LocalNetworkAccess.isLocalNetworkAddress(InetAddress.getByName(it))).isFalse() }
    }

    @Test
    fun isLocalNetworkUrl_detectsLiteralLanAddressesAndMdnsHosts() = runTest {
        assertThat(LocalNetworkAccess.isLocalNetworkUrl("http://192.168.1.10:4533")).isTrue()
        assertThat(LocalNetworkAccess.isLocalNetworkUrl("http://navidrome.local:4533")).isTrue()
        assertThat(LocalNetworkAccess.isLocalNetworkUrl("http://127.0.0.1:8080")).isFalse()
        assertThat(LocalNetworkAccess.isLocalNetworkUrl("https://8.8.8.8")).isFalse()
        assertThat(LocalNetworkAccess.isLocalNetworkUrl("")).isFalse()
    }
}
