import "./index.css";
import React from "react";
import { Composition, Folder } from "remotion";
import { Trailer, TRAILER_DURATION } from "./Trailer";
import { FPS, HEIGHT, WIDTH } from "./theme";
import { SceneAddons } from "./scenes/SceneAddons";
import { SceneEmoji } from "./scenes/SceneEmoji";
import { SceneGlide } from "./scenes/SceneGlide";
import { SceneIntro } from "./scenes/SceneIntro";
import { SceneModes } from "./scenes/SceneModes";
import { SceneOutro } from "./scenes/SceneOutro";
import { SceneThemes } from "./scenes/SceneThemes";
import { SceneTools } from "./scenes/SceneTools";

const size = { fps: FPS, width: WIDTH, height: HEIGHT };

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition id="Trailer" component={Trailer} durationInFrames={TRAILER_DURATION} {...size} />
      <Folder name="Scenes">
        <Composition id="Intro" component={SceneIntro} durationInFrames={110} {...size} />
        <Composition id="Glide" component={SceneGlide} durationInFrames={205} {...size} />
        <Composition id="Themes" component={SceneThemes} durationInFrames={205} {...size} />
        <Composition id="Tools" component={SceneTools} durationInFrames={245} {...size} />
        <Composition id="Modes" component={SceneModes} durationInFrames={185} {...size} />
        <Composition id="Emoji" component={SceneEmoji} durationInFrames={185} {...size} />
        <Composition id="Addons" component={SceneAddons} durationInFrames={160} {...size} />
        <Composition id="Outro" component={SceneOutro} durationInFrames={229} {...size} />
      </Folder>
    </>
  );
};
