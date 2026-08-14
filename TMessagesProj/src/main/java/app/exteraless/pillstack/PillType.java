package app.exteraless.pillstack;

/**
 * Типы пилюль. Идентификаторы совпадают с оригиналом из exteraGram,
 * чтобы раскладка читалась одинаково.
 */
public enum PillType {
    WEATHER(1),
    GRAM(2),
    BTC(3),
    USD(4),
    CACHE(5),
    PROXY(6);

    public final int id;

    PillType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
