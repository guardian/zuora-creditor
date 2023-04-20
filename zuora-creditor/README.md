# Zuora Creditor

This repository contains a Scala-based code API to handle the process of downloading a report of negative invoices from Zuora and transferring their balance into an account credit.

There is a concrete version of the ZuoraClients traint called `ZuoraClientsFromEnvironment.scala` which uses environment variables: `ZuoraApiAccessKeyId` `ZuoraApiSecretAccessKey` and `ZuoraApiHost`. 
Simply set them in IntelliJ when testing locally, and never run locally with Production credentials.
 
The code is structured in a way that an implementing class only need to:

- Configure the [Zuora Export command and filter criteria JSON](https://www.zuora.com/developer/api-reference/#operation/Object_POSTExport) for which negative invoices they want to target in a class which extends: `ExportCommand`
- Configure how they want to generate a `CreditBalanceAdjustment` by extending the `CreateCreditBalanceAdjustmentCommand` trait which has a method which takes a single `NegativeInvoiceToTransfer` case class to start with.
- Write your own main file or AWS Lambda function to orchestrate the stages of the process.
 
The repository contains the implementation of a specific use case of this process, which targets negative holiday suspension invoices. It has a main method for local testing and an AWS Lambda incorporated into the build system for deploying to production. See the Home Delivery Suspension Scala trait overrides: [src/main/resources/scala/com/gu/zuora/creditor/holidaysuspension/...](https://github.com/guardian/zuora-creditor/tree/main/src/main/scala/com/gu/zuora/creditor/holidaysuspension).
 
# Usage

Run `sbt compile` to generate the SOAP client sources.
Run `sbt assembly` to generate the Home Delivery Holiday Suspension AWS Lambda jar.
