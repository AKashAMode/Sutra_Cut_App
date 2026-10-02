export default function TimelineTrack({ segments, activeId, onSelect }) {
  return (
    <div className="track">
      {segments.map((segment) => (
        <button
          type="button"
          key={segment.id}
          className={`clip ${segment.dirty ? "dirty" : ""} ${segment.id === activeId ? "active" : ""}`}
          onClick={() => onSelect(segment)}
        >
          {segment.visualType === "icon" && segment.assetUrl ? (
            <img
              className="clip-icon"
              src={segment.assetUrl.replace(/\/([^/?]+):([^/?]+)\.svg(?=\?|$)/, "/$1/$2.svg")}
              alt={`${segment.keyword || "Matched"} icon`}
              loading="lazy"
              onError={(event) => {
                event.currentTarget.hidden = true;
              }}
            />
          ) : null}
          <div className="meta">{segment.visualType} · {segment.keyword || "caption"}</div>
          <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.4 }}>
            {(segment.captionText || "").slice(0, 72) || "Empty caption"}
          </div>
        </button>
      ))}
    </div>
  );
}
