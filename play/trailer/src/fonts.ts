import { loadFont as loadManrope } from "@remotion/google-fonts/Manrope";
import { loadFont as loadInter } from "@remotion/google-fonts/Inter";
import { loadFont as loadSpaceGrotesk } from "@remotion/google-fonts/SpaceGrotesk";

const manrope = loadManrope("normal", { weights: ["600", "700", "800"] });
const inter = loadInter("normal", { weights: ["400", "500", "600"] });
const spaceGrotesk = loadSpaceGrotesk("normal", { weights: ["500", "700"] });

export const MANROPE = manrope.fontFamily;
export const INTER = inter.fontFamily;
export const SPACE_GROTESK = spaceGrotesk.fontFamily;
