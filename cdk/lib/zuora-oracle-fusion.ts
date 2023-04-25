import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";
import type { App } from "aws-cdk-lib";
import { GuApiLambda } from '@guardian/cdk/lib/patterns/api-lambda';
import { Runtime } from "aws-cdk-lib/aws-lambda";

export class ZuoraOracleFusion extends GuStack {
  protected apiLambda: GuApiLambda;

  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    this.apiLambda = new GuApiLambda(this, "zuora-oracle-post-process-lambda", {
      fileName: "zuora-oracle-fusion.zip",
      handler: "dist/lambda/main.handler",
      runtime: Runtime.NODEJS_18_X,
      monitoringConfiguration: {
        http5xxAlarm: { tolerated5xxPercentage: 5 },
        snsTopicName: "alerts-topic",
      },
      app: "zuora-oracle-fusion",
      api: {
        id: "zuora-oracle-fusion",
        description: "Webhook called by Zuora on completion of a Journal Run",
      },
    });
  }

}
