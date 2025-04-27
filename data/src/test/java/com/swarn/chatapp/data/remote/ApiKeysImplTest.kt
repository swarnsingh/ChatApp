package com.swarn.chatapp.data.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

class ApiKeysImplTest {
    private lateinit var apiKeys: ApiKeys

    @Before
    fun setup() {
        apiKeys = mockk<ApiKeys>()
    }

    @Test
    fun `when getting pie socket api key, should return from native implementation`() {
        // Given
        val expectedKey = "test-api-key"
        every { apiKeys.getPieSocketApiKey() } returns expectedKey

        // When
        val actualKey = apiKeys.getPieSocketApiKey()

        // Then
        assertEquals(expectedKey, actualKey)
        verify { apiKeys.getPieSocketApiKey() }
    }

    @Test
    fun `when getting pie socket cluster id, should return from native implementation`() {
        // Given
        val expectedClusterId = "test-cluster-id"
        every { apiKeys.getPieSocketClusterId() } returns expectedClusterId

        // When
        val actualClusterId = apiKeys.getPieSocketClusterId()

        // Then
        assertEquals(expectedClusterId, actualClusterId)
        verify { apiKeys.getPieSocketClusterId() }
    }
} 