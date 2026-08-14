package app.exteraless.chats

import android.content.SharedPreferences
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.config.ConfigItem

/**
 * Настройки экрана «Chats», перенесённые из exteraGram.
 *
 * Здесь только то, чего в NagramX нет: остальное экран берёт из [tw.nekomimi.nekogram.NekoConfig]
 * и [xyz.nextalone.nagram.NaConfig], чтобы настройка не задваивалась.
 *
 * Схема повторяет [app.exteraless.OpenExteraConfig]: те же SharedPreferences, тот же [ConfigItem],
 * ключи с префиксом OEChats. Конфиг подгружается лениво (см. [ensureLoaded]), потому что
 * ApplicationLoader не трогаем.
 */
object ChatsConfig {

    private val sync = Any()
    private val configs = ArrayList<ConfigItem>()

    @Volatile
    private var configLoaded = false

    @JvmStatic
    fun getPreferences(): SharedPreferences = NekoConfig.getPreferences()

    // ---- Стикеры ----

    /** Форма стикеров: 0 — как есть, 1 — скруглённая, 2 — как сообщение. */
    @JvmField
    val stickerShape = addConfig("OEChatsStickerShape", ConfigItem.configTypeInt, 1)

    // ---- Ответы ----

    /** Цветной фон блока ответа. */
    @JvmField
    val replyColors = addConfig("OEChatsReplyColors", ConfigItem.configTypeBool, true)

    /** Эмодзи-иконка в блоке ответа. */
    @JvmField
    val replyEmoji = addConfig("OEChatsReplyEmoji", ConfigItem.configTypeBool, true)

    /** Подложка под блоком ответа. */
    @JvmField
    val replyBackground = addConfig("OEChatsReplyBackground", ConfigItem.configTypeBool, true)

    // ---- Камера ----

    /** Тип камеры: 0 — системная, 1 — Camera2, 2 — CameraX. */
    @JvmField
    val cameraType = addConfig("OEChatsCameraType", ConfigItem.configTypeInt, 0)

    /** Стабилизация видео. */
    @JvmField
    val cameraStabilization = addConfig("OEChatsCameraStabilization", ConfigItem.configTypeBool, false)

    /** Зеркалирование фронтальной камеры. */
    @JvmField
    val cameraMirrorMode = addConfig("OEChatsCameraMirrorMode", ConfigItem.configTypeBool, false)

    /** Расширенный диапазон FPS. */
    @JvmField
    val extendedFramesPerSecond = addConfig("OEChatsExtendedFps", ConfigItem.configTypeBool, false)

    /** Стартовать с широкоугольного объектива. */
    @JvmField
    val startWithWideAngleCamera = addConfig("OEChatsWideAngleCamera", ConfigItem.configTypeBool, false)

    /** Статичный зум: уровень не сбрасывается после отпускания пальца. */
    @JvmField
    val staticZoom = addConfig("OEChatsStaticZoom", ConfigItem.configTypeBool, false)

    // ---- Фото ----

    /**
     * Тёплость вспышки при записи кружка, проценты (50 = 0.5, как FloatPref(0.5f) exteraGram).
     * ConfigItem не умеет float, поэтому целое.
     */
    @JvmField
    val flashWarmth = addConfig("OEChatsFlashWarmth", ConfigItem.configTypeInt, 50)

    /** Яркость вспышки при записи кружка, проценты (100 = 1.0, FloatPref(1.0f) exteraGram). */
    @JvmField
    val flashIntensity = addConfig("OEChatsFlashIntensity", ConfigItem.configTypeInt, 100)

    @JvmStatic
    fun flashWarmth(): Float = flashWarmth.Int() / 100f

    @JvmStatic
    fun flashIntensity(): Float = flashIntensity.Int() / 100f

    /** Скрыть плитку камеры в шторке вложений. */
    @JvmField
    val hideCameraTile = addConfig("OEChatsHideCameraTile", ConfigItem.configTypeBool, false)

    /**
     * Отправлять фото в высоком качестве по умолчанию. Дефолт true, как в exteraGram.
     * При включённом бейдж на превью инвертируется: помечается не «HD», а «SD» —
     * то есть те кадры, которые уйдут в обычном качестве.
     */
    @JvmField
    val alwaysSendInHD = addConfig("OEChatsAlwaysSendInHD", ConfigItem.configTypeBool, true)

    // ---- Стикеры и эмодзи ----

    /** Скрывать реакции — каналы / группы / личные (группа-мультивыбор, только UI). */
    @JvmField
    val hideReactionsInChannels = addConfig("OEChatsHideReactionsChannels", ConfigItem.configTypeBool, false)
    @JvmField
    val hideReactionsInGroups = addConfig("OEChatsHideReactionsGroups", ConfigItem.configTypeBool, false)
    @JvmField
    val hideReactionsInPrivate = addConfig("OEChatsHideReactionsPrivate", ConfigItem.configTypeBool, false)

