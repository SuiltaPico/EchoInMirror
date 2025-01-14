package cn.apisium.eim.impl.processor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.apisium.eim.ROOT_PATH
import cn.apisium.eim.api.processor.NativeAudioPlugin
import cn.apisium.eim.api.processor.NativeAudioPluginDescription
import cn.apisium.eim.api.processor.NativeAudioPluginFactory
import cn.apisium.eim.utils.mutableStateSetOf
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class NativeAudioPluginImpl(
    override val description: NativeAudioPluginDescription
) : NativeAudioPlugin, ProcessAudioProcessorImpl("D:\\Cpp\\EIMPluginScanner\\build\\EIMHost_artefacts\\Debug\\EIMHost.exe", " -L",
    JsonPrimitive(Json.encodeToString(NativeAudioPluginDescription.serializer(), description)).toString())

private val NATIVE_AUDIO_PLUGIN_CONFIG = ROOT_PATH.resolve("nativeAudioPlugin.json")

@Serializable
data class NativeAudioPluginFactoryData(
    val pluginDescriptions: MutableMap<String, NativeAudioPluginDescription>,
    val scanPaths: MutableSet<String>,
    val skipList: MutableSet<String>
)

class NativeAudioPluginFactoryImpl: NativeAudioPluginFactory {
    private val logger = LoggerFactory.getLogger(NativeAudioPluginFactoryImpl::class.java)
    override val name = "NativeAudioPluginFactory"
    override val pluginDescriptions = mutableStateMapOf<String, NativeAudioPluginDescription>()
    override val scanPaths = mutableStateSetOf<String>()
    override val skipList = mutableStateSetOf<String>()
    override lateinit var pluginExtensions: Set<String>
    var scannedCount by mutableStateOf(0)
    var allScanCount by mutableStateOf(0)
    val scanningPlugins = mutableStateMapOf<String, Process>()

    init {
        if (NATIVE_AUDIO_PLUGIN_CONFIG.toFile().exists()) {
            val json = NATIVE_AUDIO_PLUGIN_CONFIG.toFile().readText()
            val data = Json.decodeFromString(NativeAudioPluginFactoryData.serializer(), json)
            pluginDescriptions.putAll(data.pluginDescriptions)
            scanPaths.addAll(data.scanPaths)
            skipList.addAll(data.skipList)
        } else {
            if (SystemUtils.IS_OS_WINDOWS) {
                scanPaths.add("C:\\Program Files\\Common Files\\VST3")
                scanPaths.add("C:\\Program Files\\Steinberg\\VSTPlugins")
                scanPaths.add("C:\\Program Files\\VstPlugins")
                scanPaths.add("C:\\Program Files\\Native Instruments\\VSTPlugins 64 bit")
            } else if (SystemUtils.IS_OS_LINUX) {
                scanPaths.addAll(System.getenv("LADSPA_PATH")
                    .ifEmpty { "/usr/lib/ladspa;/usr/local/lib/ladspa;~/.ladspa" }
                    .replace(":", ";").split(";"))
            }
        }
        pluginExtensions = if (SystemUtils.IS_OS_WINDOWS) setOf("dll", "vst", "vst3")
        else if (SystemUtils.IS_OS_LINUX) setOf("so")
        else setOf("dylib")
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun scan()  {
        scanningPlugins.clear()
        val pluginList : MutableList<String> = mutableListOf()
        val scanned = hashSetOf<String>()
        scanned.addAll(skipList)
        pluginDescriptions.forEach { (_, it) -> scanned.add(it.fileOrIdentifier) }
        val scanVisitor = fileVisitor {
            onPreVisitDirectory { directory, _ ->
                if (directory.name.startsWith(".")) {
                    FileVisitResult.SKIP_SUBTREE
                } else {
                    FileVisitResult.CONTINUE
                }
            }
            onVisitFile { file, _ ->
                if (pluginExtensions.contains(file.extension) && !scanned.contains(file.absolutePathString())) {
                    pluginList.add(file.absolutePathString())
                }
                FileVisitResult.CONTINUE
            }
        }

        scanPaths.forEach {
            val path = Path(it)
            if(!path.exists()) return@forEach
            Files.walkFileTree(path, scanVisitor)
        }
        allScanCount = pluginList.size

        val scanSemaphore = Semaphore(10)

        scannedCount = 0

        coroutineScope {
            pluginList.map {
                async {
                    scanSemaphore.withPermit {
                        logger.info("Scanning native audio plugin: {}", it)
                        val process = Runtime.getRuntime().exec(arrayOf("./EIMHost.exe",  " -S ", Json.encodeToString(it)))
                        scanningPlugins[it] = process
                        var result = ""
                        try {
                            result = process.inputStream.readAllBytes().decodeToString()
                                .substringAfter("\$EIMHostScanner{{").substringBeforeLast("}}EIMHostScanner\$")
                            Json.decodeFromString<List<NativeAudioPluginDescription>>(result).forEach {
                                pluginDescriptions[it.fileOrIdentifier] = it
                            }
                        } catch (e: Throwable) {
                            logger.error("Failed to scan native audio plugin: $it, data: $result", e)
                            skipList.add(it)
                        } finally {
                            scannedCount++
                            scanningPlugins.remove(it)
                            process.destroy()
                            save()
                        }
                    }
                }
            }.awaitAll()
        }
        scanningPlugins.clear()
    }

    override fun createProcessor(identifier: String?, file: Path?): NativeAudioPlugin {
        return NativeAudioPluginImpl(pluginDescriptions[identifier]!!)
    }

     override fun save() {
        NATIVE_AUDIO_PLUGIN_CONFIG.toFile().writeText(Json.encodeToString(NativeAudioPluginFactoryData.serializer(),
            NativeAudioPluginFactoryData(pluginDescriptions, scanPaths, skipList)))
    }
}
