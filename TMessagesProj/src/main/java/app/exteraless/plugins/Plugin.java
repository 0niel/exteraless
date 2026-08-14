package app.exteraless.plugins;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель плагина: метаданные, прочитанные из .py-файла (AST-парсинг на Python-стороне,
 * сюда приезжают уже готовыми полями), состояние и путь к файлу.
 *
 * Аналог com.exteragram.messenger.plugins.Plugin.
 */
public class Plugin {

    public String id;
    public String name;
    public String description;
    public String author;
    public String version = "1.0";
    /** StickerPackShortName/index, например "exteraPlugins/1". */
    public String icon;
    public String appVersion;
    public String sdkVersion;
    public boolean beta;
    public List<String> requirements = new ArrayList<>();

    /** Абсолютный путь к файлу плагина в filesDir/plugins. */
    public String path;

    /** Включён ли пользователем (персистентно). */
    public boolean enabled = true;
    /** Загружен ли сейчас в Python-рантайм. */
    public transient boolean loaded;
    /** Есть ли у плагина create_settings (показывать ли экран настроек). */
    public transient boolean hasSettings;
    /** Текст последней ошибки загрузки/валидации; null если всё хорошо. */
    public String loadError;

    public String getDisplayName() {
        return name != null ? name : id;
    }

    public String getSubtitle() {
        StringBuilder sb = new StringBuilder();
        sb.append("v").append(version != null ? version : "1.0");
        if (author != null && !author.isEmpty()) {
            sb.append(" • ").append(author);
        }
        if (loadError != null) {
            sb.append(" • error");
        }
        return sb.toString();
    }
}
