ls# What's this?

Post-processing script/s to make exported Zuora Journal Runs compatible with Oracle Fusion.

Issues that have to be fixed by post-processing include:

* Determine region from Sold-to country and assign Legal Entity, Region Code and Sales Tax Account Code.
* (Coming soon!) Assign correct codes to Discounts

The country to region mapping is detailed here: https://docs.google.com/spreadsheets/d/1e2UqkwbfTn6hwP9_v3_qksl0I2id0-eNOdpbhRUWQJM/edit#gid=0

This processing is at a prototype stage. There is a decision to be made whether post-process is the right approach. 

Pros:

- any shortcomings of the Zuora Journal can be fixed in one place
- can work across entities e.g. tax account depends on country (contact) and product (rate plan charge).

Cons:

- any post-processing is subject to financial auditing
- Finance team might prefer to be able to work directly with the Zuora UI to generate and download the Journals.


An alternative to post-processing might be to populate the Tax Region (or other custom field(s)) of the sold-to contact within Zuora and include those fields in the journal run.  
This would have to be done by a (daily ?) process such as a scheduled lambda or Zuora Workflow to scan all Sold-to contacts and determine their region.



## Input

Put the exported journal entry file from Zuora - `JournalEntryItem.csv` - into this directory.

## Run it!

```shell
npm install
npm run main
```

## Output

The output file - `JournalEntryItemFusion.csv` - will be created in this directory.

