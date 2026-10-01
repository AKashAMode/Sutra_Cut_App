export default function VideoPlayerPreview({ src, poster }) {
  if (!src) {
    return (
      <div className="video-frame" style={{ display: "grid", placeItems: "center", color: "var(--muted)" }}>
        No media yet
      </div>
    );
  }
  return (
    <div className="video-frame">
      <video src={src} poster={poster} controls playsInline />
    </div>
  );
}
