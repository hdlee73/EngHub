package com.hdlee73.englishstudy.listening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderImportNameTest {
    @Test
    fun audioNamesAreRecognisedByExtension() {
        for (name in listOf("a.mp3", "A.M4A", "talk.opus", "x.amr", "book.m4b", "z.FLAC", "w.wma")) assertTrue(name, FolderImport.isAudioName(name))
    }

    @Test
    fun otherFilesAreNotAudio() {
        for (name in listOf("notes.pdf", "movie.mkv", "readme", "photo.jpg", "a.srt")) assertFalse(name, FolderImport.isAudioName(name))
    }
}
