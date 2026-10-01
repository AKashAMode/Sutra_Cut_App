import { useState } from "react";

export default function SegmentEditModal({ segment, onClose, onSave }) {
  const [draft, setDraft] = useState(segment);

  if (!segment) {
    return null;
  }

  return (
    <div className="modal-back" onClick={onClose}>
      <div className="modal" onClick={(event) => event.stopPropagation()}>
        <div className="kicker">Segment</div>
        <h3 style={{ marginTop: 0 }}>{draft.keyword || "caption"}</h3>
        <label className="meta">Caption</label>
        <textarea
          value={draft.captionText || ""}
          onChange={(event) => setDraft({ ...draft, captionText: event.target.value })}
          style={{ margin: "8px 0 12px" }}
        />
        <label className="meta">Visual type</label>
        <div className="row" style={{ margin: "8px 0 12px" }}>
          {["broll", "icon", "caption"].map((type) => (
            <button
              key={type}
              type="button"
              className={draft.visualType === type ? "primary" : "ghost"}
              onClick={() => setDraft({ ...draft, visualType: type })}
            >
              {type}
            </button>
          ))}
        </div>
        <label className="meta">Keyword</label>
        <input
          type="text"
          value={draft.keyword || ""}
          onChange={(event) => setDraft({ ...draft, keyword: event.target.value })}
          style={{ margin: "8px 0 12px" }}
        />
        <label className="meta">Asset URL</label>
        <input
          type="url"
          placeholder="https://..."
          value={draft.assetUrl || ""}
          onChange={(event) => setDraft({ ...draft, assetUrl: event.target.value, assetSource: "manual" })}
          style={{ margin: "8px 0 16px" }}
        />
        <div className="row">
          <button type="button" className="primary" onClick={() => onSave(draft)}>Save segment</button>
          <button type="button" className="ghost" onClick={onClose}>Cancel</button>
        </div>
      </div>
    </div>
  );
}
