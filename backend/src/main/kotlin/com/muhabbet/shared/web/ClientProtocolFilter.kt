package com.muhabbet.shared.web

import com.muhabbet.shared.dto.ApiError
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.exception.defaultMessage
import com.muhabbet.shared.exception.httpStatus
import com.muhabbet.shared.protocol.ClientInfo
import com.muhabbet.shared.protocol.MuhabbetJson
import com.muhabbet.shared.protocol.WireProtocol
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.builtins.serializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * Refuses API calls from a client whose protocol is older than `muhabbet.protocol.min-supported`
 * (#873), with **426 Upgrade Required** and `CLIENT_UPGRADE_REQUIRED` in the standard envelope, which
 * the app maps to its force-update state.
 *
 * A client that announces nothing is [WireProtocol.LEGACY_VERSION] — every build released before
 * versioning — so the default minimum of 0 refuses nobody. Raising it is the kill-switch for a client
 * that is no longer safe to serve, and it shows the force-update screen to everyone below it; do it
 * only after a release that speaks the newer protocol has had time to reach phones.
 *
 * Runs before security on purpose: a client too old to be served should be told to update whether or
 * not its token is still good, and before the request costs a database lookup. Only `/api/` is gated
 * — actuator health is called by the container healthcheck, which announces no client, and gating it
 * would turn raising the minimum into a failed deploy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class ClientProtocolFilter(
    @Value("\${muhabbet.protocol.min-supported:0}")
    private val minSupported: Int,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val client = ClientInfo.parseHeader(request.getHeader(WireProtocol.CLIENT_HEADER))
        if (client.protocolVersion >= minSupported) {
            filterChain.doFilter(request, response)
            return
        }

        log.info(
            "Refusing client below minimum protocol: protocol={}, app={}, platform={}, min={}",
            client.protocolVersion, client.appVersion, client.platform, minSupported,
        )
        val code = ErrorCode.CLIENT_UPGRADE_REQUIRED
        val envelope = ApiResponse<Unit>(
            error = ApiError(code = code, message = code.defaultMessage),
            timestamp = Instant.now().toString(),
        )
        response.status = code.httpStatus.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(MuhabbetJson.encodeToString(ApiResponse.serializer(Unit.serializer()), envelope))
    }
}
