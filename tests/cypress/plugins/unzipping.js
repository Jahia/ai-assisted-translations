const decompress = require("decompress");
const glob = require("glob");

const unzipArtifact = ({ artifactFilename, filteredPath }) => {
  const files = glob.sync("./artifacts/" + artifactFilename);
  if (files.length === 0) {
    throw new Error(`No files found matching pattern: ${artifactFilename}`);
  }
  return decompress(files[0], "./artifacts", {
    filter: (file) => {
      return filteredPath === file.path;
    },
  });
};

module.exports = {
  unzipArtifact,
};
