#Zuora to Oracle Fusion

Post-processing lambda to make exported Zuora Journal Runs compatible with Oracle Fusion.

Issues that have to be fixed by post-processing include:

* Determine region from Sold-to country and assign Legal Entity, Region Code and Sales Tax Account Code.
* selection and ordering of columns

The country to region mapping is detailed here: https://docs.google.com/spreadsheets/d/1e2UqkwbfTn6hwP9_v3_qksl0I2id0-eNOdpbhRUWQJM/edit#gid=0

