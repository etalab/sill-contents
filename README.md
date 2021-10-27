
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


# Contribute

The development of this repository happens on [the SourceHut
repository](https://git.sr.ht/~etalab/sill-data).  The code is also published on [GitHub](https://github.com/etalab/sill-data) to reach more
developers.

Your help is welcome.  You can contribute with bug reports, patches,
feature requests or general questions by sending an email to
`~etalab@lists.sr.ht`.


# License

2020-2021 DINUM, Bastien Guerry.

This application is published under the EPL 2.0 license.

