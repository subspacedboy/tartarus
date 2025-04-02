#!/bin/zsh -ex

flatc --rust flatbuffers/contract.fbs
mv contract_generated.rs src/contract_generated.rs

# RIP python implementation
#rm -rf tartarus-coordinator/tartarus-coordinator/club
#mv club tartarus-coordinator/tartarus-coordinator/

flatc --ts flatbuffers/contract.fbs
rm -rf tartarus/src/app/club
mv club tartarus/src/app/
# It's making this extra file with bad import paths?
rm tartarus/src/app/club/subjugated/fb/message.ts

# Kotlin needs to be merged in
flatc --kotlin flatbuffers/contract.fbs
rsync -av club/ tartarus-coordinator/src/main/kotlin/club/

# Remove the temp directory
rm -rf club
