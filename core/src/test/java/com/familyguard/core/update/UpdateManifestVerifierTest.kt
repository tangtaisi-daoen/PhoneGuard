package com.familyguard.core.update

import com.google.gson.Gson
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestVerifierTest {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
    private val now = 1_800_000_000_000L

    @Test
    fun `valid signed update is accepted`() {
        val manifest = signedManifest()

        assertEquals(
            UpdateManifestCheck.Accepted,
            UpdateManifestVerifier.verify(manifest, keyPair.public.encoded, "com.familyguard.kid", 3, now),
        )
    }

    @Test
    fun `tampered manifest is rejected`() {
        val manifest = signedManifest().copy(url = "https://evil.example/app.apk")

        assertEquals(
            UpdateManifestCheck.InvalidSignature,
            UpdateManifestVerifier.verify(manifest, keyPair.public.encoded, "com.familyguard.kid", 3, now),
        )
    }

    @Test
    fun `wrong package downgrade insecure url and expiry are rejected`() {
        val cases = listOf(
            signedManifest(packageName = "other.app") to UpdateManifestCheck.WrongPackage,
            signedManifest(versionCode = 3) to UpdateManifestCheck.NotNewer,
            signedManifest(url = "http://example.com/app.apk") to UpdateManifestCheck.InsecureUrl,
            signedManifest(expiresAt = now - 1) to UpdateManifestCheck.Expired,
        )

        cases.forEach { (manifest, expected) ->
            assertEquals(
                expected,
                UpdateManifestVerifier.verify(manifest, keyPair.public.encoded, "com.familyguard.kid", 3, now),
            )
        }
    }

    @Test
    fun `canonical payload is stable and excludes signature`() {
        val manifest = signedManifest()
        val payload = manifest.canonicalPayload()

        assertTrue(payload.startsWith("schemaVersion=1\nreleaseId=release-4\n"))
        assertTrue(!payload.contains("manifestSignature"))
    }

    @Test
    fun `generated stable release manifest verifies with repository public key`() {
        val repositoryRoot = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val manifest = Gson().fromJson(
            File(repositoryRoot, "docs/updates/kid-stable-update-manifest.json").readText(),
            UpdateManifest::class.java,
        )
        val publicKey = File(repositoryRoot, "release-keys/update-manifest-public.der").readBytes()

        assertEquals(
            UpdateManifestCheck.Accepted,
            UpdateManifestVerifier.verify(
                manifest,
                publicKey,
                "com.familyguard.kid",
                manifest.versionCode - 1,
                manifest.issuedAt + 1_000,
            ),
        )
    }

    private fun signedManifest(
        packageName: String = "com.familyguard.kid",
        versionCode: Long = 4,
        url: String = "https://example.com/kid.apk",
        expiresAt: Long = now + 86_400_000L,
    ): UpdateManifest {
        val unsigned = UpdateManifest(
            schemaVersion = 1,
            releaseId = "release-4",
            channel = "stable",
            packageName = packageName,
            versionCode = versionCode,
            versionName = "0.1.3",
            minSupportedVersionCode = 3,
            url = url,
            sizeBytes = 1_234,
            apkSha256 = "a".repeat(64),
            signingCertSha256 = "b".repeat(64),
            mandatory = false,
            deadlineAt = 0,
            rolloutPercent = 100,
            issuedAt = now - 1_000,
            expiresAt = expiresAt,
            signatureAlgorithm = "SHA256withECDSA",
            manifestSignature = "",
        )
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(unsigned.canonicalPayload().toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        return unsigned.copy(manifestSignature = signature)
    }
}
