import React from "react";
import { PhoneFrame } from "../components/PhoneFrame";
import { MAGENTA, VIOLET } from "../theme";
import { SceneSplit } from "./SceneSplit";

export const SceneEmoji: React.FC = () => {
  return (
    <SceneSplit
      headline="Say it with «style»"
      sub="Semantic emoji search in 125 languages, animated emoji, GIFs and your own sticker packs."
      chips={[
        { label: "Semantic emoji search", accent: MAGENTA },
        { label: "Animated emoji", accent: MAGENTA },
        { label: "Custom sticker packs", accent: null },
      ]}
      accentA={MAGENTA}
      accentB={VIOLET}
    >
      <PhoneFrame
        clip="emoji"
        still="kb-english-dark.png"
        width={400}
        delay={8}
        tilt={2}
        playbackRate={1.2}
      />
    </SceneSplit>
  );
};
