package app.exteraless

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem

/**
 * Настройки фич, перенесённых из exteraGram.
 *
 * Повторяет схему [xyz.nextalone.nagram.NaConfig]: те же SharedPreferences, тот же [ConfigItem],
 * но собственный список — чтобы порт не конфликтовал с апстримом NagramX при мерджах.
 * Ключи с префиксом OE, чтобы не столкнуться с существующими.
 */
object OpenExteraConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = NekoConfig.getPreferences()

    // ---- Форматирование ----

    /** Относительное «был(а) в сети»: «5 минут назад» вместо времени. */
    @JvmField
    val relativeLastSeen = addConfig("OERelativeLastSeen", ConfigItem.configTypeBool, false)

    // ---- Чаты ----

    /** Запятая после упоминания: «@user, » вместо «@user ». */
    @JvmField
    val addCommaAfterMention = addConfig("OEAddCommaAfterMention", ConfigItem.configTypeBool, true)

    // ---- Статические геттеры для вызова из Java в горячих местах ----

    @JvmStatic
    fun relativeLastSeen(): Boolean = relativeLastSeen.Bool()

    @JvmStatic
    fun addCommaAfterMention(): Boolean = addCommaAfterMention.Bool()

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    @JvmStatic
    fun init() {
        loadConfig(false)
    }

    @JvmStatic
    fun loadConfig(force: Boolean) {
        synchronized(sync) {
            if (configLoaded && !force) return
            if (ApplicationLoader.applicationContext == null) return
            val preferences = getPreferences()
            for (item in configs) {
                try {
                    when (item.type) {
                        ConfigItem.configTypeBool ->
                            item.value = preferences.getBoolean(item.key, item.defaultValue as Boolean)

                        ConfigItem.configTypeInt ->
                            item.value = preferences.getInt(item.key, item.defaultValue as Int)

                        ConfigItem.configTypeLong ->
                            item.value = preferences.getLong(item.key, item.defaultValue as Long)

                        ConfigItem.configTypeFloat ->
                            item.value = preferences.getFloat(item.key, item.defaultValue as Float)

                        ConfigItem.configTypeString ->
                            item.value = preferences.getString(item.key, item.defaultValue as String?)
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
            configLoaded = true
        }
    }

    /** Сбрасывает все настройки openExtera к значениям по умолчанию. */
    @JvmStatic
    fun reset() {
        synchronized(sync) {
            val editor = getPreferences().edit()
            for (item in configs) {
                editor.remove(item.key)
                item.value = item.defaultValue
            }
            editor.apply()
        }
    }
}
