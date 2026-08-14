package com.familyguard.core.update

import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class UpdateManifest(
    val schemaVersion: Int,
    val releaseId: String,
    val channel: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minSupportedVersionCode: Long,
    val url: String,
    val sizeBytes: Long,
    val apkSha256: String,
    val signingCertSha256: String,
    val mandatory: Boolean,
    val deadlineAt: Long,
    val rolloutPercent: Int,
    val issuedAt: Long,
    val expiresAt: Long,
    val signatureAlgorithm: String,
    val manifestSignature: String,
) {
    /** Stable UTF-8 representation signed by the offline release key. */
    fun canonicalPayload(): String = listOf(
        "schemaVersion=$schemaVersion",
        "releaseId=$releaseId",
        "channel=$channel",
        "packageName=$packageName",
        "versionCode=$versionCode",
        "versionName=$versionName",
        "minSupportedVersionCode=$minSupportedVersionCode",
        "url=$url",
        "sizeBytes=$sizeBytes",
        "apkSha256=${apkSha256.lowercase()}",
        "signingCertSha256=${signingCertSha256.lowercase()}",
        "mandatory=$mandatory",
        "deadlineAt=$deadlineAt",
        "rolloutPercent=$rolloutPercent",
        "issuedAt=$issuedAt",
        "expiresAt=$expiresAt",
        "signatureAlgorithm=$signatureAlgorithm",
    ).joinToString(separator = "\n", postfix = "\n")
}

sealed interface UpdateManifestCheck {
    data object Accepted : UpdateManifestCheck
    data object UnsupportedSchema : UpdateManifestCheck
    data object InvalidSignature : UpdateManifestCheck
    data object WrongPackage : UpdateManifestCheck
    data object NotNewer : UpdateManifestCheck
    data object InsecureUrl : UpdateManifestCheck
    data object Expired : UpdateManifestCheck
    data object InvalidMetadata : UpdateManifestCheck
}

object UpdateManifestVerifier {
    fun verify(
        manifest: UpdateManifest,
        publicKeyDer: ByteArray,
        expectedPackage: String,
        installedVersionCode: Long,
        now: Long,
    ): UpdateManifestCheck {
        if (manifest.schemaVersion != 1 || manifest.signatureAlgorithm != SIGNATURE_ALGORITHM) {
            return UpdateManifestCheck.UnsupportedSchema
        }
        // Verify before returning field-specific errors so tampering is never treated as trusted metadata.
        if (!hasValidSignature(manifest, publicKeyDer)) return UpdateManifestCheck.InvalidSignature
        if (manifest.packageName != expectedPackage) return UpdateManifestCheck.WrongPackage
        if (manifest.versionCode <= installedVersionCode) return UpdateManifestCheck.NotNewer
        if (runCatching { URI(manifest.url).scheme }.getOrNull() != "https") {
            return UpdateManifestCheck.InsecureUrl
        }
        if (manifest.expiresAt < now || manifest.issuedAt > now + MAX_CLOCK_SKEW_MS) {
            return UpdateManifestCheck.Expired
        }
        if (
            manifest.releaseId.isBlank() || manifest.versionName.isBlank() ||
            manifest.sizeBytes <= 0 || manifest.apkSha256.length != 64 ||
            manifest.signingCertSha256.length != 64 || manifest.rolloutPercent !in 0..100
        ) return UpdateManifestCheck.InvalidMetadata
        return UpdateManifestCheck.Accepted
    }

    private fun hasValidSignature(manifest: UpdateManifest, publicKeyDer: ByteArray): Boolean = runCatching {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyDer))
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(key)
            update(manifest.canonicalPayload().toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(manifest.manifestSignature))
        }
    }.getOrDefault(false)

    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val MAX_CLOCK_SKEW_MS = 5 * 60_000L
}
