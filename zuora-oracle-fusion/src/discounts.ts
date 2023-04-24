
export type DiscountColumns = {
    accountCode : string,
    productCode : string
}

export function discountFixes(
    productRatePlanChargeProductCode : string,
    accountingCodeType : string,
    accountingCodeAccount : string,
    accountingCodeProduct : string,
) : DiscountColumns {

    const AC_DIGITAL_SUBSCRIBERS_REVENUE = '411301';
    const AC_DIGITAL_SUBSCRIBERS_DISCOUNTS = '411302';
    const DEFERRED_REVENUE = 'Deferred Revenue';
    const SALES_REVENUE = 'Sales Revenue';

    // Zuora is configured to report discounts as default product
    const isDiscount = productRatePlanChargeProductCode === 'P0000';

    if (isDiscount) {
        // if P0000 (i.e. it's a discount) and Account code is 411301 Digital subscribers - revenue, then change it to
        // 411302 Digital subscribers - discounts
        const accountCode = accountingCodeAccount === AC_DIGITAL_SUBSCRIBERS_REVENUE
            ? AC_DIGITAL_SUBSCRIBERS_DISCOUNTS
            : accountingCodeAccount;

        // And then the P0000 code needs overwrite as well
        // The overwrite would be linked to Accounting Code: Name,
        //  but only where "Accounting Code: Type" is "deferred revenue" or "sales revenue"
        const productCode = accountingCodeType === DEFERRED_REVENUE || accountingCodeType === SALES_REVENUE
            ? accountingCodeProduct
            : productRatePlanChargeProductCode;

        return {accountCode, productCode};
    } else {
        return {accountCode: "", productCode: ""};
    }
}
