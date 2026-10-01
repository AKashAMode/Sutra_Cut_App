from __future__ import annotations

import re
from collections import Counter

STOPWORDS = {
    "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with",
    "is", "are", "was", "were", "be", "been", "this", "that", "it", "we", "you", "i",
    "he", "she", "they", "them", "our", "your", "my", "me", "us", "so", "if", "as",
    "from", "by", "about", "into", "over", "after", "before", "then", "than", "not",
    "do", "does", "did", "have", "has", "had", "will", "would", "can", "could",
    "today", "talk", "talking", "going", "just", "like", "also", "very", "more",
    "हम", "है", "हैं", "का", "की", "के", "को", "में", "से", "और", "यह", "वह", "आज",
    "हम", "बात", "करेंगे", "करते", "करना", "एक", "तो", "ही", "भी", "नहीं", "लिए",
}

ICON_HINTS = {
    "idea", "light", "star", "heart", "music", "alert", "warning", "check", "phone",
    "email", "chat", "user", "settings", "search", "download", "play", "pause",
}


def extract_keyword(text: str) -> tuple[str, str]:
    if not text:
        return "discussion", "caption"
    tokens = re.findall(r"[\w\u0900-\u097F]+", text.lower())
    filtered = [t for t in tokens if t not in STOPWORDS and len(t) >= 3]
    if not filtered:
        return "discussion", "caption"
    counts = Counter(filtered)
    keyword = counts.most_common(1)[0][0]
    visual_type = "icon" if keyword in ICON_HINTS else "broll"
    return keyword, visual_type
