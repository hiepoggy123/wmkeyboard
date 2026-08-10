import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { MANROPE } from "../fonts";
import { CYAN, VIOLET, WHITE } from "../theme";

// Kinetic headline: each word springs up+in with a stagger. Words wrapped in
// «guillemets» get the VIOLET→CYAN gradient treatment from the Play art.
export const GradientHeadline: React.FC<{
  text: string;
  size?: number;
  delay?: number;
  align?: "left" | "center";
}> = ({ text, size = 96, delay = 0, align = "left" }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const words = text.split(" ");

  return (
    <h1
      style={{
        fontFamily: MANROPE,
        fontWeight: 800,
        fontSize: size,
        lineHeight: 1.08,
        margin: 0,
        color: WHITE,
        letterSpacing: "-0.02em",
        textAlign: align,
      }}
    >
      {words.map((word, i) => {
        const accent = word.startsWith("«");
        const clean = word.replace(/[«»]/g, "");
        const s = spring({
          frame: frame - delay - i * 3,
          fps,
          config: { damping: 200, stiffness: 140 },
        });
        const y = interpolate(s, [0, 1], [40, 0]);
        return (
          <span
            key={i}
            style={{
              display: "inline-block",
              opacity: s,
              transform: `translateY(${y}px)`,
              marginRight: "0.26em",
              ...(accent
                ? {
                    background: `linear-gradient(90deg, ${VIOLET}, ${CYAN})`,
                    WebkitBackgroundClip: "text",
                    backgroundClip: "text",
                    color: "transparent",
                    filter: `drop-shadow(0 0 ${size / 6}px ${VIOLET}66)`,
                  }
                : {}),
            }}
          >
            {clean}
          </span>
        );
      })}
    </h1>
  );
};
