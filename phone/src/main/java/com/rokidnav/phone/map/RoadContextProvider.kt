package com.rokidnav.phone.map

import com.rokidnav.protocol.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.floor

/** Local street geometry from OSM. Requests disclose a 2 km neighbourhood, never the destination. */
class RoadContextProvider(private val directory:File,private val http:OkHttpClient=OkHttpClient.Builder()
    .callTimeout(25,TimeUnit.SECONDS).build()) {
    suspend fun roadsNear(center:LatLng):List<ContextRoad> = withContext(Dispatchers.IO) {
        val latCell=floor(center.lat*100).toInt();val lonCell=floor(center.lng*100).toInt()
        val tile=File(directory,"$latCell-$lonCell.json")
        directory.mkdirs()
        var json=tile.takeIf {it.exists()}?.readText()
        if(json==null || System.currentTimeMillis()-tile.lastModified()>86_400_000) {
            val query="[out:json][timeout:20];way(around:2000,${center.lat},${center.lng})[\"highway\"~\"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$\"];out tags geom;"
            try {
                val url="https://overpass-api.de/api/interpreter".toHttpUrl().newBuilder().addQueryParameter("data",query).build()
                val request=Request.Builder().url(url)
                    .header("User-Agent","RokidNavigation/0.1 (personal navigation HUD)")
                    .header("Accept","application/json").get().build()
                val downloaded=http.newCall(request).execute().use {response->
                    require(response.isSuccessful)
                    val body=requireNotNull(response.body)
                    body.charStream().use {reader->
                        val chars=CharArray(2_000_001);var count=0
                        while(count<chars.size){val n=reader.read(chars,count,chars.size-count);if(n<0)break;count+=n}
                        require(count<=2_000_000)
                        String(chars,0,count)
                    }
                }
                parse(downloaded,center) // Validate before replacing a working cache tile.
                val temporary=File(directory,"$latCell-$lonCell.tmp")
                temporary.writeText(downloaded)
                java.nio.file.Files.move(temporary.toPath(),tile.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                json=downloaded
                directory.listFiles {file->file.extension=="json"}?.sortedByDescending {it.lastModified()}?.drop(20)?.forEach {it.delete()}
            }catch(_:java.io.IOException){/* Offline: retain cached streets. */}
            catch(_:IllegalArgumentException){/* Invalid service response: retain cached streets. */}
            catch(_:IllegalStateException){/* Invalid JSON shape: retain cached streets. */}
            catch(_:NoSuchElementException){/* Missing required field: retain cached streets. */}
        }
        json?.let {runCatching {parse(it,center)}.getOrDefault(emptyList())}.orEmpty()
    }

    companion object {
        /** Simplifies to five-meter tolerance and packs the nearest streets into one transport-safe frame. */
        fun parse(json:String,center:LatLng):List<ContextRoad> {
            val root=Json.parseToJsonElement(json).jsonObject
            require(root["remark"]==null) {"Incomplete Overpass response"}
            val roads=root.getValue("elements").jsonArray.mapNotNull {element->runCatching {
                val item=element.jsonObject
                if(item["type"]?.jsonPrimitive?.content!="way")return@runCatching null
                val tags=item.getValue("tags").jsonObject
                val highway=tags.getValue("highway").jsonPrimitive.content
                val points=item.getValue("geometry").jsonArray.take(2048).map {raw->
                    val point=raw.jsonObject;val lat=point.getValue("lat").jsonPrimitive.double;val lon=point.getValue("lon").jsonPrimitive.double
                    require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0)
                    LatLng(lat,lon)
                }
                if(points.size<2)return@runCatching null
                val name=(tags["name"]?:tags["ref"])?.jsonPrimitive?.content.orEmpty().filterNot {it.isISOControl()}.take(40)
                val distance=Geo.projectOntoPolyline(points,center)?.distanceMeters ?: Double.MAX_VALUE
                ContextRoad(PolylineCodec.encode(simplify(points)),name,highway in setOf("motorway","trunk","primary","secondary","tertiary")) to distance
            }.getOrNull()}.sortedBy {it.second}
            val selected=ArrayList<ContextRoad>()
            for((road,_)in roads){
                selected+=road
                val size=ProtocolCodec.encode(RoadContext(Long.MAX_VALUE,"r".repeat(64),selected)).second.toByteArray(Charsets.UTF_8).size
                if(size>6000){selected.removeAt(selected.lastIndex);continue}
                if(selected.size>=100)break
            }
            return selected
        }
        private fun simplify(points:List<LatLng>):List<LatLng> {
            if(points.size<3)return points
            val keep=BooleanArray(points.size);keep[0]=true;keep[points.lastIndex]=true
            val ranges=java.util.ArrayDeque<Pair<Int,Int>>();ranges.add(0 to points.lastIndex)
            while(ranges.isNotEmpty()){
                val (first,last)=ranges.removeLast();var farthest=5.0;var split=-1
                val ends=listOf(points[first],points[last])
                for(i in first+1 until last){val d=Geo.projectOntoPolyline(ends,points[i])!!.distanceMeters;if(d>farthest){farthest=d;split=i}}
                if(split>=0){keep[split]=true;ranges.add(first to split);ranges.add(split to last)}
            }
            return points.filterIndexed {i,_->keep[i]}
        }
    }
}
