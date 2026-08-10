import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { INTER } from "../fonts";
import { BODY, CHIP_TEXT, INK_HI } from "../theme";

// Feature chip matching wmkit.chip(): lifted pill, accent dot, Inter medium.
export const Chip: React.FC<{
  label: string;
  accent?: string | null;
  delay?: number;
  size?: number;
}> = ({ label, accent, delay = 0, size = 34 }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({
    frame: frame - delay,
    fps,
    config: { damping: 200, stiffness: 160 },
  });
  return (
    <div
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: size * 0.45,
        backgroundColor: INK_HI,
        border: "1.5px solid rgba(255,255,255,0.10)",
        borderRadius: 999,
        padding: `${size * 0.5}px ${size * 0.85}px`,
        opacity: s,
        transform: `translateY(${interpolate(s, [0, 1], [26, 0])}px) scale(${interpolate(s, [0, 1], [0.92, 1])})`,
      }}
    >
      {accent ? (
        <div
          style={{
            width: size * 0.38,
            height: size * 0.38,
            borderRadius: "50%",
            backgroundColor: accent,
            boxShadow: `0 0 ${size * 0.5}px ${accent}AA`,
          }}
        />
      ) : null}
      <span
        style={{
          fontFamily: INTER,
          fontWeight: 500,
          fontSize: size,
          color: accent ? CHIP_TEXT : BODY,
          whiteSpace: "nowrap",
        }}
      >
        {label}
      </span>
    </div>
  );
};
