import React from "react";
import { PhoneFrame } from "../components/PhoneFrame";
import { CYAN, LIME } from "../theme";
import { SceneSplit } from "./SceneSplit";

export const SceneAddons: React.FC = () => {
  return (
    <SceneSplit
      headline="Extend it «endlessly»"
      sub="Addon repositories serve themes, sound packs, dictionaries, sticker packs and Lua plugins."
      chips={[
        { label: "Addon repositories", accent: LIME },
        { label: "Lua plugins", accent: LIME },
        { label: "Import old-keyboard layouts", accent: CYAN },
      ]}
      accentA={LIME}
      accentB={CYAN}
    >
      <PhoneFrame clip="addons" still="theme-light.png" width={400} delay={4} tilt={-2} />
    </SceneSplit>
  );
};
