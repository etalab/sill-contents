
# Generate SILL data

SILL stands for "Socle Interministériel de Logiciels Libres"
("Recommended free softwares for the public sector").

Browse it here: <https://sill.etalab.gouv.fr>

This repository contains the code to generate `sill.json`, read by the
SILL frontend.

It takes SILL data from [this csv file](https://raw.githubusercontent.com/DISIC/sill/master/2020/sill-2020.csv) and convert it to `json`, adding
wikidata additional information.


# Generate the binary file

Assuming GraalVM >19.3 is installed:

    clj -A:native-image


# License

2020-2021 DINUM, Bastien Guerry.

This application is published under the EPL 2.0 license.

