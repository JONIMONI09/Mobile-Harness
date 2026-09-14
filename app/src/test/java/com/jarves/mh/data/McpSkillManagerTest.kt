package com.jarves.mh.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class McpSkillManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun readMcpConfigReturnsDefaultWhenFileMissing() = runBlocking {
        val workspace = tempFolder.newFolder("workspace_default")
        val config = McpSkillManager.readMcpConfig(workspace)
        assertTrue(config.contains("mcpServers"))
    }

    @Test
    fun saveAndReadMcpConfigPersistsValidJson() = runBlocking {
        val workspace = tempFolder.newFolder("workspace_mcp")
        val json = """
            {
              "mcpServers": {
                "fetch": {
                  "command": "uvx",
                  "args": ["mcp-server-fetch"]
                }
              }
            }
        """.trimIndent()

        val saved = McpSkillManager.saveMcpConfig(workspace, json)
        assertTrue(saved)

        val read = McpSkillManager.readMcpConfig(workspace)
        assertTrue(read.contains("mcp-server-fetch"))
        assertTrue(read.contains("uvx"))
    }

    @Test
    fun saveMcpConfigRejectsInvalidJson() = runBlocking {
        val workspace = tempFolder.newFolder("workspace_invalid_mcp")
        val saved = McpSkillManager.saveMcpConfig(workspace, "{ not: valid: json }")
        assertFalse(saved)
    }

    @Test
    fun saveListReadAndDeleteCustomSkills() = runBlocking {
        val workspace = tempFolder.newFolder("workspace_skills")

        // Initially empty
        val initialSkills = McpSkillManager.listSkills(workspace)
        assertTrue(initialSkills.isEmpty())

        // Save skill 1
        val saved1 = McpSkillManager.saveSkill(workspace, "review-pr", "# Review PR instructions\nRun lint and diff.")
        assertTrue(saved1)

        // Save skill 2 with slash prefix and .md suffix
        val saved2 = McpSkillManager.saveSkill(workspace, "/compact.md", "# Compact instructions\nClean memory.")
        assertTrue(saved2)

        // List skills
        val skills = McpSkillManager.listSkills(workspace)
        assertEquals(2, skills.size)
        assertEquals("compact", skills[0].name)
        assertEquals("compact.md", skills[0].filename)
        assertEquals("review-pr", skills[1].name)
        assertEquals("review-pr.md", skills[1].filename)

        // Read specific skill
        val readSkill = McpSkillManager.readSkill(workspace, "review-pr")
        assertNotNull(readSkill)
        assertTrue(readSkill!!.contains("Run lint and diff."))

        // Delete skill
        val deleted = McpSkillManager.deleteSkill(workspace, "compact")
        assertTrue(deleted)

        val afterDelete = McpSkillManager.listSkills(workspace)
        assertEquals(1, afterDelete.size)
        assertEquals("review-pr", afterDelete[0].name)

        val readDeleted = McpSkillManager.readSkill(workspace, "compact")
        assertNull(readDeleted)
    }
}
