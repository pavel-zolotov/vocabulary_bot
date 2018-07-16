package org.qweco.vocabularybot;

import org.qweco.vocabularybot.db.DatabaseManager;
import org.qweco.vocabularybot.services.Translator;

public class BotUtils {
    final public static String BOT_NAME = "Vocabulary Bot";

    public static String countryCode2EmojiFlag (String country){
        int flagOffset = 0x1F1E6;
        int asciiOffset = 0x41;

        int firstChar = Character.codePointAt(country, 0) - asciiOffset + flagOffset;
        int secondChar = Character.codePointAt(country, 1) - asciiOffset + flagOffset;

        return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
    }

    public static Phrase makePhrase (String originalText, String translation, String fullLang) {
        String inputLang = fullLang.split("-")[0]; //get the input lang

        Phrase phrase = null;
        if (!originalText.trim().contains(" ")) { //is it one word?
            String definition = Translator.getDefinitions(inputLang, originalText.trim());
            if (!definition.equals("")) {
                phrase = new Phrase(originalText,
                        translation,
                        definition,
                        fullLang, 0, 0, true);
            }
        }

        if (phrase == null) {
            phrase = new Phrase(originalText,
                    translation,
                    fullLang, 0, 0, true);
        }

        return phrase;
    }
}
