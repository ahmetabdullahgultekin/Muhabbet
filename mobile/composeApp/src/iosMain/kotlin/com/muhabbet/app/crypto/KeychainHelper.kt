package com.muhabbet.app.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus

/**
 * iOS Keychain helper for secure key storage.
 *
 * Stores sensitive data (tokens, encryption keys) in the iOS Keychain
 * which is hardware-encrypted and persists across app reinstalls.
 * Used for token storage and future E2E key persistence on iOS.
 */
@OptIn(ExperimentalForeignApi::class)
object KeychainHelper {

    private const val SERVICE_NAME = "com.muhabbet.app"

    fun save(key: String, value: String): Boolean {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false

        // Delete existing item first
        delete(key)

        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key,
            kSecValueData to data,
            // This device only, and only once it has been unlocked since boot: the item is left
            // out of encrypted backups and iCloud Keychain, so tokens do not follow a backup to
            // another phone. Readable while locked after the first unlock, which background sync
            // needs. Items written before this was set pick it up on their next save, because
            // save() deletes and re-adds.
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        )

        val status = SecItemAdd(query as CFDictionaryRef, null)
        return status == errSecSuccess
    }

    fun load(key: String): String? {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key,
            kSecMatchLimit to kSecMatchLimitOne,
            kSecReturnData to true
        )

        memScoped {
            val result = alloc<kotlinx.cinterop.ObjCObjectVar<Any?>>()
            @Suppress("UNCHECKED_CAST")
            val status = SecItemCopyMatching(
                query as CFDictionaryRef,
                result.ptr as kotlinx.cinterop.CValuesRef<kotlinx.cinterop.CPointerVarOf<kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>>>
            )

            if (status == errSecSuccess) {
                val data = result.value as? NSData ?: return null
                return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
            }
        }
        return null
    }

    fun delete(key: String): Boolean {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME,
            kSecAttrAccount to key
        )

        val status = SecItemDelete(query as CFDictionaryRef)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    fun deleteAll(): Boolean {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE_NAME
        )
        val status = SecItemDelete(query as CFDictionaryRef)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}
