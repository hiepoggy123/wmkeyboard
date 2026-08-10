import React from "react";
import {
  Img,
  OffthreadVideo,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { AVAILABLE_CLIPS } from "../clips";
import { INTER } from "../fonts";
import { BODY, CLIP_H, CLIP_W, INK_HI, STATUS_BAR } from "../theme";

// Device mockup for 1080×2400 recordings/stills. The 140px status bar is
// cropped out, so ColorOS clock/battery junk never reaches the frame.
export const PhoneFrame: React.FC<{
  clip?: string; // name in public/clips without .mp4 — placeholder if absent
  still?: string; // file in public/stills — used when no clip
  width?: number;
  delay?: number;
  tilt?: number;
  startFrom?: number;
  playbackRate?: number;
  label?: string;
}> = ({
  clip,
  still,
  width = 380,
  delay = 0,
  tilt = 0,
  startFrom = 0,
  playbackRate = 1,
  label,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({
    frame: frame - delay,
    fps,
    config: { damping: 200, stiffness: 90 },
  });

  const scale = width / CLIP_W;
  const screenH = (CLIP_H - STATUS_BAR) * scale;
  const bezel = width * 0.03;
  const hasClip = clip !== undefined && AVAILABLE_CLIPS.includes(`${clip}.mp4`);
  const media = hasClip ? (
    <OffthreadVideo
      src={staticFile(`clips/${clip}.mp4`)}
      startFrom={startFrom}
      playbackRate={playbackRate}
      muted
      style={{
        width,
        height: CLIP_H * scale,
        marginTop: -STATUS_BAR * scale,
        display: "block",
      }}
    />
  ) : still ? (
    <Img
      src={staticFile(`stills/${still}`)}
      style={{
        width,
        height: CLIP_H * scale,
        marginTop: -STATUS_BAR * scale,
        display: "block",
      }}
    />
  ) : (
    <div
      style={{
        width,
        height: screenH,
        background: `linear-gradient(160deg, ${INK_HI}, #1A2030)`,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontFamily: INTER,
        fontSize: width * 0.06,
        color: BODY,
      }}
    >
      {clip ? `[${clip}]` : ""}
    </div>
  );

  return (
    <div
      style={{
        opacity: s,
        transform: `translateY(${interpolate(s, [0, 1], [90, 0])}px) rotate(${tilt}deg)`,
      }}
    >
      <div
        style={{
          padding: bezel,
          borderRadius: width * 0.135,
          backgroundColor: "#05070C",
          border: "2px solid rgba(255,255,255,0.14)",
          boxShadow:
            "0 40px 90px rgba(0,0,0,0.6), 0 0 60px rgba(124,92,255,0.12)",
        }}
      >
        <div
          style={{
            borderRadius: width * 0.105,
            overflow: "hidden",
            width,
            height: screenH,
          }}
        >
          {media}
        </div>
      </div>
      {label ? (
        <div
          style={{
            marginTop: 24,
            textAlign: "center",
            fontFamily: INTER,
            fontWeight: 500,
            fontSize: 30,
            color: BODY,
          }}
        >
          {label}
        </div>
      ) : null}
    </div>
  );
};
