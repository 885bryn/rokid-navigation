package com.rokidnav.phone.map

import com.rokidnav.protocol.*
import org.junit.Assert.*
import org.junit.Test

class RoadContextProviderTest {
    @Test fun keepsStreetNamesIntersectionsAndOnlyValidGeometry() {
        val json="""{"elements":[
          {"type":"way","tags":{"name":"Main Street","highway":"primary"},"geometry":[{"lat":0,"lon":0},{"lat":0,"lon":0.001},{"lat":0,"lon":0.002}]},
          {"type":"way","tags":{"name":"Side Street","highway":"residential"},"geometry":[{"lat":-0.001,"lon":0.001},{"lat":0.001,"lon":0.001}]},
          {"type":"way","tags":{"name":"Bad","highway":"primary"},"geometry":[{"lat":91,"lon":0},{"lat":92,"lon":0}]}
        ]}"""
        val roads=RoadContextProvider.parse(json,LatLng(0.0,0.0))
        assertEquals(2,roads.size)
        assertEquals("Main Street",roads.first().name)
        assertTrue(roads.first().major)
        assertEquals(2,PolylineCodec.decode(roads.first().encodedPolyline).size)
        assertEquals("Side Street",roads.last().name)
    }
    @Test fun messageAlwaysFitsTheLinkBudget() {
        val ways=(1..200).joinToString(",") {index->"""{"type":"way","tags":{"name":"Street $index","highway":"residential"},"geometry":[{"lat":0,"lon":0},{"lat":0.001,"lon":0.001}]}"""}
        val roads=RoadContextProvider.parse("""{"elements":[$ways]}""",LatLng(0.0,0.0))
        assertTrue(roads.isNotEmpty())
        assertTrue(ProtocolCodec.encode(RoadContext(Long.MAX_VALUE,"r".repeat(64),roads)).second.toByteArray().size<=6000)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsIncompleteServerResults() {
        RoadContextProvider.parse("""{"remark":"runtime error: timed out","elements":[]}""",LatLng(0.0,0.0))
    }
}
