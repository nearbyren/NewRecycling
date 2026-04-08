package com.recycling.toolsapps.utils

import java.io.File

object FilePrefixSearcher {

    /**
     * 使用 pattern.find() 方法匹配文件前缀
     */
    fun matchFilesWithFind(fileNames: List<String>, targetPrefix: String): List<String> {
        // 构建正则表达式，匹配以目标前缀开头的文件名
        val pattern = Regex("^${Regex.escape(targetPrefix)}")

        return fileNames.filter { fileName ->
            // 使用 pattern.find() 查找匹配项
            val matchResult = pattern.find(fileName)
            matchResult != null // 如果找到匹配则返回true
        }
    }

    /**
     * 从指定目录匹配文件
     */
    fun matchFilesFromDirectory(directoryPath: String, targetPrefix: String): List<String> {
        val directory = File(directoryPath)

        // 检查目录是否存在
        if (!directory.exists() || !directory.isDirectory) {
            println("目录不存在: $directoryPath")
            return emptyList()
        }

        // 获取目录下所有文件
        val files = directory.listFiles() ?: return emptyList()

        val pattern = Regex("^${Regex.escape(targetPrefix)}")

        return files
            .filter { it.isFile }
            .map { it.name }
            .filter { fileName ->
                pattern.find(fileName) != null
            }
    }

    /**
     * 提取匹配的详细信息
     */
    fun extractMatchDetails(fileNames: List<String>, targetPrefix: String) {
        val pattern = Regex("^${Regex.escape(targetPrefix)}")

        fileNames.forEach { fileName ->
            val matchResult = pattern.find(fileName)

            if (matchResult != null) {
                val matchedPart = matchResult.value
                val remainingPart = fileName.substring(matchedPart.length)

                println("文件: $fileName")
                println("  └─ 匹配部分: '$matchedPart'")
                println("     剩余部分: '$remainingPart'")
                println("     匹配位置: ${matchResult.range}")
            }
        }
    }

    /**
     * 批量匹配多个前缀
     */
    fun batchMatchFiles(fileNames: List<String>, prefixes: List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()

        prefixes.forEach { prefix ->
            val pattern = Regex("^${Regex.escape(prefix)}")
            val matched = fileNames.filter { pattern.find(it) != null }
            result[prefix] = matched
        }

        return result
    }
}