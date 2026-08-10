import React from "react";
import { TransitionSeries, linearTiming } from "@remotion/transitions";
import { fade } from "@remotion/transitions/fade";
import { slide } from "@remotion/transitions/slide";
import { Audio, Sequence, interpolate, staticFile, useVideoConfig } from "remotion";
import { MUSIC } from "./clips";
import { SceneAddons } from "./scenes/SceneAddons";
import { SceneEmoji } from "./scenes/SceneEmoji";
import { SceneGlide } from "./scenes/SceneGlide";
import { SceneIntro } from "./scenes/SceneIntro";
import { SceneModes } from "./scenes/SceneModes";
import { SceneOutro } from "./scenes/SceneOutro";
import { SceneThemes } from "./scenes/SceneThemes";
import { SceneTools } from "./scenes/SceneTools";

const T = 12; // transition overlap frames
const DURATIONS = [70, 150, 120, 200, 120, 140, 130, 120];
// Each transition starts where the next sequence begins.
const TRANSITION_STARTS = DURATIONS.slice(0, -1).reduce<number[]>((acc, d) => {
  const prev = acc.length ? acc[acc.length - 1] : 0;
  return [...acc, prev + d - T];
}, []);

export const Trailer: React.FC = () => {
  const { durationInFrames, fps } = useVideoConfig();
  return (
    <>
      {MUSIC ? (
        <Audio
          src={staticFile(MUSIC)}
          volume={(f) =>
            interpolate(
              f,
              [0, fps, durationInFrames - 2 * fps, durationInFrames],
              [0, 0.72, 0.72, 0],
              { extrapolateLeft: "clamp", extrapolateRight: "clamp" },
            )
          }
        />
      ) : null}
      {TRANSITION_STARTS.map((f) => (
        <Sequence key={f} from={f - 3}>
          <Audio src={staticFile("sfx/whoosh.wav")} volume={0.4} />
        </Sequence>
      ))}
      <TransitionSeries>
        <TransitionSeries.Sequence durationInFrames={70} name="Intro">
          <SceneIntro />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={fade()}
          timing={linearTiming({ durationInFrames: T })}
        />
        <TransitionSeries.Sequence durationInFrames={150} name="Glide">
          <SceneGlide />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-right" })}
          timing={linearTiming({ durationInFrames: T })}
        />
        <TransitionSeries.Sequence durationInFrames={120} name="Themes">
          <SceneThemes />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-bottom" })}
          timing={linearTiming({ durationInFrames: T })}
        />
        <TransitionSeries.Sequence durationInFrames={200} name="Tools">
          <SceneTools />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-right" })}
          timing={linearTiming({ durationInFrames: T })}
        />
        <TransitionSeries.Sequence durationInFrames={120} name="Modes">
          <SceneModes />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-bottom" })}
          timing={linearTiming({ durationInFrames: T })}
        />
        <TransitionSeries.Sequence durationInFrames={140} name="Emoji">
          <SceneEmoji />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={slide({ direction: "from-right" })}
          timing={linearTiming({ durationInFrames: T })}
        />
        <TransitionSeries.Sequence durationInFrames={130} name="Addons">
          <SceneAddons />
        </TransitionSeries.Sequence>
        <TransitionSeries.Transition
          presentation={fade()}
          timing={linearTiming({ durationInFrames: T })}
        />
        <TransitionSeries.Sequence durationInFrames={120} name="Outro">
          <SceneOutro />
        </TransitionSeries.Sequence>
      </TransitionSeries>
    </>
  );
};

// 70+150+120+200+120+140+130+120 - 7*12 = 966 frames = 32.2s @ 30fps
export const TRAILER_DURATION = 966;
