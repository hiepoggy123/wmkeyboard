import React from "react";
import { PhoneFrame } from "../components/PhoneFrame";
import { MAGENTA, VIOLET } from "../theme";
import { SceneSplit } from "./SceneSplit";

export const SceneThemes: React.FC = () => {
  return (
    <SceneSplit
      headline="Make it «yours»"
      sub="A full theme editor: palettes, fonts, key shapes, textures — even your own photos."
      chips={[
        { label: "Theme editor", accent: VIOLET },
        { label: "Photo backgrounds", accent: MAGENTA },
        { label: "Auto light/dark themes", accent: null },
      ]}
      accentA={MAGENTA}
      accentB={VIOLET}
    >
      <PhoneFrame
        clip="themes"
        still="theme-sakura.jpg"
        width={340}
        delay={8}
        tilt={-3}
        playbackRate={1.2}
      />
      <PhoneFrame still="theme-photo.png" width={300} delay={22} tilt={3} />
    </SceneSplit>
  );
};
