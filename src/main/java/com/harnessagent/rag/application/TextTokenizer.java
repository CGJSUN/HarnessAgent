package com.harnessagent.rag.application;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TextTokenizer {

    public Set<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        StringBuilder word = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint) && !isCjk(codePoint)) {
                word.appendCodePoint(codePoint);
                return;
            }
            flushWord(tokens, word);
            if (isCjk(codePoint)) {
                tokens.add(new String(Character.toChars(codePoint)));
            }
        });
        flushWord(tokens, word);
        return tokens;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static void flushWord(Set<String> tokens, StringBuilder word) {
        if (!word.isEmpty()) {
            tokens.add(word.toString());
            word.setLength(0);
        }
    }
}
