package com.sinema.util

import com.sinema.model.FolderItem
import com.sinema.model.Scene

object FolderHelper {
    
    /**
     * Given a list of scenes and a current folder path, extract the immediate
     * children: subfolders and files directly in this folder.
     */
    fun buildFolderContents(scenes: List<Scene>, currentPath: String): List<FolderItem> {
        val normalizedPath = currentPath.trimEnd('/')
        val subfolders = mutableMapOf<String, Int>() // folder name -> count
        val subfolderFirstScene = mutableMapOf<String, String>() // folder path -> first scene id
        val files = mutableListOf<FolderItem>()

        for (scene in scenes) {
            val scenePath = scene.path
            val sceneFolder = scenePath.substringBeforeLast('/')
            
            if (sceneFolder == normalizedPath) {
                // Direct child file
                files.add(FolderItem(
                    name = scene.filename,
                    fullPath = scenePath,
                    isFolder = false,
                    scene = scene
                ))
            } else if (sceneFolder.startsWith("$normalizedPath/")) {
                // In a subfolder - extract immediate subfolder name
                val remainder = sceneFolder.removePrefix("$normalizedPath/")
                val subfolderName = remainder.split('/')[0]
                val fullSubPath = "$normalizedPath/$subfolderName"
                subfolders[fullSubPath] = (subfolders[fullSubPath] ?: 0) + 1
                if (!subfolderFirstScene.containsKey(fullSubPath)) {
                    subfolderFirstScene[fullSubPath] = scene.id
                }
            }
        }

        val folderItems = subfolders.map { (path, count) ->
            FolderItem(
                name = path.substringAfterLast('/'),
                fullPath = path,
                isFolder = true,
                childCount = count,
                firstSceneId = subfolderFirstScene[path]
            )
        }.sortedBy { it.name.lowercase() }

        return folderItems + files.sortedBy { it.name.lowercase() }
    }

    fun getTopLevelFolders(scenes: List<Scene>): List<FolderItem> {
        // Find the common root
        val allFolders = scenes.map { it.folder }.distinct()
        if (allFolders.isEmpty()) return emptyList()
        
        // Find shortest common prefix
        val root = findCommonPrefix(allFolders)
        return buildFolderContents(scenes, root)
    }

    private fun findCommonPrefix(paths: List<String>): String {
        if (paths.isEmpty()) return ""
        if (paths.size == 1) return paths[0]
        
        // Most scenes are in /data, so use that as root
        val sorted = paths.sorted()
        val first = sorted.first()
        val last = sorted.last()
        var i = 0
        while (i < first.length && i < last.length && first[i] == last[i]) i++
        val prefix = first.substring(0, i).trimEnd('/')
        return if (prefix.isEmpty()) "/" else prefix
    }
}
