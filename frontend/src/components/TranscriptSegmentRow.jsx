function formatTime(value) {
  const seconds = Number(value || 0);
  const m = Math.floor(seconds / 60);
  const s = (seconds % 60).toFixed(2).padStart(5, "0");
  return `${m}:${s}`;
}

export default function TranscriptSegmentRow({ segment, onChange }) {
  return (
    <div className="segment">
      <div className="times">
        <span>{formatTime(segment.start)} – {formatTime(segment.end)}</span>
        <span>{segment.language || "mixed"}</span>
      </div>
      <textarea
        value={segment.text}
        onChange={(event) => onChange({ ...segment, text: event.target.value })}
      />
    </div>
  );
}
