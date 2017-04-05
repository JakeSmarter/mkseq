# Make Sequence (`mkseq`)
## What is Make Sequence
`mkseq` is a command‑line tool for processing photo sequences prior to upload to
Mapillary. It has been conceived as a prototype for functionality which is going
to be added to the [Mapillary plug‑in for JOSM](https://wiki.openstreetmap.org/wiki/JOSM/Plugins/Mapillary).
It is intended to automate common recurring tasks which may be useful in script
processing 

## Why Make Sequence

## What Make Sequence is not
* It is not a general purpose tool for handling and manipulating image files
  and meta data. 
* It is not a producer of *sequences*. A Mapillary sequence file format or
  anything similar does not exist. A Mapillary sequence is a just an abstract
  data type used by the Mapillary service.
* Sequence and photo *keys* are created on upload by the Mapillary service, not
  by `mkseq`.
* The source code of `mkseq` is not pretty (never intended).
* `mkseq` is not optimized, neither for speed nor size (never intended).

# Runtime Requirements
* [Java 8 SE](https://www.oracle.com/java),
  [OpenJDK 1.8.0](https://openjdk.java.net), or later
* [Apache Commons Imaging™](https://commons.apache.org/proper/commons-imaging)

# Install

# Building
## Requirements
* [Apache Ant™](https://ant.apache.org)
* [Java 8 SE](https://www.oracle.com/java),
  [OpenJDK 1.8.0](https://openjdk.java.net), or later
* [Apache Commons Imaging™](https://commons.apache.org/proper/commons-imaging)

Run
```sh
$ ant -projecthelp
```
for building targets. Note that not all targets produce expected results.

# Contributing
## Pull Requests
Because `mkseq` is just a prototype, pull requests with new or additional
functionality or new features will **not** be merged. Only bug fixes will be
accepted.

## Coding Style
* Use Java coding style, put block opening curly braces (`U+007B LEFT CURLY
  BRACKET`) on the same line as class and method declarations, and flow control 
  structures. Put block closing curly braces (`U+007D RIGHT CURLY BACKET`) on a
  separate line.
* Use 4 spaces (`U+0020 SPACE`) for indentation, not horizontal tabs (`U+0009
  CHARACTER TABULATION`)
* Lines should not exceed 80 characters
  * If method declaration parameters or calls exceed this limit indent by 4
    spaces on the next line
* Use [UTF-8](https://en.wikipedia.org/wiki/UTF-8) little endian encoding and
  no [BOM](https://en.wikipedia.org/wiki/Byte_Order_Mark) for source files

# Legal
## License
See the LICENSE.md file for the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## Trademarks
Mapillary is a registered trademark of Mapillary AB, Malmö, Kingdom of Sweden

Oracle and Java are registered trademarks of Oracle and/or its affiliates.
Other names may be trademarks of their respective owners.