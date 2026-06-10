package com.sinema.util

import com.sinema.model.FolderItem
import com.sinema.model.ImageItem
import com.sinema.model.Scene

object FolderHelper {

    /**
     * Given the scenes (videos) and images (pictures) under a folder path,
     * extract the immediate children: subfolders first, then videos, then
     * pictures.
     */
    fun buildFolderContents(
        scenes: List<Scene>,
        images: List<ImageItem>,
        currentPath: String
    ): List<FolderItem> {
        val normalizedPath = currentPath.trimEnd('/')
        val prefix = "$normalizedPath/"
        val subfolders = mutableMapOf<String, Int>() // folder path -> count
        val subfolderFirstScene = mutableMapOf<String, String>() // folder path -> first scene id
        val subfolderFirstImage = mutableMapOf<String, String>() // folder path -> first image id
        val videoFiles = mutableListOf<FolderItem>()
        val imageFiles = mutableListOf<FolderItem>()

        fun immediateSubfolder(fileFolder: String): String? =
            if (fileFolder.startsWith(prefix)) {
                prefix + fileFolder.removePrefix(prefix).substringBefore('/')
            } else null

        for (scene in scenes) {
            val sceneFolder = scene.path.substringBeforeLast('/')
            if (sceneFolder == normalizedPath) {
                videoFiles.add(FolderItem(
                    name = scene.filename,
                    fullPath = scene.path,
                    isFolder = false,
                    scene = scene
                ))
            } else immediateSubfolder(sceneFolder)?.let { sub ->
                subfolders[sub] = (subfolders[sub] ?: 0) + 1
                if (!subfolderFirstScene.containsKey(sub)) subfolderFirstScene[sub] = scene.id
            }
        }

        for (img in images) {
            val imageFolder = img.path.substringBeforeLast('/')
            if (imageFolder == normalizedPath) {
                imageFiles.add(FolderItem(
                    name = img.filename,
                    fullPath = img.path,
                    isFolder = false,
                    image = img
                ))
            } else immediateSubfolder(imageFolder)?.let { sub ->
                subfolders[sub] = (subfolders[sub] ?: 0) + 1
                if (!subfolderFirstImage.containsKey(sub)) subfolderFirstImage[sub] = img.id
            }
        }

        val folderItems = subfolders.map { (path, count) ->
            FolderItem(
                name = path.substringAfterLast('/'),
                fullPath = path,
                isFolder = true,
                childCount = count,
                firstSceneId = subfolderFirstScene[path],
                firstImageId = subfolderFirstImage[path]
            )
        }.sortedBy { it.name.lowercase() }

        return folderItems +
            videoFiles +
            imageFiles.sortedBy { it.name.lowercase() }
    }

    fun getTopLevelFolders(scenes: List<Scene>): List<FolderItem> {
        // Find the common root
        val allFolders = scenes.map { it.folder }.distinct()
        if (allFolders.isEmpty()) return emptyList()
        
        // Find shortest common prefix
        val root = findCommonPrefix(allFolders)
        return buildFolderContents(scenes, emptyList(), root)
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
