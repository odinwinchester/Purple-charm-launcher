package com.purplecharm.launcher
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

internal object PurpleUpdateApkSecurity {
 internal fun isCompatible(archivePackage:String?,installedPackage:String,installedCode:Long,
  archiveCode:Long,archiveName:String?,candidateCode:Long,candidateName:String):Boolean =
  archivePackage=="com.purplecharm.launcher"&&archivePackage==installedPackage&&
   archiveCode>installedCode&&archiveCode==candidateCode&&archiveName==candidateName
 internal fun signerSetsMatch(installed:Set<String>,archive:Set<String>):Boolean =
  installed.isNotEmpty()&&installed==archive
 fun verify(context:Context,apk:File,c:PurpleUpdateRelease.Candidate):Boolean {
  if(!apk.isFile||apk.length()!=c.sizeBytes)return false
  return try {
   val pm=context.packageManager
   val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
   val installed=pm.getPackageInfo(context.packageName,flags)
   val archive=pm.getPackageArchiveInfo(apk.absolutePath,flags)?:return false
   if(!isCompatible(archive.packageName,context.packageName,installed.longVersionCodeCompat(),
      archive.longVersionCodeCompat(),archive.versionName,c.versionCode,c.versionName))return false
   val a=signers(installed)?:return false;val b=signers(archive)?:return false
   signerSetsMatch(a,b)
  }catch(_:Exception){false}
 }
 @Suppress("DEPRECATION")
 private fun signers(p:PackageInfo):Set<String>? {
  val sigs=if(Build.VERSION.SDK_INT>=28)p.signingInfo?.apkContentsSigners?:return null else p.signatures?:return null
  if(sigs.isEmpty())return null
  return sigs.map{MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 0xff)}}.toSet().takeIf{it.size==sigs.size}
 }
}
internal object PurpleUpdateInstaller {
 fun createUserConfirmedIntent(context:Context,apk:File):Intent {
  require(apk.isFile&&apk.parentFile==File(context.cacheDir,"updates"))
  if(Build.VERSION.SDK_INT>=26&&!context.packageManager.canRequestPackageInstalls())
   return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  val uri=FileProvider.getUriForFile(context,context.packageName + ".updates",apk)
  return Intent(Intent.ACTION_INSTALL_PACKAGE).apply{setDataAndType(uri,"application/vnd.android.package-archive");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)}
 }
}
@Suppress("DEPRECATION")
private fun PackageInfo.longVersionCodeCompat():Long=if(Build.VERSION.SDK_INT>=28)longVersionCode else versionCode.toLong()
