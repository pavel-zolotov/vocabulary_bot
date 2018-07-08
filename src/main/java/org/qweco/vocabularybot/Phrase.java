package org.qweco.vocabularybot;

public class Phrase {
    private String id;
    private String source;
    private String translation;
    private String definition;

    public Phrase(String source, String translation) {
        this.source = source;
        this.translation = translation;
    }

    public Phrase(String id, String source, String translation, String definition) {
        this.id = id;
        this.source = source;
        this.translation = translation;
        this.definition = definition;
    }

    public Phrase(String source, String translation, String definition) {
        this.source = source;
        this.translation = translation;
        this.definition = definition;
    }
}
