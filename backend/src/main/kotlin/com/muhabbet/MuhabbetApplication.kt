package com.muhabbet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(
    excludeName = ["io.sentry.spring.boot.jakarta.SentryAutoConfiguration"]
)
@EnableAsync
@EnableScheduling
class MuhabbetApplication

fun main(args: Array<String>) {
    runApplication<MuhabbetApplication>(*args)
}
