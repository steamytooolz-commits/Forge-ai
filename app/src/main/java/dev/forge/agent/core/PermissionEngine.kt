package dev.forge.agent.core

import dev.forge.agent.tools.ToolPermission

class PermissionEngine {
    private val toolPermissions = mutableMapOf<String, ToolPermission>()
    private var defaultPermission = ToolPermission.ALLOW

    fun setPermission(toolName: String, permission: ToolPermission) {
        toolPermissions[toolName] = permission
    }

    fun getPermission(toolName: String): ToolPermission {
        // Check exact match
        toolPermissions[toolName]?.let { return it }

        // Check wildcard matches (e.g. "github__*" or "lsp_*")
        for ((pattern, perm) in toolPermissions) {
            if (pattern.endsWith("*")) {
                val prefix = pattern.removeSuffix("*")
                if (toolName.startsWith(prefix)) return perm
            }
        }

        // Return default tool permission
        return defaultPermission
    }

    fun getAllPermissions(): Map<String, ToolPermission> = toolPermissions.toMap()
}
