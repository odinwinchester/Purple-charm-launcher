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
   val statusCode=c.responseCode
   if(statusCode==404){
    applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit()
     .remove("available_version_code").remove("available_version_name").remove("available_tag")
     .remove("available_url").remove("available_size").remove("available_sha")
     .putString("last_result","up-to-date").putLong("last_check_at",System.currentTimeMillis()).apply()
    return Result.success()
   }
   when(statusCode){
    403->return Result.retry()
    in 500..599->return Result.retry()
    HttpURLConnection.HTTP_OK->{}
    else->{
     applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit().putString("last_result","error").apply()
     PurpleUpdateNotifications.failure(applicationContext,"O GitHub não retornou uma resposta válida.")
     return Result.failure()
    }
   }
   val body=PurpleUpdateHttp.bounded(c)
   val current=applicationContext.packageManager.getPackageInfo(applicationContext.packageName,0).longVersionCodeCompat()
   val candidate=PurpleUpdateRelease.parseLatest(body,current)
   if(candidate==null){
    applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit()
     .remove("available_version_code").remove("available_version_name").remove("available_tag")
     .remove("available_url").remove("available_size").remove("available_sha")
     .putString("last_result","up-to-date").putLong("last_check_at",System.currentTimeMillis()).apply()
    return Result.success()
   }
   applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit()
    .putLong("available_version_code",candidate.versionCode).putString("available_version_name",candidate.versionName)
    .putString("available_tag",candidate.tag).putString("available_url",candidate.downloadUrl)
    .putLong("available_size",candidate.sizeBytes).putString("available_sha",candidate.sha256)
    .putString("last_result","available").putLong("last_check_at",System.currentTimeMillis()).apply()
   PurpleUpdateNotifications.available(applicationContext,candidate.versionName)
   PurpleUpdateDownloadWorker.enqueue(applicationContext,candidate)
   Result.success()
  }catch(_:IOException){
   applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit().putString("last_result","network-error").apply()
   PurpleUpdateNotifications.failure(applicationContext,"Verifique a conexão e tente novamente.")
   Result.retry()
  }catch(_:Exception){
   applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit().putString("last_result","error").apply()
   PurpleUpdateNotifications.failure(applicationContext,"Não foi possível verificar atualizações.")
   Result.failure()
  }finally{c.disconnect()}
 }
}
class PurpleUpdateDownloadWorker(c:Context,p:WorkerParameters):Worker(c,p) {
 override fun doWork():Result {
  val code=inputData.getLong("code",-1);val name=inputData.getString("name")?:return failed(code)
  val tag=inputData.getString("tag")?:return failed(code);var url=inputData.getString("url")?:return failed(code)
  val size=inputData.getLong("size",-1);val sha=inputData.getString("sha")?:return failed(code)
  val tagVersion=tag.removePrefix("v").removeSuffix("-stable")
  val apkName="Purple-Charm-v$tagVersion-OFFICIAL-RELEASE.apk"
  val exact="https://github.com/odinwinchester/Purple-charm-launcher/releases/download/$tag/$apkName"
  if(!tag.matches(Regex("^v[0-9]+(?:\\.[0-9]+){0,2}(?:-stable)?$"))||url!=exact||
    !name.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$"))||code !in 1..Int.MAX_VALUE.toLong()||
    size !in 1..PurpleUpdateRelease.MAX_APK_SIZE_BYTES||!sha.matches(Regex("^[0-9a-fA-F]{64}$")))return failed(code)
  val dir=File(applicationContext.cacheDir,"updates");if(!dir.exists()&&!dir.mkdirs())return failed(code)
  val part=File(dir,"purple-$code.apk.part");val final=File(dir,"purple-$code.apk");part.delete();var success=false
  applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit()
   .putString("last_result","downloading").putLong("downloaded_bytes",0L).apply()
  return try {
   var hops=0;var count=0L;var downloadedSha=""
   while(true){
    if(isStopped)throw IOException("stopped")
    if(!PurpleUpdateHttp.assetUrl(url,true))return failed(code)
    val c=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=15000;readTimeout=30000;instanceFollowRedirects=false;setRequestProperty("User-Agent","Purple-Charm-Android-Updater");setRequestProperty("Accept","application/octet-stream")}
    try{
     val status=c.responseCode
     if(status in listOf(301,302,303,307,308)){if(hops++>=PurpleUpdateHttp.MAX_REDIRECTS)return failed(code);url=PurpleUpdateHttp.redirect(url,c.getHeaderField("Location")?:return failed(code))?:return failed(code);continue}
     if(status==408||status==429||status>=500){
      applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit().putString("last_result","download-error").apply()
      PurpleUpdateNotifications.failure(applicationContext,"O download será tentado novamente quando a conexão permitir.")
      return Result.retry()
     }
     if(status!=200)return failed(code)
     if(c.contentLengthLong>=0&&c.contentLengthLong!=size)return failed(code)
     var lastNotified=0L;var lastStateAt=0L
     val copied=c.inputStream.use{input->part.outputStream().buffered().use{out->PurpleUpdateTransfer.copy(input,out,size,{isStopped}){n->
      val threshold=(size/20).coerceAtLeast(1024L*1024L)
      val now=System.currentTimeMillis()
      if(n-lastStateAt>=1000L||n==size){
       lastStateAt=now
       setProgressAsync(Data.Builder().putLong("downloaded",n).putLong("total",size).build())
       applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit().putLong("downloaded_bytes",n).apply()
      }
      if(n-lastNotified>=threshold||n==size){lastNotified=n;PurpleUpdateNotifications.progress(applicationContext,name,n,size)}
     }}}
     count=copied.first
     val calculatedSha=copied.second
     downloadedSha=calculatedSha
     if(count!=size||!calculatedSha.equals(sha,true))return failed(code)
     break
    }finally{c.disconnect()}
   }
   if(count!=size||!downloadedSha.equals(sha,true))return failed(code)
   if(!part.renameTo(final))return failed(code)
   val candidate=PurpleUpdateRelease.Candidate(code,name,tag,exact,size,sha)
   if(!PurpleUpdateApkSecurity.verify(applicationContext,final,candidate)){final.delete();return failed(code)}
   success=true
   applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit()
    .putString("last_result","ready").putLong("downloaded_bytes",size).apply()
   PurpleUpdateNotifications.ready(applicationContext,name)
   Result.success(Data.Builder().putString("verified_apk",final.absolutePath).putLong("version_code",code).build())
  }catch(_:IOException){
   applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit().putString("last_result","download-error").apply()
   PurpleUpdateNotifications.failure(applicationContext,"O download foi interrompido e será tentado novamente.")
   Result.retry()
  }catch(_:Exception){failed(code)}finally{part.delete();if(!success)final.delete()}
 }
 private fun failed(code:Long):Result {
  applicationContext.getSharedPreferences("purple_update_state",Context.MODE_PRIVATE).edit().putString("last_result","failed").apply()
  PurpleUpdateNotifications.failure(applicationContext,"O APK não passou pela validação. Verifique novamente as atualizações.")
  return Result.failure()
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
