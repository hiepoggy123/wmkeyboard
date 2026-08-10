import React from "react";
import { Series } from "remotion";
import { PhoneFrame } from "../components/PhoneFrame";
import { Sfx } from "../components/Sfx";
import { CYAN, VIOLET } from "../theme";
import { SceneSplit } from "./SceneSplit";

export const SceneModes: React.FC = () => {
  return (
    <SceneSplit
      headline="Fits every «app,» every hand"
      sub="Resize it, float it, split it — and watch it morph for email, number and URL fields."
      chips={[
        { label: "One-handed · split · floating", accent: VIOLET },
        { label: "Per-app keyboard modes", accent: VIOLET },
        { label: "Foldable-aware", accent: CYAN },
      ]}
      accentA={VIOLET}
      accentB={CYAN}
    >
      <Series>
        <Series.Sequence durationInFrames={60} layout="none">
          <PhoneFrame
            clip="modes"
            still="kb-english-dark.png"
            width={400}
            label="One-handed · split · floating"
          />
        </Series.Sequence>
        <Series.Sequence durationInFrames={60} layout="none">
          <Sfx name="swap" volume={0.45} />
          <PhoneFrame
            clip="fieldkinds"
            still="kb-english-dark.png"
            width={400}
            label="Adapts to the field"
          />
        </Series.Sequence>
      </Series>
    </SceneSplit>
  );
};
