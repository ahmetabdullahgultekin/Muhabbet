package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.port.out.NotificationTextPort
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.stereotype.Component
import java.util.Locale

/**
 * Resolves push text from `notification-messages*.properties` via Spring's [MessageSource].
 *
 * The keys are derived from the [ContentType] name so a new content type cannot be added without
 * either adding its line to every bundle or failing `NotificationTextCatalogTest`.
 */
@Component
class MessageSourceNotificationTextAdapter(
    private val notificationMessageSource: MessageSource
) : NotificationTextPort {

    override fun contentSummary(contentType: ContentType, locale: Locale): String =
        notificationMessageSource.getMessage("push.content.${contentType.name}", null, locale)

    override fun groupTitle(senderName: String, groupName: String, locale: Locale): String =
        notificationMessageSource.getMessage("push.title.group", arrayOf(senderName, groupName), locale)

    override fun unknownSender(locale: Locale): String =
        notificationMessageSource.getMessage("push.sender.unknown", null, locale)
}

@Configuration
class NotificationTextConfig {

    /**
     * The only [MessageSource] in the context. Spring Boot auto-configures one only when a
     * `messages*.properties` exists, and this basename deliberately is not that, so there is
     * nothing to be @Primary over.
     */
    @Bean
    fun notificationMessageSource(): MessageSource = ResourceBundleMessageSource().apply {
        setBasename("notification-messages")
        setDefaultEncoding("UTF-8")
        // A server whose JVM happens to run in de_DE must not silently serve German push text.
        // With this off, an unmatched locale falls back to the default bundle (Turkish) instead.
        setFallbackToSystemLocale(false)
    }
}
