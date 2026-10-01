package com.videoapp.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class AssetFallbackService {

    public String resolveVisualType(String requestedType, String assetUrl) {
        if (assetUrl != null && !assetUrl.isBlank()) {
            return requestedType == null || requestedType.isBlank() ? "broll" : requestedType;
        }
        return "caption";
    }

    public String captionOnlyKeyword(String text) {
        if (text == null || text.isBlank()) {
            return "caption";
        }
        String[] tokens = text.toLowerCase(Locale.ROOT).split("\\s+");
        for (String token : tokens) {
            String cleaned = token.replaceAll("[^\\p{L}\\p{N}]+", "");
            if (cleaned.length() >= 4) {
                return cleaned;
            }
        }
        return "caption";
    }
}
