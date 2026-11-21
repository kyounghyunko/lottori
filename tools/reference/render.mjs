import { readFile, mkdir, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

const require = createRequire(import.meta.url);
const root = new URL("../../fixtures/", import.meta.url);
const destination = new URL("frames/web/", root);
const browser = await chromium.launch({
  ...(process.env.CHROME_BIN ? { executablePath: process.env.CHROME_BIN } : { channel: "chrome" }),
  headless: true,
  args: ["--disable-gpu"],
});
try {
  const page = await browser.newPage({ deviceScaleFactor: 1 });
  await page.setContent('<style>body{margin:0;background:transparent}</style><div id="view" style="width:100px;height:40px"></div>');
  await page.addScriptTag({ path: require.resolve("lottie-web/build/player/lottie_svg.min.js") });
  await mkdir(destination, { recursive: true });
  for (const [fixture, frames] of Object.entries({
    "static-circle": [0], "moving-circle": [0, 50, 100], "render-order": [50], timing: [0, 25, 50, 100],
  })) {
    const data = JSON.parse(await readFile(new URL(fixture + ".json", root), "utf8"));
    for (const percent of frames) {
      const sourceFrame = Math.min(data.ip + percent / 100 * (data.op - data.ip), Math.max(data.ip, Math.ceil(data.op) - 1));
      await page.evaluate(async ({ data, sourceFrame }) => {
        const anim = lottie.loadAnimation({
          container: document.getElementById("view"), renderer: "svg",
          loop: false, autoplay: false, animationData: data, rendererSettings: { dpr: 1 },
        });
        window.currentAnimation = anim;
        if (!anim.isLoaded) await new Promise((resolve, reject) => {
          anim.addEventListener("DOMLoaded", resolve);
          anim.addEventListener("data_failed", reject);
        });
        anim.goToAndStop(sourceFrame - data.ip, true);
        if (!document.querySelector("#view svg path[d]")) throw Error("Missing reference geometry");
      }, { data, sourceFrame });
      const png = await page.locator("#view").screenshot({ omitBackground: true });
      await page.evaluate(() => window.currentAnimation.destroy());
      const name = fixture + "-" + String(percent).padStart(3, "0") + ".png";
      await writeFile(new URL(name, destination), png);
      console.log(name, "source frame", sourceFrame);
    }
  }
  console.log("Browser", await browser.version(), fileURLToPath(destination));
} finally {
  await browser.close();
}
