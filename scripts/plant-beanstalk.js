const fs = require('fs');
const path = require('path');

const version = process.argv[2];

if (!version) {
  throw new Error("Missing <version> argument");
}

const dockerrun = {
  "AWSEBDockerrunVersion": "1",
  "Image": {
    "Name": "mojdigitalstudio/keyworker-api:" + version,
    "Update": "true"
  },
  "Ports": [
    {"ContainerPort": "8080"}
  ]
};

const output = JSON.stringify(dockerrun, null, 2);

console.log(output);

fs.writeFileSync(path.resolve(__dirname, '../Dockerrun.aws.json'), output);
