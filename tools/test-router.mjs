import { readFileSync } from "node:fs";
import vm from "node:vm";

const rules = JSON.parse(readFileSync(new URL("../rules/rules.json", import.meta.url), "utf8"));
const fixtures = JSON.parse(readFileSync(new URL("./route-fixtures.json", import.meta.url), "utf8"));
const source = readFileSync(new URL("../app/src/main/assets/inject/router.js", import.meta.url), "utf8");

if (fixtures.length < 50) {
  console.error(`Need at least 50 fixtures, found ${fixtures.length}`);
  process.exit(1);
}

const context = { URL, URLSearchParams, console };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(source, context);
const router = context.dmgramRouter;
if (!router) {
  console.error("router.js did not export dmgramRouter");
  process.exit(1);
}

let failed = 0;
for (const fixture of fixtures) {
  const result = router.policy(fixture.tab, fixture.url, rules);
  const problems = [];
  if (result.class !== fixture.expectedClass) {
    problems.push(`class ${result.class} != ${fixture.expectedClass}`);
  }
  if (result.action !== fixture.expectedAction) {
    problems.push(`action ${result.action} != ${fixture.expectedAction}`);
  }
  if (fixture.expectedTab && result.tab !== fixture.expectedTab) {
    problems.push(`tab ${result.tab} != ${fixture.expectedTab}`);
  }
  if (problems.length) {
    failed += 1;
    console.error(`${fixture.tab} ${fixture.url}: ${problems.join(", ")}`);
  }
}

if (failed) {
  console.error(`${failed} fixture(s) failed`);
  process.exit(1);
}
console.log(`${fixtures.length} fixtures passed`);
