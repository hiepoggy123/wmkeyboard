import React from "react";
import { Series } from "remotion";
import { PhoneFrame } from "../components/PhoneFrame";
import { AMBER, CYAN, LIME } from "../theme";
import { SceneSplit } from "./SceneSplit";

// Rapid cuts through toolbar panels; headline persists, phone + chip swap.
const CUTS: {
  clip: string;
  still?: string;
  label: string;
  accent: string;
  frames: number;
  rate?: number;
}[] = [
  { clip: "toolbox", label: "40+ built-in tools", accent: AMBER, frames: 65 },
  { clip: "dictionary", label: "Dictionary & Wikipedia", accent: CYAN, frames: 60 },
  { clip: "calc", label: "Calculator in the strip", accent: AMBER, frames: 60 },
  { clip: "voice", label: "On-device voice typing", accent: LIME, frames: 60, rate: 0.75 },
];

export const SceneTools: React.FC = () => {
  return (
    <SceneSplit
      headline="A «real» toolbox"
      sub="Calculator, translator, scanner, clipboard history, AI writing — one tap from the keyboard."
      chips={[
        { label: "QR + document scanner", accent: AMBER },
        { label: "Offline Whisper dictation", accent: LIME },
        { label: "Snippets with {date} {clip}", accent: null },
      ]}
      accentA={AMBER}
      accentB={CYAN}
    >
      <Series>
        {CUTS.map((cut) => (
          <Series.Sequence key={cut.clip} durationInFrames={cut.frames} layout="none">
            <PhoneFrame
              clip={cut.clip}
              still="kb-english-dark.png"
              width={400}
              delay={0}
              playbackRate={cut.rate}
              label={cut.label}
            />
          </Series.Sequence>
        ))}
      </Series>
    </SceneSplit>
  );
};
