package dev.forge.agent

import dev.forge.agent.core.ChatMessage
import dev.forge.agent.core.SessionManager
import dev.forge.agent.core.TokenBudget
import dev.forge.agent.llm.LlmMessage
import dev.forge.agent.tools.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ForgeUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testTokenBudget() {
        val budget = TokenBudget(contextWindow = 1000)
        val messages = listOf(
            ChatMessage.User("1", content = "Hello world! This is a test message."),
            ChatMessage.Assistant("2", content = "Here is the response code.")
        )
        val estimate = budget.estimate(messages)
        assertTrue(estimate > 0)
        assertTrue(budget.remaining(messages) > 0)
        assertFalse(budget.shouldCompress(messages))
    }

    @Test
    fun testToolRegistry() {
        val registry = ToolRegistry.defaultRegistry()
        assertNotNull(registry.get("read_file"))
        assertNotNull(registry.get("write_file"))
        assertNotNull(registry.get("edit_file"))
        assertNotNull(registry.get("delete_file"))
        assertNotNull(registry.get("list_directory"))
        assertNotNull(registry.get("list_dir"))
        assertNotNull(registry.get("glob"))
        assertNotNull(registry.get("grep"))
        assertNotNull(registry.get("bash"))
        assertNotNull(registry.get("webfetch"))
        assertNotNull(registry.get("web_fetch"))

        val planModeDefs = registry.getDefinitions(planMode = true)
        // In plan mode, only readOnly tools should be included
        assertTrue(planModeDefs.any { it.name == "read_file" })
        assertTrue(planModeDefs.any { it.name == "list_directory" })
        assertTrue(planModeDefs.any { it.name == "glob" })
        assertTrue(planModeDefs.any { it.name == "grep" })
        assertTrue(planModeDefs.any { it.name == "webfetch" })
        assertFalse(planModeDefs.any { it.name == "write_file" })
        assertFalse(planModeDefs.any { it.name == "edit_file" })
        assertFalse(planModeDefs.any { it.name == "delete_file" })
        assertFalse(planModeDefs.any { it.name == "bash" })
    }

    @Test
    fun testAllToolsExecution() = runBlocking {
        val workspace = tempFolder.root
        val writeTool = WriteFileTool()
        val readTool = ReadFileTool()
        val editTool = EditFileTool()
        val listDirTool = ListDirectoryTool()
        val globTool = GlobTool()
        val grepTool = GrepTool()
        val deleteTool = DeleteFileTool()

        // 1. Write file
        val writeRes = writeTool.execute("""{"path":"src/Main.kt","content":"package demo\n\nfun main() {\n    println(\"Hello Forge\")\n}"}""", workspace)
        assertFalse(writeRes.isError)
        assertTrue(File(workspace, "src/Main.kt").exists())

        // 2. Read file
        val readRes = readTool.execute("""{"path":"src/Main.kt"}""", workspace)
        assertFalse(readRes.isError)
        assertTrue(readRes.output.contains("println"))

        // 3. Edit file
        val editRes = editTool.execute("""{"path":"src/Main.kt","old_string":"Hello Forge","new_string":"Hello World"}""", workspace)
        assertFalse(editRes.isError)
        assertTrue(File(workspace, "src/Main.kt").readText().contains("Hello World"))

        // 4. List directory
        val listRes = listDirTool.execute("""{"path":"."}""", workspace)
        assertFalse(listRes.isError)
        assertTrue(listRes.output.contains("src/"))

        // 5. Glob
        val globRes = globTool.execute("""{"pattern":"**/*.kt"}""", workspace)
        assertFalse(globRes.isError)
        assertTrue(globRes.output.contains("Main.kt"))

        // 6. Grep
        val grepRes = grepTool.execute("""{"pattern":"Hello"}""", workspace)
        assertFalse(grepRes.isError)
        assertTrue(grepRes.output.contains("Hello World"))

        // 7. Delete file
        val deleteRes = deleteTool.execute("""{"path":"src/Main.kt"}""", workspace)
        assertFalse(deleteRes.isError)
        assertFalse(File(workspace, "src/Main.kt").exists())
    }

    @Test
    fun testFileToolsLifecycle() = runBlocking {
        val workspace = tempFolder.root
        val writeTool = WriteFileTool()
        val readTool = ReadFileTool()
        val editTool = EditFileTool()
        val deleteTool = DeleteFileTool()

        // 1. Write file
        val writeRes = writeTool.execute("""{"path":"test.txt","content":"line 1\nline 2\nline 3"}""", workspace)
        assertFalse(writeRes.isError)
        assertTrue(File(workspace, "test.txt").exists())

        // 2. Read file
        val readRes = readTool.execute("""{"path":"test.txt"}""", workspace)
        assertFalse(readRes.isError)
        assertTrue(readRes.output.contains("line 1"))
        assertTrue(readRes.output.contains("line 2"))

        // 3. Edit file
        val editRes = editTool.execute("""{"path":"test.txt","old_string":"line 2","new_string":"line TWO"}""", workspace)
        assertFalse(editRes.isError)
        val content = File(workspace, "test.txt").readText()
        assertTrue(content.contains("line TWO"))

        // 4. Delete file
        val deleteRes = deleteTool.execute("""{"path":"test.txt"}""", workspace)
        assertFalse(deleteRes.isError)
        assertFalse(File(workspace, "test.txt").exists())
    }

    @Test
    fun testSessionManagerPersistence() {
        val sessionsDir = tempFolder.newFolder("sessions")
        val sm = SessionManager(sessionsDir)

        val sessionId = sm.createSession(title = "Test Session", model = "gemini-2.5-flash")
        sm.append(sessionId, ChatMessage.User("u1", content = "Write a function"))
        sm.append(sessionId, ChatMessage.Assistant("a1", content = "fun test() = true"))

        val loaded = sm.load(sessionId)
        assertEquals(2, loaded.size)
        assertTrue(loaded[0] is ChatMessage.User)
        assertEquals("Write a function", (loaded[0] as ChatMessage.User).content)
        assertTrue(loaded[1] is ChatMessage.Assistant)
        assertEquals("fun test() = true", (loaded[1] as ChatMessage.Assistant).content)

        val list = sm.listSessions()
        assertEquals(1, list.size)
        assertEquals("Test Session", list[0].title)

        sm.deleteSession(sessionId)
        assertEquals(0, sm.listSessions().size)
    }

    @Test
    fun testChatMessageToLlm() {
        val user = ChatMessage.User("1", content = "ping")
        val llmMsg = user.toLlm()
        assertEquals(LlmMessage.Role.USER, llmMsg.role)
        assertEquals("ping", llmMsg.content)
    }
}
