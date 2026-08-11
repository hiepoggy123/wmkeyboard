import React from "react";
import { AbsoluteFill } from "remotion";
import { Backdrop } from "../components/Backdrop";
import { Chip } from "../components/Chip";
import { GradientHeadline } from "../components/GradientHeadline";
import { INTER } from "../fonts";
import { BODY } from "../theme";

// Shared layout: text column left, phone(s) right.
export const SceneSplit: React.FC<{
  headline: string;
  sub?: string;
  chips?: { label: string; accent?: string | null }[];
  accentA?: string;
  accentB?: string;
  children: React.ReactNode; // phone frame(s)
}> = ({ headline, sub, chips = [], accentA, accentB, children }) => {
  return (
    <Backdrop accentA={accentA} accentB={accentB}>
      <AbsoluteFill
        style={{
          flexDirection: "row",
          alignItems: "center",
          padding: "0 140px",
          gap: 90,
        }}
      >
        <div style={{ flex: 1.15, display: "flex", flexDirection: "column", gap: 44 }}>
          <GradientHeadline text={headline} size={100} delay={4} />
          {sub ? (
            <div
              style={{
                fontFamily: INTER,
                fontWeight: 400,
                fontSize: 42,
                lineHeight: 1.4,
                color: BODY,
                maxWidth: 760,
              }}
            >
              {sub}
            </div>
          ) : null}
          <div style={{ display: "flex", flexWrap: "wrap", gap: 22, maxWidth: 800 }}>
            {chips.map((c, i) => (
              <Chip key={c.label} label={c.label} accent={c.accent} delay={22 + i * 7} />
            ))}
          </div>
        </div>
        <div
          style={{
            flex: 1,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 60,
          }}
        >
          {children}
        </div>
      </AbsoluteFill>
    </Backdrop>
  );
};
