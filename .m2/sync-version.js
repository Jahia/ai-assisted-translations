/**
 * @module
 *
 * Updates the package.json version, used during the maven release process.
 *
 * Usage: node sync-version.js 1.2.3
 *
 * @see {@link ./pom.xml}
 */

import fs from "node:fs";

const updated = fs
  .readFileSync("package.json", "utf8")
  .replace(/"version": ".+"/, `"version": ${JSON.stringify(process.argv[2])}`);
fs.writeFileSync("package.json", updated);
