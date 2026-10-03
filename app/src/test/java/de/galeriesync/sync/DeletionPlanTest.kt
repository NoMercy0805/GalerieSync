package de.galeriesync.sync
import org.junit.Assert.*
import org.junit.Test

class DeletionPlanTest {
 @Test fun mirrorDeletesMissingPhonePhoto(){
  assertEquals(setOf("DCIM/Camera/deleted.jpg"),nasDeletionPlan(setOf("DCIM/Camera/kept.jpg"),setOf("DCIM/Camera/kept.jpg","DCIM/Camera/deleted.jpg"),emptySet(),setOf("DCIM/Camera"),true))
 }
 @Test fun bidirectionalOnlyDeletesPreviouslySyncedFiles(){
  assertEquals(setOf("DCIM/Camera/deleted.jpg"),nasDeletionPlan(emptySet(),setOf("DCIM/Camera/deleted.jpg","DCIM/Camera/nas-only.jpg"),setOf("DCIM/Camera/deleted.jpg"),emptySet(),false))
 }
 @Test fun firstBidirectionalRunCannotInferDeletion(){
  assertTrue(nasDeletionPlan(emptySet(),setOf("DCIM/Camera/a.jpg"),emptySet(),emptySet(),false).isEmpty())
 }
 @Test fun pathsMatchWithoutDependingOnCaseOrSlashes(){
  assertTrue(nasDeletionPlan(setOf("DCIM\\Camera\\A.jpg"),setOf("dcim/camera/a.JPG"),emptySet(),emptySet(),true).isEmpty())
  assertEquals(setOf("dcim/camera/a.JPG"),nasDeletionPlan(emptySet(),setOf("dcim/camera/a.JPG"),setOf("DCIM\\Camera\\A.jpg"),emptySet(),false))
 }
 @Test fun deselectedFoldersRemainOnNas(){
  assertTrue(nasDeletionPlan(emptySet(),setOf("Pictures/Screenshots/a.png","DCIM/Camera-old/a.jpg"),setOf("Pictures/Screenshots/a.png"),setOf("DCIM/Camera"),true).isEmpty())
 }
 @Test fun emptySelectedFolderStillDeletesItsPreviousPhotos(){
  assertEquals(setOf("DCIM/Camera/a.jpg"),nasDeletionPlan(emptySet(),setOf("DCIM/Camera/a.jpg"),setOf("DCIM/Camera/a.jpg"),setOf("DCIM/Camera"),false))
 }
 @Test fun temporaryUploadsAreNotMediaDeletions(){
  assertTrue(nasDeletionPlan(emptySet(),setOf("DCIM/Camera/a.jpg.galeriesync-part"),emptySet(),emptySet(),true).isEmpty())
 }
}
