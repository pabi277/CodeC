package com.codeci.ide.ui.projects

import java.io.File

sealed class FileNode {
    abstract val file: File
    abstract val relativePath: String
    abstract val depth: Int

    data class DirectoryNode(
        override val file: File,
        override val relativePath: String,
        override val depth: Int,
        val isExpanded: Boolean,
        val children: List<FileNode>
    ) : FileNode()

    data class FileLeaf(
        override val file: File,
        override val relativePath: String,
        override val depth: Int,
        val extension: String,
        val sizeBytes: Long
    ) : FileNode()
}

/** Pure filesystem operations used by the project tree and host tests. */
object FileTreeRepository {
    fun buildTree(root: File, expandedDirectories: Set<String> = emptySet()): FileNode.DirectoryNode {
        val canonicalRoot = root.canonicalFile
        return buildDirectory(
            root = canonicalRoot,
            directory = canonicalRoot,
            relativePath = "",
            depth = -1,
            expandedDirectories = expandedDirectories
        )
    }

    fun flattenVisible(root: FileNode.DirectoryNode): List<FileNode> {
        val result = mutableListOf<FileNode>()
        fun append(directory: FileNode.DirectoryNode) {
            directory.children.forEach { child ->
                result += child
                if (child is FileNode.DirectoryNode && child.isExpanded) append(child)
            }
        }
        append(root)
        return result
    }

    fun createDirectory(root: File, parentPath: String, name: String): Result<String> {
        val safeName = ProjectPathUtils.sanitizeSegment(name)
            ?: return Result.failure(IllegalArgumentException("Invalid folder name"))
        val parent = ProjectPathUtils.resolveInside(root, parentPath)
            ?: return Result.failure(IllegalArgumentException("Invalid parent folder"))
        if (!parent.isDirectory) return Result.failure(IllegalArgumentException("Parent folder does not exist"))
        val target = File(parent, safeName)
        if (target.exists()) return Result.failure(IllegalStateException("A file or folder with that name already exists"))
        return if (target.mkdirs()) {
            Result.success(ProjectPathUtils.relativePath(root, target) ?: safeName)
        } else {
            Result.failure(IllegalStateException("Could not create folder"))
        }
    }

    fun createFile(root: File, parentPath: String, name: String, content: String = ""): Result<String> {
        val safeName = ProjectPathUtils.sanitizeSegment(name)
            ?: return Result.failure(IllegalArgumentException("Invalid file name"))
        val parent = ProjectPathUtils.resolveInside(root, parentPath)
            ?: return Result.failure(IllegalArgumentException("Invalid parent folder"))
        if (!parent.isDirectory) return Result.failure(IllegalArgumentException("Parent folder does not exist"))
        val target = File(parent, safeName)
        if (target.exists()) return Result.failure(IllegalStateException("A file or folder with that name already exists"))
        return try {
            target.writeText(content)
            Result.success(ProjectPathUtils.relativePath(root, target) ?: safeName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun rename(root: File, relativePath: String, newName: String): Result<String> {
        val safeName = ProjectPathUtils.sanitizeSegment(newName)
            ?: return Result.failure(IllegalArgumentException("Invalid name"))
        val source = ProjectPathUtils.resolveInside(root, relativePath)
            ?: return Result.failure(IllegalArgumentException("Invalid path"))
        if (source == root.canonicalFile || !source.exists()) {
            return Result.failure(IllegalArgumentException("Cannot rename this item"))
        }
        val parent = source.parentFile ?: return Result.failure(IllegalArgumentException("Invalid parent"))
        val target = File(parent, safeName)
        if (target.exists()) return Result.failure(IllegalStateException("A file or folder with that name already exists"))
        return if (source.renameTo(target)) {
            Result.success(ProjectPathUtils.relativePath(root, target) ?: safeName)
        } else {
            Result.failure(IllegalStateException("Could not rename item"))
        }
    }

    fun delete(root: File, relativePath: String): Boolean {
        val target = ProjectPathUtils.resolveInside(root, relativePath) ?: return false
        if (target == root.canonicalFile || !target.exists()) return false
        return target.deleteRecursively()
    }

    private fun buildDirectory(
        root: File,
        directory: File,
        relativePath: String,
        depth: Int,
        expandedDirectories: Set<String>
    ): FileNode.DirectoryNode {
        val children = directory.listFiles()
            ?.asSequence()
            ?.filter { child -> isSafeChild(root, child) }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { child ->
                val childPath = ProjectPathUtils.relativePath(root, child) ?: return@map null
                if (child.isDirectory) {
                    FileNode.DirectoryNode(
                        file = child,
                        relativePath = childPath,
                        depth = depth + 1,
                        isExpanded = childPath in expandedDirectories,
                        children = buildDirectory(
                            root,
                            child,
                            childPath,
                            depth + 1,
                            expandedDirectories
                        ).children
                    )
                } else {
                    FileNode.FileLeaf(
                        file = child,
                        relativePath = childPath,
                        depth = depth + 1,
                        extension = child.extension.lowercase(),
                        sizeBytes = child.length()
                    )
                }
            }
            ?.filterNotNull()
            ?.toList()
            ?: emptyList()
        return FileNode.DirectoryNode(
            file = directory,
            relativePath = relativePath,
            depth = depth,
            isExpanded = relativePath.isEmpty() || relativePath in expandedDirectories,
            children = children
        )
    }

    private fun isSafeChild(root: File, child: File): Boolean {
        // listFiles() returns ordinary absolute paths. A different canonical
        // path therefore identifies a symlink without using java.nio (minSdk 24).
        if (child.absoluteFile.path != child.canonicalFile.path) return false
        val relative = ProjectPathUtils.relativePath(root, child) ?: return false
        return ProjectPathUtils.resolveInside(root, relative) != null
    }
}
