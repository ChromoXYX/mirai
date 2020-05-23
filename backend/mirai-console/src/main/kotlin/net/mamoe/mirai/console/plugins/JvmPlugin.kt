/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "EXPOSED_SUPER_CLASS")


package net.mamoe.mirai.console.plugins

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.scheduler.PluginScheduler
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


interface JvmPlugin : Plugin, CoroutineScope {
    val logger: MiraiLogger
    val description: JvmPluginDescription

    @JvmDefault
    fun onLoad() {
    }

    @JvmDefault
    fun onEnable() {
    }

    @JvmDefault
    fun onDisable() {
    }
}


abstract class JavaPlugin @JvmOverloads constructor(
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : JvmPlugin, JvmPluginImpl(coroutineContext) {

    /**
     * Java API Scheduler
     */
    val scheduler: PluginScheduler? = PluginScheduler(this.coroutineContext)
}

abstract class KotlinPlugin @JvmOverloads constructor(
    coroutineContext: CoroutineContext = EmptyCoroutineContext
) : JvmPlugin, JvmPluginImpl(coroutineContext) {
    // that's it
}

@Serializable
data class JvmPluginDescription internal constructor( // serializer 可以用这个构造器
    override val kind: PluginKind,
    override val name: String,
    override val author: String,
    override val version: String,
    override val info: String,
    override val loadBefore: List<String>,
    override val dependencies: List<PluginDependency>
) : PluginDescription, FilePluginDescription {
    /**
     * 在手动实现时使用这个构造器.
     */
    @Suppress("unused")
    constructor(
        kind: PluginKind,
        name: String,
        author: String,
        version: String,
        info: String,
        loadBefore: List<String>,
        depends: List<PluginDependency>,
        file: File
    ) : this(kind, name, author, version, info, loadBefore, depends) {
        this._file = file
    }

    @Suppress("PropertyName")
    @Transient
    internal var _file: File? = null

    override val file: File
        get() = _file ?: error("Internal error: JvmPluginDescription(name=$name)._file == null")
}

internal abstract class JvmPluginImpl(
    parentCoroutineContext: CoroutineContext
) : JvmPlugin, CoroutineScope {
    /**
     * Initialized immediately after construction of [JvmPluginImpl] instance
     */
    @Suppress("PropertyName")
    internal lateinit var _description: JvmPluginDescription
    override val description: JvmPluginDescription get() = _description

    final override val logger: MiraiLogger by lazy { MiraiConsole.newLogger(this._description.name) }

    final override val coroutineContext: CoroutineContext by lazy {
        CoroutineExceptionHandler { _, throwable -> logger.error(throwable) }
            .plus(parentCoroutineContext)
            .plus(SupervisorJob(parentCoroutineContext[Job]))
    }
}

/**
 * 内建的 Jar (JVM) 插件加载器
 */
object JarPluginLoader : AbstractFilePluginLoader<JvmPlugin, JvmPluginDescription>("jar") {
    override fun getPluginDescription(plugin: JvmPlugin): JvmPluginDescription = plugin.description

    override fun Sequence<File>.mapToDescription(): List<JvmPluginDescription> {
        TODO(
            """
            CHECK IS JAR FILE AND CAN BE READ
            READ JAR FILE, EXTRACT PLUGIN DESCRIPTION
            SET JvmPluginDescription._file
            RETURN PLUGIN 
        """.trimIndent()
        )
    }

    @Throws(PluginLoadException::class)
    override fun load(description: JvmPluginDescription): JvmPlugin {
        TODO("FIND PLUGIN MAIN, THEN LOAD")
        // no need to check dependencies
    }

    override fun enable(plugin: JvmPlugin) = plugin.onEnable()
    override fun disable(plugin: JvmPlugin) = plugin.onDisable()
}

/*
object PluginManagerOld {
    /**
     * 通过插件获取介绍
     * @see description
     */
    fun getPluginDescription(base: PluginBase): PluginDescription {
        nameToPluginBaseMap.forEach { (s, pluginBase) ->
            if (pluginBase == base) {
                return pluginDescriptions[s]!!
            }
        }
        error("can not find plugin description")
    }

    /**
     * 获取所有插件摘要
     */
    fun getAllPluginDescriptions(): Collection<PluginDescription> {
        return pluginDescriptions.values
    }

    /**
     * 关闭所有插件
     */
    @JvmOverloads
    fun disablePlugins(throwable: CancellationException? = null) {
        pluginsSequence.forEach { plugin ->
            plugin.unregisterAllCommands()
            plugin.disable(throwable)
        }
        nameToPluginBaseMap.clear()
        pluginDescriptions.clear()
        pluginsLoader.clear()
        pluginsSequence.clear()
    }

    /**
     * 重载所有插件
     */
    fun reloadPlugins() {
        pluginsSequence.forEach {
            it.disable()
        }
        loadPlugins(false)
    }

    /**
     * 尝试加载全部插件
     */
    fun loadPlugins(clear: Boolean = true) = loadPluginsImpl(clear)


    //////////////////
    //// internal ////
    //////////////////

    internal val pluginsPath = (MiraiConsole.path + "/plugins/").replace("//", "/").also {
        File(it).mkdirs()
    }

    private val logger = MiraiConsole.newLogger("Plugin Manager")

    /**
     * 加载成功的插件, 名字->插件
     */
    internal val nameToPluginBaseMap: MutableMap<String, PluginBase> = mutableMapOf()

    /**
     * 加载成功的插件, 名字->插件摘要
     */
    private val pluginDescriptions: MutableMap<String, PluginDescription> = mutableMapOf()

    /**
     * 加载插件的PluginsLoader
     */
    private val pluginsLoader: PluginsLoader = PluginsLoader(this.javaClass.classLoader)

    /**
     * 插件优先级队列
     * 任何操作应该按这个Sequence顺序进行
     * 他的优先级取决于依赖,
     * 在这个队列中, 被依赖的插件会在依赖的插件之前
     */
    private val pluginsSequence: LockFreeLinkedList<PluginBase> = LockFreeLinkedList()


    /**
     * 广播Command方法
     */
    internal fun onCommand(command: Command, sender: CommandSender, args: List<String>) {
        pluginsSequence.forEach {
            try {
                it.onCommand(command, sender, args)
            } catch (e: Throwable) {
                logger.info(e)
            }
        }
    }


    @Volatile
    internal var lastPluginName: String = ""

    /**
     * 判断文件名/插件名是否已加载
     */
    private fun isPluginLoaded(file: File, name: String): Boolean {
        pluginDescriptions.forEach {
            if (it.key == name || it.value.file == file) {
                return true
            }
        }
        return false
    }

    /**
     * 寻找所有安装的插件（在文件夹）, 并将它读取, 记录位置
     * 这个不等同于加载的插件, 可以理解为还没有加载的插件
     */
    internal data class FindPluginsResult(
        val pluginsLocation: MutableMap<String, File>,
        val pluginsFound: MutableMap<String, PluginDescription>
    )

    internal fun findPlugins(): FindPluginsResult {
        val pluginsLocation: MutableMap<String, File> = mutableMapOf()
        val pluginsFound: MutableMap<String, PluginDescription> = mutableMapOf()

        File(pluginsPath).listFiles()?.forEach { file ->
            if (file != null && file.extension == "jar") {
                val jar = JarFile(file)
                val pluginYml =
                    jar.entries().asSequence().filter { it.name.toLowerCase().contains("plugin.yml") }.firstOrNull()

                if (pluginYml == null) {
                    logger.info("plugin.yml not found in jar " + jar.name + ", it will not be consider as a Plugin")
                } else {
                    try {
                        val description = PluginDescription.readFromContent(
                            URL("jar:file:" + file.absoluteFile + "!/" + pluginYml.name).openConnection().let {
                                val res = it.inputStream.use { input ->
                                    input.readBytes().encodeToString()
                                }

                                // 关闭jarFile，解决热更新插件问题
                                (it as JarURLConnection).jarFile.close()
                                res
                            }, file
                        )
                        if (!isPluginLoaded(file, description.name)) {
                            pluginsFound[description.name] = description
                            pluginsLocation[description.name] = file
                        }
                    } catch (e: Exception) {
                        logger.info(e)
                    }
                }
            }
        }
        return FindPluginsResult(pluginsLocation, pluginsFound)
    }

    internal fun loadPluginsImpl(clear: Boolean = true) {
        logger.info("""开始加载${pluginsPath}下的插件""")
        val findPluginsResult = findPlugins()
        val pluginsFound = findPluginsResult.pluginsFound
        val pluginsLocation = findPluginsResult.pluginsLocation

        //不仅要解决A->B->C->A, 还要解决A->B->A->A
        fun checkNoCircularDepends(
            target: PluginDescription,
            needDepends: List<String>,
            existDepends: MutableList<String>
        ) {

            if (!target.noCircularDepend) {
                return
            }

            existDepends.add(target.name)

            if (needDepends.any { existDepends.contains(it) }) {
                target.noCircularDepend = false
            }

            existDepends.addAll(needDepends)

            needDepends.forEach {
                if (pluginsFound.containsKey(it)) {
                    checkNoCircularDepends(pluginsFound[it]!!, pluginsFound[it]!!.depends, existDepends)
                }
            }
        }

        pluginsFound.values.forEach {
            checkNoCircularDepends(it, it.depends, mutableListOf())
        }

        //load plugin individually
        fun loadPlugin(description: PluginDescription): Boolean {
            if (!description.noCircularDepend) {
                logger.error("Failed to load plugin " + description.name + " because it has circular dependency")
                return false
            }

            if (description.loaded || nameToPluginBaseMap.containsKey(description.name)) {
                return true
            }

            description.depends.forEach { dependent ->
                if (!pluginsFound.containsKey(dependent)) {
                    logger.error("Failed to load plugin " + description.name + " because it need " + dependent + " as dependency")
                    return false
                }
                val depend = pluginsFound[dependent]!!

                if (!loadPlugin(depend)) {//先加载depend
                    logger.error("Failed to load plugin " + description.name + " because " + dependent + " as dependency failed to load")
                    return false
                }
            }

            logger.info("loading plugin " + description.name)

            val jarFile = pluginsLocation[description.name]!!
            val pluginClass = try {
                pluginsLoader.loadPluginMainClassByJarFile(description.name, description.basePath, jarFile)
            } catch (e: ClassNotFoundException) {
                pluginsLoader.loadPluginMainClassByJarFile(description.name, "${description.basePath}Kt", jarFile)
            }

            val subClass = pluginClass.asSubclass(PluginBase::class.java)

            lastPluginName = description.name
            val plugin: PluginBase =
                subClass.kotlin.objectInstance ?: subClass.getDeclaredConstructor().apply {
                    kotlin.runCatching {
                        this.isAccessible = true
                    }
                }.newInstance()
            plugin.dataFolder // initialize right now

            description.loaded = true
            logger.info("successfully loaded plugin " + description.name + " version " + description.version + " by " + description.author)
            logger.info(description.info)

            nameToPluginBaseMap[description.name] = plugin
            pluginDescriptions[description.name] = description
            plugin.pluginName = description.name
            pluginsSequence.addLast(plugin)//按照实际加载顺序加入队列
            return true
        }


        if (clear) {
            //清掉优先级队列, 来重新填充
            pluginsSequence.clear()
        }

        pluginsFound.values.forEach {
            try {
                // 尝试加载插件
                loadPlugin(it)
            } catch (e: Throwable) {
                pluginsLoader.remove(it.name)
                when (e) {
                    is ClassCastException -> logger.error(
                        "failed to load plugin " + it.name + " , Main class does not extends PluginBase",
                        e
                    )
                    is ClassNotFoundException -> logger.error(
                        "failed to load plugin " + it.name + " , Main class not found under " + it.basePath,
                        e
                    )
                    is NoClassDefFoundError -> logger.error(
                        "failed to load plugin " + it.name + " , dependent class not found.",
                        e
                    )
                    else -> logger.error("failed to load plugin " + it.name, e)
                }
            }
        }


        pluginsSequence.forEach {
            try {
                it.load()
            } catch (ignored: Throwable) {
                logger.info(ignored)
                logger.info(it.pluginName + " failed to load, disabling it")
                logger.info(it.pluginName + " 推荐立即删除/替换并重启")
                if (ignored is CancellationException) {
                    disablePlugin(it, ignored)
                } else {
                    disablePlugin(it)
                }
            }
        }

        pluginsSequence.forEach {
            try {
                it.enable()
            } catch (ignored: Throwable) {
                logger.info(ignored)
                logger.info(it.pluginName + " failed to enable, disabling it")
                logger.info(it.pluginName + " 推荐立即删除/替换并重启")
                if (ignored is CancellationException) {
                    disablePlugin(it, ignored)
                } else {
                    disablePlugin(it)
                }
            }
        }

        logger.info("""加载了${nameToPluginBaseMap.size}个插件""")
    }

    private fun disablePlugin(
        plugin: PluginBase,
        exception: CancellationException? = null
    ) {
        plugin.unregisterAllCommands()
        plugin.disable(exception)
        nameToPluginBaseMap.remove(plugin.pluginName)
        pluginDescriptions.remove(plugin.pluginName)
        pluginsLoader.remove(plugin.pluginName)
        pluginsSequence.remove(plugin)
    }


    /**
     * 根据插件名字找Jar的文件
     * null => 没找到
     * 这里的url的jarFile没关，热更新插件可能出事
     */
    internal fun getJarFileByName(pluginName: String): File? {
        File(pluginsPath).listFiles()?.forEach { file ->
            if (file != null && file.extension == "jar") {
                val jar = JarFile(file)
                val pluginYml =
                    jar.entries().asSequence().filter { it.name.toLowerCase().contains("plugin.yml") }.firstOrNull()
                if (pluginYml != null) {
                    val description =
                        PluginDescription.readFromContent(
                            URL("jar:file:" + file.absoluteFile + "!/" + pluginYml.name).openConnection().inputStream.use {
                                it.readBytes().encodeToString()
                            }, file
                        )
                    if (description.name.toLowerCase() == pluginName.toLowerCase()) {
                        return file
                    }
                }
            }
        }
        return null
    }


    /**
     * 根据插件名字找Jar中的文件
     * null => 没找到
     * 这里的url的jarFile没关，热更新插件可能出事
     */
    internal fun getFileInJarByName(pluginName: String, toFind: String): InputStream? {
        val jarFile = getJarFileByName(pluginName) ?: return null
        val jar = JarFile(jarFile)
        val toFindFile =
            jar.entries().asSequence().filter { it.name == toFind }.firstOrNull() ?: return null
        return URL("jar:file:" + jarFile.absoluteFile + "!/" + toFindFile.name).openConnection().inputStream
    }
}*/