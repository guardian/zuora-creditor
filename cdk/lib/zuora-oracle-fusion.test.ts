import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { ZuoraOracleFusion } from "./zuora-oracle-fusion";

describe("The ZuoraOracleFusion stack", () => {
  it("matches the snapshot", () => {
    const app = new App();
    const stack = new ZuoraOracleFusion(app, "ZuoraOracleFusion", { stack: "membership", stage: "TEST" });
    const template = Template.fromStack(stack);
    expect(template.toJSON()).toMatchSnapshot();
  });
});
