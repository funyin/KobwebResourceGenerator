package com.funyin.plugin.utils

import com.funyin.plugin.setting.PluginSetting
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.configuration.isGradleModule
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.plugins.gradle.util.GradleModuleData
import org.yaml.snakeyaml.Yaml
import java.io.FileInputStream
import java.util.*
import java.util.regex.Pattern

/**
 * 基于Module来处理Assets
 */
object FileHelperNew {

    /**
     * 获取所有可用的Flutter Module的Asset配置
     */
    @JvmStatic
    fun getResources(project: Project): List<ModuleGradleConfig> {
        val gradleModules = project.allModules().filter { it.isGradleModule() }
        val folders = mutableListOf<ModuleGradleConfig>()
        for (module in gradleModules) {
            val moduleDir = module.guessModuleDir()
            if (moduleDir != null) {
                getGradleConfig(module)?.let {
                    folders.add(it)
                }
            }
        }

        return folders
    }

    @JvmStatic
    fun shouldActivateFor(project: Project): Boolean {
        return project.allModules().any { it.isGradleModule() }
    }


    @JvmStatic
    fun getGradleConfig(module: Module): ModuleGradleConfig? {
        try {
            val moduleDir = module.guessModuleDir()
            val gradleRoot = module.isGradleModule()
            if (moduleDir != null && gradleRoot != null) {
                val fis = FileInputStream(gradleRoot.gradle)
                val pubConfigMap = Yaml().load(fis) as? Map<String, Any>
                if (pubConfigMap != null) {
                    val assetVFiles = mutableListOf<VirtualFile>()
                    val resources = mutableListOf("")
                    for (path in resources) {
                        moduleDir.findFileByRelativePath(path)?.let {
                            if (it.isDirectory) {
                                val index = path.indexOf("/")
                                val assetsPath = if (index == -1) {
                                    path
                                } else {
                                    path.substring(0, index)
                                }
                                val assetVFile = moduleDir.findChild(assetsPath)
                                    ?: moduleDir.createChildDirectory(this, assetsPath)
                                if (!assetVFiles.contains(assetVFile)) {
                                    assetVFiles.add(assetVFile)
                                }
                            } else {
                                if (!assetVFiles.contains(it)) {
                                    assetVFiles.add(it)
                                }
                            }
                        }
                    }
                    return ModuleGradleConfig(
                        module,
                        gradleRoot,
                        assetVFiles,
                        pubConfigMap,
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return null
    }

    /**
     * 读取配置
     */
    private fun readSetting(config: ModuleGradleConfig, key: String): Any? {
        (config.map[Constants.KEY_CONFIGURATION_MAP] as? Map<*, *>)?.let { configureMap ->
            return configureMap[key]
        }
        return null
    }

    /**
     * 是否开启了自动检测
     */
    fun isAutoDetectionEnable(config: ModuleGradleConfig): Boolean {
        return readSetting(config, Constants.KEY_AUTO_DETECTION) as Boolean? ?: PluginSetting.instance.autoDetection
    }

    /**
     * 是否根据父文件夹命名 默认true
     */
    fun isNamedWithParent(config: ModuleGradleConfig): Boolean {
        return readSetting(config, Constants.KEY_NAMED_WITH_PARENT) as Boolean?
            ?: PluginSetting.instance.namedWithParent
    }

    /**
     * 读取生成的类名配置
     */
    fun getGeneratedClassName(config: ModuleGradleConfig): String {
        return readSetting(config, Constants.KEY_CLASS_NAME) as String? ?: PluginSetting.instance.className
        ?: Constants.DEFAULT_CLASS_NAME
    }

    /**
     * 读取文件分割配置
     */
    fun getFilenameSplitPattern(config: ModuleGradleConfig): String {
        return try {
            val pattern =
                readSetting(config, Constants.FILENAME_SPLIT_PATTERN) as String?
                    ?: PluginSetting.instance.filenameSplitPattern ?: Constants.DEFAULT_FILENAME_SPLIT_PATTERN
            Pattern.compile(pattern)
            pattern
        } catch (e: Exception) {
            e.printStackTrace()
            Constants.DEFAULT_FILENAME_SPLIT_PATTERN
        }
    }

    /**
     * 读取忽略文件目录
     */
    fun getPathIgnore(config: ModuleGradleConfig): List<String> {
        return try {
            val paths =
                readSetting(config, Constants.PATH_IGNORE) as List<String>?
                    ?: emptyList()
            paths
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取generated自动生成目录
     * 从yaml中读取
     */
    private fun getGeneratedFilePath(config: ModulePubSpecConfig): VirtualFile {
        return config.pubRoot.lib?.let { lib ->
            // 没有配置则返回默认path
            val filePath: String = readSetting(config, Constants.KEY_OUTPUT_DIR) as String?
                ?: PluginSetting.instance.filePath ?: Constants.DEFAULT_OUTPUT_DIR
            println("getGeneratedFilePath $filePath")
            if (!filePath.contains(File.separator)) {
                return@let lib.findOrCreateChildDir(lib, filePath)
            } else {
                var file = lib
                filePath.split(File.separator).forEach { dir ->
                    if (dir.isNotEmpty()) {
                        file = file.findOrCreateChildDir(file, dir)
                    }
                }
                return@let file
            }
        }!!
    }

    private fun VirtualFile.findOrCreateChildDir(requestor: Any, name: String): VirtualFile {
        val child = findChild(name)
        return child ?: createChildDirectory(requestor, name)
    }

    /**
     * 获取需要生成的文件 如果没有则会创建文件
     */
    fun getGeneratedFile(config: ModuleGradleConfig): VirtualFile {
        return getGeneratedFilePath(config).let {
            val configName = getGeneratedFileName(config)
            return@let it.findOrCreateChildData(
                it,
                "$configName.kt"
            )
        }
    }

    fun getGeneratedFileName(config: ModuleGradleConfig): String =
        readSetting(config, Constants.KEY_OUTPUT_FILENAME) as? String ?: PluginSetting.instance.fileName
        ?: Constants.DEFAULT_CLASS_NAME.lowercase()

}

/**
 * 模块Flutter配置信息
 */
data class ModuleGradleConfig(
    val module: Module,
    val pubRoot: PubRoot,
    val assetVFiles: List<VirtualFile>,
    val map: Map<String, Any>,
)
