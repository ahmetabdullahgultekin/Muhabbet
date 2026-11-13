package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.ExportedEncryptionKeySummary
import com.muhabbet.auth.domain.model.ExportedPage
import com.muhabbet.auth.domain.model.ExportedPrivacySettings
import com.muhabbet.auth.domain.model.ExportedProfile
import com.muhabbet.auth.domain.model.UserDataExport
import com.muhabbet.auth.domain.port.`in`.ManageUserDataUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.config.JsonConfig
import com.muhabbet.shared.security.JwtAuthFilter
import com.muhabbet.shared.security.JwtClaims
import com.muhabbet.shared.security.RateLimitFilter
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * The two KVKK addresses the privacy dashboard calls, driven through the real `DispatcherServlet`
 * (#780).
 *
 * `UserDataControllerTest` invokes the controller methods directly, so it passed the whole time both
 * buttons answered 404: the app called `/users/data/export` and `/users/data/account`, which nothing
 * maps. Only a test that starts from a URL and a verb can catch that, and the URLs are spelled as
 * **literals** here — not read from `ApiRoutes` — because a test that reuses the constant agrees with
 * whatever the constant says. These literals must match `DataExportRepositoryTest` and
 * `AuthRepositoryTest` on the mobile side.
 *
 * It also pins the page shape the client walks: `messages`/`mediaFiles` each carry `items`,
 * `hasMore` and `nextCursor`, and the cursors are passed back under these query parameter names.
 *
 * A slice test: no database, no Redis, no Docker.
 */
@WebMvcTest(
    controllers = [UserDataController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class, RateLimitFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
@Import(UserDataRoutingTest.Mocks::class, JsonConfig::class)
class UserDataRoutingTest {

    @TestConfiguration
    class Mocks {
        @Bean fun manageUserDataUseCase(): ManageUserDataUseCase = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var manageUserDataUseCase: ManageUserDataUseCase

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun authenticate() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            JwtClaims(userId = userId, deviceId = TestData.DEVICE_ID_1),
            null,
            emptyList()
        )
    }

    @AfterEach
    fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Test
    fun `should serve the export page at the address the app calls, with the cursors it sends`() {
        every {
            manageUserDataUseCase.exportUserData(userId, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", 500)
        } returns sampleExport()

        mockMvc.perform(
            get("/api/v1/users/me/data-export")
                .param("messagesCursor", "2026-01-01T00:00:00Z")
                .param("mediaCursor", "2026-02-01T00:00:00Z")
                .param("pageSize", "500")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.messages.hasMore").value(true))
            .andExpect(jsonPath("$.data.messages.nextCursor").value("2026-03-01T00:00:00Z"))
            .andExpect(jsonPath("$.data.messages.items").isArray)
            .andExpect(jsonPath("$.data.mediaFiles.hasMore").value(false))

        verify { manageUserDataUseCase.exportUserData(userId, "2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z", 500) }
    }

    @Test
    fun `should erase the account on DELETE of the account itself`() {
        justRun { manageUserDataUseCase.requestAccountDeletion(userId) }

        mockMvc.perform(delete("/api/v1/users/me"))
            .andExpect(status().is2xxSuccessful)

        verify { manageUserDataUseCase.requestAccountDeletion(userId) }
    }

    @Test
    fun `should not serve the old addresses the app used to call`() {
        // Documents the 404 the buttons were getting, so nobody "fixes" the client back to them.
        mockMvc.perform(get("/api/v1/users/data/export")).andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/v1/users/data/account")).andExpect(status().isNotFound)
    }

    private fun sampleExport() = UserDataExport(
        exportedAt = Instant.parse("2026-03-02T00:00:00Z"),
        profile = ExportedProfile(
            userId = userId,
            phoneNumber = TestData.PHONE_1,
            displayName = "Test User",
            avatarUrl = null,
            about = null,
            joinedAt = Instant.parse("2025-01-15T10:00:00Z"),
            twoStepVerificationEnabled = false
        ),
        privacySettings = ExportedPrivacySettings(true, "everyone", "everyone"),
        devices = emptyList(),
        sessions = emptyList(),
        loginApprovals = emptyList(),
        linkedDeviceSessions = emptyList(),
        contacts = emptyList(),
        discoverableByPhoneHash = true,
        conversations = emptyList(),
        messages = ExportedPage(items = emptyList(), nextCursor = "2026-03-01T00:00:00Z", hasMore = true, totalCount = 900),
        mediaFiles = ExportedPage(items = emptyList(), nextCursor = null, hasMore = false, totalCount = 0),
        chatWallpapers = emptyList(),
        chatFolders = emptyList(),
        messageBackups = emptyList(),
        ownedBroadcastLists = emptyList(),
        broadcastListMemberships = emptyList(),
        encryptionKeys = ExportedEncryptionKeySummary(false, null, null, 0)
    )
}
