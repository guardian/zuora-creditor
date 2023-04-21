import "source-map-support/register";
import { App } from "aws-cdk-lib";
import { ZuoraOracleFusion } from "../lib/zuora-oracle-fusion";

const app = new App();
new ZuoraOracleFusion(app, "ZuoraOracleFusion-CSBX", { stack: "membership", stage: "CSBX" });
new ZuoraOracleFusion(app, "ZuoraOracleFusion-PROD", { stack: "membership", stage: "PROD" });
