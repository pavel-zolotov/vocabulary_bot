package org.qweco.vocabularybot;

public class Phrase {
    public String id;
    public String source;
    public String translation;
    public String definition;
    public String lang;
    public int repeats;
    public int correctAnswers;
    public boolean enabled;

    public Phrase(String source, String translation, String lang, int repeats, int correctAnswers, boolean enabled) {
        this.source = source;
        this.translation = translation;
        this.lang = lang;
        this.repeats = repeats;
        this.correctAnswers = correctAnswers;
        this.enabled = enabled;
    }

    public Phrase(String id, String source, String translation, String definition, String lang, int repeats, int correctAnswers, boolean enabled) {
        this.id = id;
        this.source = source;
        this.translation = translation;
        this.definition = definition;
        this.lang = lang;
        this.repeats = repeats;
        this.correctAnswers = correctAnswers;
        this.enabled = enabled;
    }

    public Phrase(String source, String translation, String definition, String lang, int repeats, int correctAnswers, boolean enabled) {
        this.source = source;
        this.translation = translation;
        this.definition = definition;
        this.lang = lang;
        this.repeats = repeats;
        this.correctAnswers = correctAnswers;
        this.enabled = enabled;
    }
}
