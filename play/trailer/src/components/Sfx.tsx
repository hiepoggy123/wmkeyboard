import React from "react";
import { Audio, Sequence, staticFile } from "remotion";

// One-shot sound effect at a scene-relative frame. Files live in public/sfx.
export const Sfx: React.FC<{
  name: "whoosh" | "pop" | "swap" | "impact" | "riser";
  at?: number;
  volume?: number;
}> = ({ name, at = 0, volume = 0.5 }) => {
  return (
    <Sequence from={at}>
      <Audio src={staticFile(`sfx/${name}.wav`)} volume={volume} />
    </Sequence>
  );
};
