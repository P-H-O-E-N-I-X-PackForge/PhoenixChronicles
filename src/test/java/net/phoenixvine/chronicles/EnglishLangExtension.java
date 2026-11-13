package net.phoenixvine.chronicles;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class EnglishLangExtension implements BeforeAllCallback {

    private static final Map<String, String> STRINGS = load();

    private static Map<String, String> load() {
        Map<String, String> strings = new HashMap<>();
        try (Reader reader = new InputStreamReader(
                EnglishLangExtension.class.getResourceAsStream("/assets/phoenix_chronicles/lang/en_us.json"),
                StandardCharsets.UTF_8)) {
            JsonObject json = new Gson().fromJson(reader, JsonObject.class);
            json.entrySet().forEach(e -> strings.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load en_us.json for tests", e);
        }
        return strings;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        Language.inject(new Language() {

            @Override
            public String getOrDefault(String key, String fallback) {
                return STRINGS.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return STRINGS.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.EMPTY;
            }
        });
    }
}
