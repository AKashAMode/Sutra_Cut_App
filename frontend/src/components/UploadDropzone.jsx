import { useRef, useState } from "react";

export default function UploadDropzone({ onFile, disabled }) {
  const inputRef = useRef(null);
  const [hot, setHot] = useState(false);

  function takeFiles(files) {
    const file = files?.[0];
    if (file) {
      onFile(file);
    }
  }

  return (
    <div
      className={`dropzone ${hot ? "hot" : ""}`}
      onClick={() => !disabled && inputRef.current?.click()}
      onDragOver={(event) => {
        event.preventDefault();
        setHot(true);
      }}
      onDragLeave={() => setHot(false)}
      onDrop={(event) => {
        event.preventDefault();
        setHot(false);
        takeFiles(event.dataTransfer.files);
      }}
    >
      <div>
        <div className="kicker">Drop media</div>
        <h3 style={{ margin: "0 0 8px", fontFamily: "var(--serif)" }}>Video or audio, Hindi, English, or mixed</h3>
        <p className="meta">MP4, MOV, MKV, WEBM, MP3, WAV, M4A. Max 500MB. Transcription starts immediately after upload.</p>
      </div>
      <input
        ref={inputRef}
        type="file"
        hidden
        accept="video/*,audio/*,.mp4,.mov,.mkv,.webm,.mp3,.wav,.m4a,.aac,.ogg"
        onChange={(event) => takeFiles(event.target.files)}
        disabled={disabled}
      />
    </div>
  );
}
