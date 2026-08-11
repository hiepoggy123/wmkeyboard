import React from "react";
import { Audio, Sequence, staticFile } from "remotion";
import { SFX_MIX } from "../sfxConfig";

// One-shot sound effect at a scene-relative frame. Files live in public/sfx.
// Final loudness = call-site volume x SFX_MIX[name].vol; SFX_MIX[name].on
// kills the effect everywhere.
export const Sfx: React.FC<{
  name: "whoosh" | "pop" | "swap" | "impact" | "riser";
  at?: number;
  volume?: number;
}> = ({ name, at = 0, volume = 0.5 }) => {
  const mix = SFX_MIX[name];
  if (!mix?.on) {
    return null;
  }
  return (
    <Sequence from={at}>
      <Audio src={staticFile(`sfx/${name}.wav`)} volume={volume * mix.vol} />
    </Sequence>
  );
};
