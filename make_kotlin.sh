#!/bin/zsh -ex

flatc --kotlin flatbuffers/contract.fbs
rsync -av club/ tartarus-coordinator/src/main/kotlin/club/
