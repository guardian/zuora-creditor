import {ZuoraV1Api} from "canonical-config/dist/zuora-api.js";
import {appParams} from "canonical-config/dist/app-parameters.js";

const v1Api = await ZuoraV1Api.fromStage(appParams.stage);

const result = await v1Api.createJournalRun("Feb-23", "2023-02-28", [
    "Invoice Item",
    "Taxation Item",
    "Invoice Item Adjustment (Invoice)",
    "Invoice Item Adjustment (Tax)",
    "Credit Balance Adjustment (Applied from Credit Balance)",
    "Credit Balance Adjustment (Transferred to Credit Balance)",
    "Electronic Payment",
    "External Payment",
    "Electronic Refund",
    "External Refund",
    "Electronic Credit Balance Payment",
    "External Credit Balance Payment",
    "Electronic Credit Balance Refund",
    "External Credit Balance Refund",
    "Revenue Event Item",
]);

console.log(result);