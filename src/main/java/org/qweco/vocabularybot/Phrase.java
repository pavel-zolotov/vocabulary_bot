package org.qweco.vocabularybot;

public class Phrase {
    public String id;
    public String source;
    public String translation;
    public String definition;

    public Phrase(){
        super();
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
