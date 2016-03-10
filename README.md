# Semantically-Enhanced Automated Assessment for Virtual Environments (SAVE)

This is the backend of the SAVE framework, which can observe a learner operating within an
instrumented virtual environment, assess learner performance, and provide helpful feedback to
improve learner skills.

This is the top level of the SAVE source tree.

## Build system

The build uses Gradle. It's self-contained in this repository, so you don't need to install
anything on your machine. From this directory, type `gradlew test` to run the tests, or
`gradlew eclipse` to auto-generate Eclipse project files.

## Missing libraries

Some libraries have been removed from this distribution:

* Java installer
* NPM (Node.JS) installer
* SRI libraries, not funded by this project:
  * DEFT automated assessment
  * LAPDOG (required by DEFT)
  * Lumen (required by DEFT)
  * Sunflower Foundation (formerly FloraLib)
  * floralib-ext

## Acknowledgment of support

This work is supported by the Advanced Distributed Learning Initiative (ADL) under Contract
No. W911QY-14-C-0023. The United States Government retains unlimited rights (See
DFARS 252.227-7013 and 7014) to the software and technical data developed under the contract that
are not otherwise designated as "special works" by the Contracting Officer, with "special works"
as defined by DFARS 252.227-7020.

## License

Copyright 2016 SRI International, unless otherwise stated. Licensed under the
[Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
