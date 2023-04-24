import fs from 'fs';
import readline from 'readline';
import {lookupRegion, RegionColumns} from './region.js';
import {DiscountColumns, discountFixes} from "./discounts.js";
import { parse } from 'csv-parse/sync';

const inputPath = 'JournalEntryItem.csv';
const outputPath = 'JournalEntryItemFusion.csv';

const input = fs.createReadStream(inputPath);
const output = fs.createWriteStream(outputPath);

const rl = readline.createInterface({input, output});

let columns = [];
let idx = {
    country: -1,
    state: -1,
    productRatePlanChargeProductCode: -1,
    accountingCodeType: -1,
    accountingCodeAccount: -1,
    accountingCodeProduct: -1,
};

rl.on('line', process)
    .on('close', () => output.close());

function splitCsv(line: string): string[] {
    const fields = parse(line)[0];
    if (fields === undefined)
        console.log('undefined line', line, parse(line));
    return fields;
}

function process(line: string) {
    console.log(line);
    if (line) {
        output.write(line)
        if (columns.length === 0) {
            processHeader(line);
            output.write(',Region Code,Tax Account,Legal Entity,Discount Account,Discount Product\n');
        } else {
            const fields = splitCsv(line);

            const r = getRegionData(fields);
            const d = getDiscountData(fields);

            output.write(`,${r.regionCode},${r.taxAccount},${r.legalEntity},${d.accountCode},${d.productCode}\n`);
        }
    }
}

function processHeader(line: string) {
    columns = splitCsv(line);

    idx.country = columns.indexOf('Journal Entry: Sold To Contact Country');
    idx.state = columns.indexOf('Journal Entry: Sold To Contact State/Province');
    idx.productRatePlanChargeProductCode = columns.indexOf('Journal Entry: Product Rate Plan Charge ProductCode');
    idx.accountingCodeType = columns.indexOf('Accounting Code: Type');
    idx.accountingCodeAccount = columns.indexOf('Accounting Code: Account');
    idx.accountingCodeProduct = columns.indexOf('Accounting Code: Product');
}

function getRegionData(fields: string[]): RegionColumns {
    const country = fields[idx.country];
    const state = fields[idx.state];
    const accountingCodeType = fields[idx.accountingCodeType];

    return lookupRegion(country, state, accountingCodeType);
}

function getDiscountData(fields: string[]): DiscountColumns {
    const productRatePlanChargeProductCode = fields[idx.productRatePlanChargeProductCode];
    const accountingCodeType = fields[idx.accountingCodeType];
    const accountingCodeAccount = fields[idx.accountingCodeAccount];
    const accountingCodeProduct = fields[idx.accountingCodeProduct];

    return discountFixes(productRatePlanChargeProductCode, accountingCodeType, accountingCodeAccount, accountingCodeProduct);
}
