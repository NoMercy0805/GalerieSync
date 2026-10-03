package de.galeriesync.sync
import org.junit.Assert.*
import org.junit.Test
class RemoteLayoutTest {
 @Test fun oldCameraLayoutMapsToCurrentPhonePath(){assertEquals("DCIM/Camera/a.jpg",phonePathForRemote("Bilder & Videos/Camera/a.jpg",setOf("DCIM/Camera")))}
 @Test fun currentLayoutRemainsUnchanged(){assertEquals("DCIM/Camera/a.jpg",phonePathForRemote("DCIM/Camera/a.jpg",setOf("DCIM/Camera")))}
 @Test fun ambiguousLegacyFoldersRemainUnmapped(){val old="Bilder & Videos/Camera/a.jpg";assertEquals(old,phonePathForRemote(old,setOf("DCIM/Camera","Pictures/Camera")))}
 @Test fun legacyNestedFoldersMatch(){assertEquals("Pictures/App/Sub/a.png",phonePathForRemote("Bilder & Videos/App/Sub/a.png",setOf("Pictures/App","Pictures/App/Sub")))}
 @Test fun matchingIsCaseInsensitive(){assertEquals("DCIM/Camera/a.jpg",phonePathForRemote("bilder & videos/camera/a.jpg",setOf("DCIM/Camera")))}
 @Test fun legacyConversionMatchesHistoricCode(){assertEquals("Bilder & Videos/Camera/a.jpg",legacyMediaPath("DCIM/Camera/a.jpg"));assertEquals("Bilder & Videos/Downloads/a.jpg",legacyMediaPath("Downloads/a.jpg"))}
}