    // ---- Чаты ----

    /** Быстрый свайп-переход (родитель + каналы / темы, группа-мультивыбор, только UI). */
    @JvmField
    val quickTransitionForChannels = addConfig("OEChatsQuickTransitionChannels", ConfigItem.configTypeBool, false)
    @JvmField
    val quickTransitionForTopics = addConfig("OEChatsQuickTransitionTopics", ConfigItem.configTypeBool, false)

    // ---- Сообщения ----

    /** Убрать «хвостик» пузыря (только UI). */
    @JvmField
    val removeMessageTail = addConfig("OEChatsRemoveMessageTail", ConfigItem.configTypeBool, true)

    /** Заменять «edited» иконкой (только UI). */
    @JvmField
    val replaceEditedWithIcon = addConfig("OEChatsReplaceEditedWithIcon", ConfigItem.configTypeBool, true)

    /** Показывать результаты опроса до голосования (только UI). */
    @JvmField
    val showResultsBeforeVoting = addConfig("OEChatsShowResultsBeforeVoting", ConfigItem.configTypeBool, false)

    // ---- Камера (расширенные) ----

    /** Бесшовное переключение камер (только UI). */
    @JvmField
    val cameraSeamlessSwitching = addConfig("OEChatsCameraSeamless", ConfigItem.configTypeBool, false)

    /** Запоминать последнюю использованную камеру (только UI). */
    @JvmField
    val rememberLastUsedCamera = addConfig("OEChatsRememberLastUsedCamera", ConfigItem.configTypeBool, false)

    // ---- Воспроизведение ----

    /** Предпочитать оригинальное качество (только UI). */
    @JvmField
    val preferOriginalQuality = addConfig("OEChatsPreferOriginalQuality", ConfigItem.configTypeBool, false)

    /**
     * Автопауза — голос / кружки (группа-мультивыбор, только UI).
     * Видео берётся из рабочего [NekoConfig.autoPauseVideo].
     */
    @JvmField
    val pauseOnMinimizeVoice = addConfig("OEChatsPauseVoice", ConfigItem.configTypeBool, false)
    @JvmField
    val pauseOnMinimizeRound = addConfig("OEChatsPauseRound", ConfigItem.configTypeBool, false)

    // ---- Воспроизведение ----

    /** Индекс в [SEEK_DURATIONS]: время перемотки двойным тапом. */
    @JvmField
    val doubleTapSeekDuration = addConfig("OEChatsDoubleTapSeekDuration", ConfigItem.configTypeInt, 1)

    /** Пауза при сворачивании приложения или блокировке экрана. */
    @JvmField
    val pauseOnMinimize = addConfig("OEChatsPauseOnMinimize", ConfigItem.configTypeBool, false)

    /** Свайп вниз по видео — в режим «картинка в картинке». */
    @JvmField
    val swipeToPip = addConfig("OEChatsSwipeToPip", ConfigItem.configTypeBool, false)

    /** Кнопки громкости снимают звук с видео вместо изменения громкости. */
    @JvmField
    val unmuteWithVolumeButtons = addConfig("OEChatsUnmuteWithVolumeButtons", ConfigItem.configTypeBool, false)

    /** Варианты времени перемотки двойным тапом, секунды. */
    @JvmField
    val SEEK_DURATIONS = intArrayOf(5, 10, 15, 30)

    // ---- Статические геттеры для горячих мест в Java ----

    /** Кнопки громкости снимают немоту с видео вместо изменения громкости. */
    @JvmStatic
    fun unmuteWithVolumeButtons(): Boolean {
        ensureLoaded()
        return unmuteWithVolumeButtons.Bool()
    }

    /** Ставить голосовое на паузу при сворачивании приложения. */
    @JvmStatic
    fun pauseOnMinimizeVoice(): Boolean {
        ensureLoaded()
        return pauseOnMinimizeVoice.Bool()
    }

    /** Ставить кружок на паузу при сворачивании приложения. */
    @JvmStatic
    fun pauseOnMinimizeRound(): Boolean {
        ensureLoaded()
        return pauseOnMinimizeRound.Bool()
    }

    @JvmStatic
    fun stickerShape(): Int {
        ensureLoaded()
        return stickerShape.Int()
    }

    @JvmStatic
    fun seekDurationSeconds(): Int {
        ensureLoaded()
        val index = doubleTapSeekDuration.Int()
        return SEEK_DURATIONS[if (index in SEEK_DURATIONS.indices) index else 1]
    }

    private fun addConfig(key: String, type: Int, defaultValue: Any?): ConfigItem {
        val item = ConfigItem(key, type, defaultValue)
        configs.add(item)
        return item
    }

    /** Ленивая инициализация: до готовности ApplicationLoader остаются значения по умолчанию. */
    @JvmStatic
    fun ensureLoaded() {
        if (configLoaded) return
        loadConfig(false)
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

    /** Сбрасывает настройки экрана «Chats» к значениям по умолчанию. */
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
