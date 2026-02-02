package com.example.mlbbop.managers;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Log;

import com.example.mlbbop.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextWithImageHelper {
    private static final String TAG = "TextWithImageHelper";
    private static Map<String, Integer> resourceMap;
    private static Pattern pattern;

    private static void init() {
        if (resourceMap != null) return;
        resourceMap = new HashMap<>();
        List<String> keys = new ArrayList<>();

        Field[] drawables = R.drawable.class.getFields();
        for (Field field : drawables) {
            try {
                String name = field.getName();
                // Filter out likely UI icons
                if (name.startsWith("ic_") || name.startsWith("abc_") || name.startsWith("notification_")) {
                    continue;
                }

                int resId = field.getInt(null);
                
                String cleanName = name;
                if (cleanName.endsWith("_png")) {
                    cleanName = cleanName.substring(0, cleanName.length() - 4);
                }
                // Replace underscores with spaces
                cleanName = cleanName.replace("_", " ");
                
                String key = cleanName.toLowerCase();
                
                // Heuristic: Filter out very short names that might be false positives
                if (key.length() < 3) continue;

                resourceMap.put(key, resId);
                keys.add(key);
                
                // Extra mappings for special cases
                if (key.contains(" s ")) {
                   String altKey = key.replace(" s ", "'s ");
                   if (!keys.contains(altKey)) {
                       resourceMap.put(altKey, resId);
                       keys.add(altKey);
                   }
                }
                if (key.equals("chang e")) {
                    String altKey = "chang'e";
                    resourceMap.put(altKey, resId);
                    keys.add(altKey);
                }
                if (key.equals("x borg")) {
                    String altKey = "x.borg";
                    resourceMap.put(altKey, resId);
                    keys.add(altKey);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to access drawable field", e);
            }
        }

        // Sort keys by length descending to match longest phrases first
        Collections.sort(keys, (s1, s2) -> Integer.compare(s2.length(), s1.length()));
        
        // Build Regex
        StringBuilder sb = new StringBuilder();
        sb.append("(?i)\\b(");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(Pattern.quote(keys.get(i)));
        }
        sb.append(")\\b");
        
        if (!keys.isEmpty()) {
            pattern = Pattern.compile(sb.toString());
        }
    }

    public static CharSequence getSpannedText(Context context, String text) {
        if (text == null || text.isEmpty()) return "";
        init();
        if (pattern == null || resourceMap.isEmpty()) return text;

        Matcher matcher = pattern.matcher(text);
        SpannableStringBuilder ssb = new SpannableStringBuilder();

        int lastEnd = 0;
        boolean foundMatch = false;

        while (matcher.find()) {
            foundMatch = true;
            // Append text before match
            ssb.append(text.substring(lastEnd, matcher.start()));
            
            String match = matcher.group(); // The actual text found
            String key = match.toLowerCase(); // Key for map lookup
            
            // Clean up key for lookup (in case regex matched something slightly specific, though we build regex from keys)
            // But our keys are "clean name" lowercased.
            // Match should match one of the keys (case insensitive). so we can just use match.toLowerCase() 
            // BUT wait. If we have keys "blade of despair" and "blade", and text is "Blade of Despair", 
            // regex `(blade of despair|blade)` will match "Blade of Despair".
            // match.toLowerCase() -> "blade of despair".
            // if we have underscores in map resourceMap? 
            // In init(), we replaced `_` with ` ` in `cleanName`. so keys have spaces.
            // However, input text might not have underscores, it will have spaces.
            // What if input text has underscores? the regex `\b` might treat underscores as word characters?
            // Actually `\w` includes `_`. 
            // If the user text is "blade_of_despair", and our key is "blade of despair", regex won't match "blade_of_despair" against "blade of despair".
            // The text from Gemini is likely "Blade of Despair".
            // So we assume the text matches the format we prepared (spaces).
            // BUT, our drawables were snake_case. `blade_of_despair`. 
            // We converted them to "blade of despair" key.
            // So we match "Blade of Despair" in text.
            
            Integer resId = resourceMap.get(key);
            if (resId != null) {
                try {
                    Drawable d = context.getDrawable(resId);
                    if (d != null) {
                        d.setBounds(0, 0, 50, 50);
                        ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_CENTER);
                        
                        int startSpan = ssb.length();
                        ssb.append(" ");
                        ssb.setSpan(span, startSpan, startSpan + 1, 0);
                        ssb.append(" "); 
                    }
                } catch (Exception e) {
                   Log.e(TAG, "Error loading drawable", e);
                }
            }

            // Append the matched text itself
            ssb.append(match);
            
            lastEnd = matcher.end();
        }
        
        // Append remaining text
        ssb.append(text.substring(lastEnd));

        return foundMatch ? ssb : text;
    }
}
