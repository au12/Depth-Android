package com.depth.breathtrainer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.depth.breathtrainer.playback.DepthMediaService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Reproduces how Android Auto decides whether an installed app belongs in its launcher.
 * It (1) queries for exported MediaBrowserService components, then (2) reads the app's
 * `com.google.android.gms.car.application` meta-data and checks the referenced XML declares
 * `<uses name="media"/>`. If either check fails, the app never appears — which is exactly
 * the failure the previous template-app build had on a real head unit.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAutoDiscoveryTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun mediaBrowserServiceIsExportedAndDiscoverable() {
        val pm = context.packageManager
        val hits = pm.queryIntentServices(
            Intent("android.media.browse.MediaBrowserService").setPackage(context.packageName), 0
        )
        val names = hits.map { it.serviceInfo.name }
        assertTrue("MediaBrowserService not discoverable: $names", names.contains(DepthMediaService::class.java.name))

        val info = pm.getServiceInfo(ComponentName(context, DepthMediaService::class.java), 0)
        assertTrue("Service must be exported so Android Auto can bind it", info.exported)
    }

    @Test
    fun carApplicationMetadataDeclaresMedia() {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val res = appInfo.metaData?.getInt("com.google.android.gms.car.application") ?: 0
        assertTrue("Missing com.google.android.gms.car.application meta-data", res != 0)

        val parser = context.resources.getXml(res)
        val uses = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "uses") {
                uses += parser.getAttributeValue(null, "name")
            }
        }
        assertEquals(listOf("media"), uses)
    }

    @Test
    fun noLeftoverAutomotiveOsOrTemplateDeclarations() {
        val pm = context.packageManager
        // A native-Automotive feature requirement hides phone apps from Android Auto.
        val features = pm.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS).reqFeatures.orEmpty()
        assertTrue(features.none { it.name == "android.hardware.type.automotive" })
        val carApps = pm.queryIntentServices(Intent("androidx.car.app.CarAppService").setPackage(context.packageName), 0)
        assertTrue("Stale template car-app service still declared", carApps.isEmpty())
    }

    @Test
    fun artworkProviderIsDeclared() {
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.artwork", 0)
        assertNotNull(provider)
        assertTrue(provider!!.exported)
    }
}
