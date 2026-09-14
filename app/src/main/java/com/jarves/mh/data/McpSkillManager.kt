package com.jarves.mh.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class CustomSkillItem(
    val name: String,
    val filename: String,
    val content: String,
)

object McpSkillManager {
    const val MCP_CONFIG_FILENAME = ".mcp.json"
    const val COMMANDS_SUBDIR = ".claude/commands"

    fun resolveWorkspaceDir(context: Context, projectId: String? = null, rootPath: String? = null): File {
        val baseDir = if (!projectId.isNullOrBlank()) {
            File(context.filesDir, "workspaces/$projectId")
        } else {
            File(context.filesDir, "workspaces/terminal")
        }
        val dir = if (!rootPath.isNullOrBlank()) {
            val sub = File(baseDir, rootPath).canonicalFile
            if (sub.toPath().startsWith(baseDir.toPath())) sub else baseDir
        } else {
            baseDir
        }
        if (!dir.exists()) {
            dir.mkdirs()
            grantFullPermissions(dir)
        }
        return dir
    }

    fun sanitizeSkillName(skillName: String): String {
        return skillName.trim()
            .removePrefix("/")
            .removeSuffix(".md")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .trim('-')
    }

    fun getMcpConfigFile(workspaceDir: File): File = File(workspaceDir, MCP_CONFIG_FILENAME)

    fun getCommandsDir(workspaceDir: File): File = File(workspaceDir, COMMANDS_SUBDIR)

    private fun grantFullPermissions(file: File) {
        try {
            file.setReadable(true, false)
            file.setWritable(true, false)
            file.setExecutable(true, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun readMcpConfig(workspaceDir: File): String = withContext(Dispatchers.IO) {
        try {
            val file = getMcpConfigFile(workspaceDir)
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                val defaultJson = JSONObject().apply {
                    put("mcpServers", JSONObject())
                }
                defaultJson.toString(2)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "{\n  \"mcpServers\": {}\n}"
        }
    }

    suspend fun saveMcpConfig(workspaceDir: File, jsonContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Validate that the content is valid JSON
            val parsed = JSONObject(jsonContent)
            val file = getMcpConfigFile(workspaceDir)
            file.parentFile?.mkdirs()
            file.parentFile?.let { grantFullPermissions(it) }

            file.writeText(parsed.toString(2))
            grantFullPermissions(file)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun listSkills(workspaceDir: File): List<CustomSkillItem> = withContext(Dispatchers.IO) {
        try {
            val commandsDir = getCommandsDir(workspaceDir)
            if (!commandsDir.exists() || !commandsDir.isDirectory) {
                return@withContext emptyList()
            }

            val files = commandsDir.listFiles { file -> file.isFile && file.name.endsWith(".md") } ?: return@withContext emptyList()
            files.sortedBy { it.name }.map { file ->
                val skillName = file.name.removeSuffix(".md")
                CustomSkillItem(
                    name = skillName,
                    filename = file.name,
                    content = file.readText(),
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun readSkill(workspaceDir: File, skillName: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanName = sanitizeSkillName(skillName)
            if (cleanName.isBlank()) return@withContext null
            val file = File(getCommandsDir(workspaceDir), "$cleanName.md")
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveSkill(
        workspaceDir: File,
        skillName: String,
        content: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanName = sanitizeSkillName(skillName)
            if (cleanName.isBlank()) return@withContext false

            val commandsDir = getCommandsDir(workspaceDir)
            if (!commandsDir.exists()) {
                commandsDir.mkdirs()
                commandsDir.parentFile?.let { grantFullPermissions(it) }
                grantFullPermissions(commandsDir)
            }

            val file = File(commandsDir, "$cleanName.md")
            file.writeText(content)
            grantFullPermissions(file)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteSkill(workspaceDir: File, skillName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanName = sanitizeSkillName(skillName)
            if (cleanName.isBlank()) return@withContext false
            val file = File(getCommandsDir(workspaceDir), "$cleanName.md")
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
