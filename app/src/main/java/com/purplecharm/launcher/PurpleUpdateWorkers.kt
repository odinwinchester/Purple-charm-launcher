package com.purplecharm.launcher
import android.content.Context
import androidx.work.*
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object PurpleUpdateHttp {
 const val MAX_JSON=512*1024; const val MAX_REDIRECTS=5
 private val hosts=setOf("release-assets.githubusercontent.com","objects.githubusercontent.com","objects-origin.githubusercontent.com","github-releases.githubusercontent.com","github-registry-files.githubusercontent.com")
 fun connection():HttpURLConnection=(URL(PurpleUpdateRelease.RELEASES_API).openConnection() as HttpURLConnection).apply {
  connectTimeout=10000; readTimeout=15000; instanceFollowRedirects=false
  setRequestProperty("Accept","application/vnd.github+json"); setRequestProperty("X-GitHub-Api-Version","2022-11-28"); setRequestProperty("User-Agent","Purple-Charm-Android-Updater")
 }
 fun assetUrl(s:String, initial:Boolean):Boolean {
  val u=try{URI(s)}catch(_:Exception){return false}; val h=u.host?.lowercase(Locale.ROOT)?:return false
  val github=initial&&h=="github.com"&&u.rawPath?.startsWith("/odinwinchester/Purple-charm-launcher/releases/download/")==true
  return u.scheme=="https"&&u.rawUserInfo==null&&u.port==-1&&u.rawFragment==null&&(if(h=="github.com")github&&u.rawQuery==null else h in hosts)
 }
 fun redirect(from:String, location:String):String?=try{URI(from).resolve(URI(location)).toString().takeIf{assetUrl(it,true)}}catch(_:Exception){null}
 fun bounded(c:HttpURLConnection):String {
  val out=ByteArrayOutputStream()
  c.inputStream.use { input->val b=ByteArray(8192); while(true){val n=input.read(b);if(n<0)break;if(out.size()+n>MAX_JSON)throw IOException("metadata limit");out.write(b,0,n)} }
  return out.toString("UTF-8")
 }
}
internal object PurpleUpdateTransfer {
 fun copy(input:InputStream,output:OutputStream,expected:Long,isCancelled:()->Boolean,progress:(Long)->Unit):Pair<Long,String> {
  val digest=MessageDigest.getInstance("SHA-256");var count=0L;val buffer=ByteArray(65536)
  while(true){if(isCancelled())throw IOException("download stopped");val n=input.read(buffer);if(n<0)break;count+=n
   if(count>expected||count>PurpleUpdateRelease.MAX_APK_SIZE_BYTES)throw IOException("download too large")
   digest.update(buffer,0,n);output.write(buffer,0,n);progress(count)
  }
  return count to digest.digest().joinToString(""){"%02x".format(it.toInt() and 0xff)}
 }
}
class PurpleUpdateCheckWorker(c:Context,p:WorkerParameters):Worker(c,p) {
 override fun doWork():Result {
  val c=try{PurpleUpdateHttp.connection()}catch(_:Exception){return Result.retry()}
  return try {
   when(val status=c.responseCode){404->return Result.success();403->return Result.retry();in 500..599->return Result.retry();HttpURLConnection.HTTP_OK->{};else->return Result.failure()}
   val body=PurpleUpdateHttp.bounded(c)
   val current=applicationContext.packageManager.getPackageInfo(applicationContext.packageName,0).longVersionCodeCompat()
   val candidate=PurpleUpdateRelease.parseLatest(body,current)
   if(candidate==null){
    applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit()
     .remove("available_version_code").remove("available_version_name").remove("available_tag").apply()
    return Result.success()
   }
   applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit()
    .putLong("available_version_code",candidate.versionCode).putString("available_version_name",candidate.versionName)
    .putString("available_tag",candidate.tag).apply()
   PurpleUpdateDownloadWorker.enqueue(applicationContext,candidate)
   Result.success()
  }catch(_:IOException){Result.retry()}catch(_:Exception){Result.failure()}finally{c.disconnect()}
 }
}
class PurpleUpdateDownloadWorker(c:Context,p:WorkerParameters):Worker(c,p) {
 override fun doWork():Result {
  val code=inputData.getLong("code",-1);val name=inputData.getString("name")?:return Result.failure()
  val tag=inputData.getString("tag")?:return Result.failure();var url=inputData.getString("url")?:return Result.failure()
  val size=inputData.getLong("size",-1);val sha=inputData.getString("sha")?:return Result.failure()
  val tagVersion=tag.removePrefix("v").removeSuffix("-stable")
  val apkName="Purple-Charm-v$tagVersion-OFFICIAL-RELEASE.apk"
  val exact="https://github.com/odinwinchester/Purple-charm-launcher/releases/download/$tag/$apkName"
  if(!tag.matches(Regex("^v[0-9]+(?:\\.[0-9]+){0,2}(?:-stable)?$"))||url!=exact||
    !name.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$"))||code !in 1..Int.MAX_VALUE.toLong()||
    size !in 1..PurpleUpdateRelease.MAX_APK_SIZE_BYTES||!sha.matches(Regex("^[0-9a-fA-F]{64}$")))return Result.failure()
  val dir=File(applicationContext.cacheDir,"updates");if(!dir.exists()&&!dir.mkdirs())return Result.failure()
  val part=File(dir,"purple-$code.apk.part");val final=File(dir,"purple-$code.apk");part.delete();var success=false
  return try {
   var hops=0;var count=0L;var downloadedSha=""
   while(true){
    if(isStopped)throw IOException("stopped")
    if(!PurpleUpdateHttp.assetUrl(url,true))return Result.failure()
    val c=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=15000;readTimeout=30000;instanceFollowRedirects=false;setRequestProperty("User-Agent","Purple-Charm-Android-Updater");setRequestProperty("Accept","application/octet-stream")}
    try{
     val status=c.responseCode
     if(status in listOf(301,302,303,307,308)){if(hops++>=PurpleUpdateHttp.MAX_REDIRECTS)return Result.failure();url=PurpleUpdateHttp.redirect(url,c.getHeaderField("Location")?:return Result.failure())?:return Result.failure();continue}
     if(status==408||status==429||status>=500)return Result.retry()
     if(status!=200)return Result.failure()
     if(c.contentLengthLong>=0&&c.contentLengthLong!=size)return Result.failure()
     val copied=c.inputStream.use{input->part.outputStream().buffered().use{out->PurpleUpdateTransfer.copy(input,out,size,{isStopped}){n->setProgressAsync(Data.Builder().putLong("downloaded",n).putLong("total",size).build())}}}
     count=copied.first
     val calculatedSha=copied.second
     downloadedSha=calculatedSha
     if(count!=size||!calculatedSha.equals(sha,true))return Result.failure()
     break
    }finally{c.disconnect()}
   }
   if(count!=size||!downloadedSha.equals(sha,true))return Result.failure()
   if(!part.renameTo(final))return Result.failure()
   val candidate=PurpleUpdateRelease.Candidate(code,name,tag,exact,size,sha)
   if(!PurpleUpdateApkSecurity.verify(applicationContext,final,candidate)){final.delete();return Result.failure()}
   success=true
   Result.success(Data.Builder().putString("verified_apk",final.absolutePath).putLong("version_code",code).build())
  }catch(_:IOException){Result.retry()}catch(_:Exception){Result.failure()}finally{part.delete();if(!success)final.delete()}
 }
 companion object {
  internal fun enqueue(context:Context,c:PurpleUpdateRelease.Candidate){
   val data=Data.Builder().putLong("code",c.versionCode).putString("name",c.versionName).putString("tag",c.tag).putString("url",c.downloadUrl).putLong("size",c.sizeBytes).putString("sha",c.sha256).build()
   val req=OneTimeWorkRequestBuilder<PurpleUpdateDownloadWorker>().setInputData(data).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
   WorkManager.getInstance(context).enqueueUniqueWork("purple-update-download-" + c.versionCode,ExistingWorkPolicy.KEEP,req)
  }
 }
}
internal object PurpleUpdateManager {
 fun schedule(context:Context){
  val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
  val manager=try{WorkManager.getInstance(context.applicationContext)}catch(_:IllegalStateException){return}
  manager.enqueueUniquePeriodicWork("purple-update-check-periodic",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<PurpleUpdateCheckWorker>(24,TimeUnit.HOURS).setConstraints(constraints).build())
  manager.enqueueUniqueWork("purple-update-check-now",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<PurpleUpdateCheckWorker>().setConstraints(constraints).build())
 }
}
@Suppress("DEPRECATION")
private fun android.content.pm.PackageInfo.longVersionCodeCompat():Long=if(android.os.Build.VERSION.SDK_INT>=28)longVersionCode else versionCode.toLong()
