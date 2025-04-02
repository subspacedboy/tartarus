#!/bin/zsh -ex

flatc --kotlin flatbuffers/*.fbs
rsync -av club/ tartarus-coordinator/src/main/kotlin/club/

# Remove the temp directory
rm -rf club
