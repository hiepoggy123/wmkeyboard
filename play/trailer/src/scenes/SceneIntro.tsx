import React from "react";
import {
  AbsoluteFill,
  Img,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";
import { Backdrop } from "../components/Backdrop";
import { GradientHeadline } from "../components/GradientHeadline";
import { INTER } from "../fonts";
import { BODY } from "../theme";

export const SceneIntro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const logo = spring({ frame, fps, config: { damping: 200, stiffness: 120 } });
  const tag = spring({
    frame: frame - 32,
    fps,
    config: { damping: 200, stiffness: 140 },
  });
  return (
    <Backdrop>
      <AbsoluteFill
        style={{
          alignItems: "center",
          justifyContent: "center",
          flexDirection: "column",
          gap: 40,
        }}
      >
        <Img
          src={staticFile("logo-mark.png")}
          style={{
            width: 190,
            height: 190,
            opacity: logo,
            transform: `scale(${interpolate(logo, [0, 1], [0.6, 1])})`,
            filter: "drop-shadow(0 0 50px rgba(124,92,255,0.45))",
          }}
        />
        <GradientHeadline text="WM «Keyboard»" size={128} delay={10} align="center" />
        <div
          style={{
            fontFamily: INTER,
            fontWeight: 500,
            fontSize: 46,
            color: BODY,
            opacity: tag,
            transform: `translateY(${interpolate(tag, [0, 1], [24, 0])}px)`,
            letterSpacing: "0.06em",
          }}
        >
          Private. Offline. Yours.
        </div>
      </AbsoluteFill>
    </Backdrop>
  );
};
