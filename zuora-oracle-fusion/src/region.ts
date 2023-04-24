// https://docs.google.com/spreadsheets/d/1vjluNxQON8P7fdDEW8q7CXD4Bcm-H40-8K-2fQ5Z9O4/edit#gid=1273794698

import cloneDeep from "clone-deep";

export type RegionColumns = {
    regionCode: string,
    taxAccount: string,
    legalEntity: string
}

// List of regions and their codes
const UK         = {regionCode: 'R101', legalEntity: '111', taxAccount: '225002'};
const EU         = {regionCode: 'R201', legalEntity: '111', taxAccount: '225006'};
const AUSTRALIA  = {regionCode: 'R801', legalEntity: '128', taxAccount: '225017'};
const CANADA     = {regionCode: 'R701', legalEntity: '129', taxAccount: '225018'};
const QUEBEC     = {regionCode: 'R712', legalEntity: '129', taxAccount: '225019'};
const ICELAND    = {regionCode: 'R388', legalEntity: '111', taxAccount: '225021'};
const INDIA      = {regionCode: 'R389', legalEntity: '111', taxAccount: '225014'};
const SINGAPORE  = {regionCode: 'R474', legalEntity: '111', taxAccount: '225021'};
const NZ         = {regionCode: 'R901', legalEntity: '128', taxAccount: '225016'};
const US         = {regionCode: 'R601', legalEntity: '129', taxAccount: '225015'};
const ROW        = {regionCode: 'R301', legalEntity: '111', taxAccount: '225021'};

export function lookupRegion(country: string, state: string, accountingCodeType: string): RegionColumns {
    let region = ROW;

    switch (country) {
        case 'United Kingdom':
            region = UK;
            break;
        case 'Austria':
        case 'Belgium':
        case 'Bulgaria':
        case 'Croatia':
        case 'Cyprus':
        case 'Czech Republic':
        case 'Denmark':
        case 'Estonia':
        case 'Finland':
        case 'France':
        case 'Germany':
        case 'Greece':
        case 'Hungary':
        case 'Ireland':
        case 'Italy':
        case 'Latvia':
        case 'Lithuania':
        case 'Luxembourg':
        case 'Malta':
        case 'Netherlands':
        case 'Poland':
        case 'Portugal':
        case 'Romania':
        case 'Slovakia':
        case 'Slovenia':
        case 'Spain':
        case 'Sweden':
            region = EU;
            break;
        case 'Australia':
            region = AUSTRALIA;
            break;
        case 'Canada':
            if (state === 'Quebec')
                region = QUEBEC;
            else
                region = CANADA;
            break;
        case 'Iceland':
            region = ICELAND;
            break;
        case 'India':
            region = INDIA;
            break;
        case 'Singapore':
            region = SINGAPORE;
            break;
        case 'New Zealand':
            region = NZ;
            break;
        case 'United States':
            region = US;
            break;
    }

    const result = cloneDeep(region);

    // Only include the taxAccount value if the transaction is for tax
    if (accountingCodeType !== 'Sales Tax Payable')
        result.taxAccount = '';

    return result;
}