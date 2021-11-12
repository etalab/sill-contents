[![img](https://img.shields.io/badge/Licence-EPL-orange.svg?style=flat-square)](https://git.sr.ht/~etalab/sill-consolidate-data/blob/master/LICENSE)


# Consolidate and generate SILL data

`SILL` stands for "socle interministériel de logiciels libres", which
refers to the list of recommended free software for the french public
sector.

You can browse it on [sill.etalab.gouv.fr](https://sill.etalab.gouv.fr).

This repository contains the code to generate various `json` files that
are used by the SILL frontend.

It takes SILL data from [this csv file](https://git.sr.ht/~etalab/sill/blob/master/sill.csv) and convert it to this [json](https://code.gouv.fr/data/sill.json),
also adding Wikidata additional information when available.

The SILL data are published under the [Open License 2.0](https://www.etalab.gouv.fr/licence-ouverte-open-licence).


# Contributing

The development of this repository happens on [the SourceHut
repository](https://git.sr.ht/~etalab/sill-consolidate-data).  

The code is also published on [GitHub](https://github.com/etalab/sill-data/) to reach more developers, but
please do not send pull requests there.

You can send **patches** by email using [git-send-email.io](https://git-send-email.io/).  For your
patches to be processed correctly, configure your local copy with
this:

    git config format.subjectPrefix 'PATCH sill-consolidate-data'

You can also contribute with bug reports, feature requests or general
questions by writing to [~etalab/codegouvfr-devel@lists.sr.ht](mailto:~etalab/codegouvfr-devel@lists.sr.ht).


# License

2020-2021 DINUM, Etalab.

This application is published under the [EPL 2.0 license](https://git.sr.ht/~etalab/sill-consolidate-data/blob/master/LICENSE).

