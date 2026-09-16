package dev.forge.agent.data

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

sealed interface Workspace {
    val displayName: String
    val isWritable: Boolean

    fun list(relativePath: String = ""): List<WorkspaceEntry>
    fun readText(relativePath: String): String
    fun writeText(relativePath: String, content: String)
    fun delete(relativePath: String): Boolean
    fun exists(relativePath: String): Boolean
    fun isDirectory(relativePath: String): Boolean
    fun canonicalPath(relativePath: String): String
}

data class WorkspaceEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

class FileWorkspace(val root: File) : Workspace {
    init {
        if (!root.exists()) {
            root.mkdirs()
        }
    }

    override val displayName: String
        get() = root.name.ifBlank { root.absolutePath }

    override val isWritable: Boolean
        get() = root.canWrite()

    private fun resolve(relativePath: String): File {
        val sanitized = relativePath.trim().removePrefix("/")
        if (sanitized.contains("..")) {
            throw IllegalArgumentException("Path traversal forbidden: $relativePath")
        }
        val file = if (sanitized.isEmpty() || sanitized == ".") root else File(root, sanitized)
        val canonical = file.canonicalFile
        if (!canonical.path.startsWith(root.canonicalPath)) {
            throw IllegalArgumentException("Path escapes workspace root: $relativePath")
        }
        return canonical
    }

    override fun list(relativePath: String): List<WorkspaceEntry> {
        val target = resolve(relativePath)
        if (!target.exists() || !target.isDirectory) {
            return emptyList()
        }
        val files = target.listFiles() ?: return emptyList()
        return files.map {
            WorkspaceEntry(
                name = it.name,
                isDirectory = it.isDirectory,
                sizeBytes = if (it.isFile) it.length() else 0L,
                lastModified = it.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override fun readText(relativePath: String): String {
        val target = resolve(relativePath)
        if (!target.exists()) {
            throw FileNotFoundException("File not found: $relativePath")
        }
        if (target.isDirectory) {
            throw IOException("Cannot read text from a directory: $relativePath")
        }
        return target.readText(Charsets.UTF_8)
    }

    override fun writeText(relativePath: String, content: String) {
        val target = resolve(relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
    }

    override fun delete(relativePath: String): Boolean {
        val target = resolve(relativePath)
        if (!target.exists()) return false
        return if (target.isDirectory) {
            target.deleteRecursively()
        } else {
            target.delete()
        }
    }

    override fun exists(relativePath: String): Boolean {
        return resolve(relativePath).exists()
    }

    override fun isDirectory(relativePath: String): Boolean {
        return resolve(relativePath).isDirectory
    }

    override fun canonicalPath(relativePath: String): String {
        return resolve(relativePath).canonicalPath
    }
}

class SafWorkspace(
    val root: DocumentFile,
    val contentResolver: ContentResolver
) : Workspace {
    override val displayName: String
        get() = root.name ?: "SAF Workspace"

    override val isWritable: Boolean
        get() = root.canWrite()

    private fun walkSegments(relativePath: String): List<String> {
        val sanitized = relativePath.trim().removePrefix("/").removeSuffix("/")
        if (sanitized.contains("..")) {
            throw IllegalArgumentException("Path traversal forbidden: $relativePath")
        }
        if (sanitized.isEmpty() || sanitized == ".") return emptyList()
        return sanitized.split("/").filter { it.isNotEmpty() }
    }

    private fun findDoc(relativePath: String): DocumentFile? {
        val segments = walkSegments(relativePath)
        if (segments.isEmpty()) return root
        var current: DocumentFile = root
        for (segment in segments) {
            val next = current.listFiles().firstOrNull { it.name == segment } ?: return null
            current = next
        }
        return current
    }

    private fun ensureParentDirs(segments: List<String>): DocumentFile {
        var current: DocumentFile = root
        for (i in 0 until segments.size - 1) {
            val segment = segments[i]
            val next = current.listFiles().firstOrNull { it.name == segment }
            current = if (next != null && next.isDirectory) {
                next
            } else {
                current.createDirectory(segment)
                    ?: throw IOException("Failed to create SAF directory: $segment")
            }
        }
        return current
    }

    override fun list(relativePath: String): List<WorkspaceEntry> {
        val doc = findDoc(relativePath) ?: return emptyList()
        if (!doc.isDirectory) return emptyList()
        return doc.listFiles().map {
            WorkspaceEntry(
                name = it.name ?: "unnamed",
                isDirectory = it.isDirectory,
                sizeBytes = it.length(),
                lastModified = it.lastModified()
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override fun readText(relativePath: String): String {
        val doc = findDoc(relativePath)
            ?: throw FileNotFoundException("SAF file not found: $relativePath")
        if (doc.isDirectory) {
            throw IOException("Cannot read text from a directory: $relativePath")
        }
        val uri = doc.uri
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Failed to open input stream for $relativePath ($uri)")
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    override fun writeText(relativePath: String, content: String) {
        val segments = walkSegments(relativePath)
        if (segments.isEmpty()) {
            throw IllegalArgumentException("Cannot write to workspace root directly")
        }
        val fileName = segments.last()
        val parentDir = ensureParentDirs(segments)

        val existingDoc = parentDir.listFiles().firstOrNull { it.name == fileName }
        val targetDoc = if (existingDoc != null) {
            existingDoc
        } else {
            // Determine mime type
            val mimeType = when {
                fileName.endsWith(".kt") || fileName.endsWith(".java") -> "text/x-java-source"
                fileName.endsWith(".json") -> "application/json"
                fileName.endsWith(".xml") -> "application/xml"
                fileName.endsWith(".md") -> "text/markdown"
                fileName.endsWith(".html") -> "text/html"
                fileName.endsWith(".txt") -> "text/plain"
                else -> "text/plain"
            }
            parentDir.createFile(mimeType, fileName)
                ?: throw IOException("Failed to create SAF file: $fileName in parent ${parentDir.name}")
        }

        val outputStream = contentResolver.openOutputStream(targetDoc.uri, "wt")
            ?: throw IOException("Failed to open output stream for ${targetDoc.uri}")
        outputStream.bufferedWriter(Charsets.UTF_8).use {
            it.write(content)
        }
    }

    override fun delete(relativePath: String): Boolean {
        val doc = findDoc(relativePath) ?: return false
        return doc.delete()
    }

    override fun exists(relativePath: String): Boolean {
        return findDoc(relativePath) != null
    }

    override fun isDirectory(relativePath: String): Boolean {
        return findDoc(relativePath)?.isDirectory ?: false
    }

    override fun canonicalPath(relativePath: String): String {
        val segments = walkSegments(relativePath)
        return "/" + segments.joinToString("/")
    }
}
