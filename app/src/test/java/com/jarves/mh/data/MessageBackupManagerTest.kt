package com.jarves.mh.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MessageBackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun backupMessageToFileCreatesFileAndAppendsEntries() = runBlocking {
        val testFile = File(tempFolder.root, "test_backups.json")
        assertFalse(testFile.exists())

        val success1 = MessageBackupManager.backupMessageToFile(testFile, "Hello World")
        assertTrue(success1)
        assertTrue(testFile.exists())

        val success2 = MessageBackupManager.backupMessageToFile(testFile, "Second message")
        assertTrue(success2)

        val entries = MessageBackupManager.loadBackupsFromFile(testFile)
        assertEquals(2, entries.size)
        assertEquals("Hello World", entries[0].text)
        assertTrue(entries[0].timestamp > 0)
        assertEquals("Second message", entries[1].text)
        assertTrue(entries[1].timestamp >= entries[0].timestamp)
    }

    @Test
    fun loadBackupsFromFileReturnsEmptyWhenFileDoesNotExist() = runBlocking {
        val nonExistent = File(tempFolder.root, "non_existent.json")
        val entries = MessageBackupManager.loadBackupsFromFile(nonExistent)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun clearBackupsFromFileDeletesExistingFile() = runBlocking {
        val testFile = File(tempFolder.root, "delete_me.json")
        MessageBackupManager.backupMessageToFile(testFile, "Will be deleted")
        assertTrue(testFile.exists())

        val cleared = MessageBackupManager.clearBackupsFromFile(testFile)
        assertTrue(cleared)
        assertFalse(testFile.exists())
    }

    @Test
    fun loadBackupsFromFileHandlesCorruptedJsonGracefully() = runBlocking {
        val testFile = File(tempFolder.root, "corrupt.json")
        testFile.writeText("{ this is not valid json")

        val entries = MessageBackupManager.loadBackupsFromFile(testFile)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun backupMessageToFileRecoversFromCorruptedJson() = runBlocking {
        val testFile = File(tempFolder.root, "corrupt_write.json")
        testFile.writeText("{ malformed json snippet")

        // Should not fail or crash, but safely recover and store the new message
        val success = MessageBackupManager.backupMessageToFile(testFile, "Recovered prompt")
        assertTrue(success)

        val entries = MessageBackupManager.loadBackupsFromFile(testFile)
        assertEquals(1, entries.size)
        assertEquals("Recovered prompt", entries[0].text)
    }

    @Test
    fun concurrentBackupsDoNotCorruptFile() = runBlocking {
        val testFile = File(tempFolder.root, "concurrent.json")
        val jobCount = 20
        val jobs = (1..jobCount).map { i ->
            async(Dispatchers.IO) {
                MessageBackupManager.backupMessageToFile(testFile, "Message $i")
            }
        }
        val results = jobs.map { it.await() }
        assertTrue(results.all { it })

        val entries = MessageBackupManager.loadBackupsFromFile(testFile)
        assertEquals(jobCount, entries.size)
    }
}
