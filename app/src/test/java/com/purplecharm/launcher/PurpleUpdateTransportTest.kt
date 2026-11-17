package com.purplecharm.launcher
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class PurpleUpdateTransportTest {
 @Test fun allowsOnlyTheOfficialReleaseOriginAndAssetHosts() {
  assertTrue(PurpleUpdateHttp.assetUrl("https://github.com/odinwinchester/Purple-charm-launcher/releases/download/v13/Purple-Charm-v13-OFFICIAL-RELEASE.apk",true))
  assertFalse(PurpleUpdateHttp.assetUrl("http://github.com/odinwinchester/Purple-charm-launcher/releases/download/v13/a.apk",true))
  assertFalse(PurpleUpdateHttp.assetUrl("https://github.com.evil.example/odinwinchester/Purple-charm-launcher/releases/download/v13/a.apk",true))
  assertFalse(PurpleUpdateHttp.assetUrl("https://github.com/other/repository/releases/download/v13/a.apk",true))
  assertFalse(PurpleUpdateHttp.assetUrl("https://evil.example/a.apk",true))
 }
 @Test fun permitsSignedQueryOnlyOnExactAssetCdnHost() {
  assertTrue(PurpleUpdateHttp.assetUrl("https://release-assets.githubusercontent.com/path/asset?token=temporary",false))
  assertFalse(PurpleUpdateHttp.assetUrl("https://evil.example/path/asset?token=temporary",false))
  assertFalse(PurpleUpdateHttp.assetUrl("https://release-assets.githubusercontent.com@evil.example/path",false))
 }
 @Test fun redirectsMustStayHttpsAndInsideTheOfficialAllowlist() {
  val start="https://github.com/odinwinchester/Purple-charm-launcher/releases/download/v13/a.apk"
  assertEquals("https://release-assets.githubusercontent.com/path/a?token=x",PurpleUpdateHttp.redirect(start,"https://release-assets.githubusercontent.com/path/a?token=x"))
  assertNull(PurpleUpdateHttp.redirect(start,"http://release-assets.githubusercontent.com/a"))
  assertNull(PurpleUpdateHttp.redirect(start,"https://release-assets.githubusercontent.com.evil.example/a"))
 }
 @Test fun interruptedStreamThrowsInsteadOfAcceptingPartialBytes() {
  val input=object:InputStream(){
   var sent=false
   override fun read():Int { if(!sent){sent=true;return 1};throw IOException("simulated disconnect") }
   override fun read(b:ByteArray,off:Int,len:Int):Int { if(!sent){sent=true;b[off]=1;return 1};throw IOException("simulated disconnect") }
  }
  try {
   PurpleUpdateTransfer.copy(input,ByteArrayOutputStream(),10,{false},{})
   fail("partial transfer must fail")
  } catch(_:IOException) { }
 }
 @Test fun copiedBytesAreBoundedAndHashed() {
  val out=ByteArrayOutputStream()
  val result=PurpleUpdateTransfer.copy("abc".byteInputStream(),out,3,{false},{})
  assertEquals(3L,result.first)
  assertEquals("abc",out.toString("UTF-8"))
  assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",result.second)
 }
 @Test fun rejectsWrongPackageVersionOrVersionNameBeforeInstall() {
  val valid=PurpleUpdateApkSecurity.isCompatible("com.purplecharm.launcher","com.purplecharm.launcher",12,13,"13",13,"13")
  assertTrue(valid)
  assertFalse(PurpleUpdateApkSecurity.isCompatible("other.app","com.purplecharm.launcher",12,13,"13",13,"13"))
  assertFalse(PurpleUpdateApkSecurity.isCompatible("com.purplecharm.launcher","com.purplecharm.launcher",12,12,"13",13,"13"))
  assertFalse(PurpleUpdateApkSecurity.isCompatible("com.purplecharm.launcher","com.purplecharm.launcher",12,13,"12",13,"13"))
 }
 @Test fun requiresExactNonEmptyInstalledSignerSet() {
  assertTrue(PurpleUpdateApkSecurity.signerSetsMatch(setOf("cert-a"),setOf("cert-a")))
  assertFalse(PurpleUpdateApkSecurity.signerSetsMatch(setOf("cert-a"),setOf("cert-b")))
  assertFalse(PurpleUpdateApkSecurity.signerSetsMatch(emptySet(),emptySet()))
 }
}
