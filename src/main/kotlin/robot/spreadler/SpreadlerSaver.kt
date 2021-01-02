package robot.spreadler

import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

class SpreadlerConfig(var spreadlers: MutableList<SpreadlerBond>)

object SpreadlerConfigurator {
    val path = Paths.get("config/spreadler.txt")
    var config: SpreadlerConfig

    init {
        val serializedConfig = Files.readString(path)

        config = GsonBuilder().setDateFormat("dd.MM.yyyy HH:mm:ss").create()
                .fromJson(serializedConfig, SpreadlerConfig::class.java)
                ?: SpreadlerConfig(ArrayList())
    }

    @Synchronized
    fun save() {
        val builder = GsonBuilder()
        builder.setPrettyPrinting()
        builder.setDateFormat("dd.MM.yyyy HH:mm:ss")
        val gson = builder.create()

        val serializedConfig = gson.toJson(config);
        if (Files.exists(path)) {
            val currentTimeMillis = System.currentTimeMillis()
            Files.move(path, Paths.get("config/archive/spreadler-$currentTimeMillis.txt"), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.writeString(path, serializedConfig, StandardOpenOption.CREATE_NEW)
    }

    @Synchronized
    fun add(spreadler: SpreadlerBond) {
        config.spreadlers.forEach {
            if (it.securityCode == spreadler.securityCode) {
                throw Exception("Уже есть такой спредлер")
            }
        }
        config.spreadlers.add(spreadler)
    }

    @Synchronized
    fun remove(id: String) {
        config.spreadlers.removeIf { it.id == id }
    }

    @Synchronized
    fun add(serializedSpreadler: String) {
        val spreadler = GsonBuilder().setDateFormat("dd.MM.yyyy HH:mm:ss").create()
                .fromJson(serializedSpreadler, SpreadlerBond::class.java)
        add(spreadler)
    }

}

