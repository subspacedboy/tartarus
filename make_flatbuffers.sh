#!/bin/zsh -ex

flatc --rust flatbuffers/*.fbs
mv contract_generated.rs src/
mv configuration_generated.rs src/

# RIP python implementation
#rm -rf tartarus-coordinator/tartarus-coordinator/club
#mv club tartarus-coordinator/tartarus-coordinator/

flatc --ts flatbuffers/*.fbs
rm -rf tartarus/src/app/club
mv club tartarus/src/app/
# It's making this extra file with bad import paths?
rm tartarus/src/app/club/subjugated/fb/message.ts

flatc --kotlin flatbuffers/*.fbs
rsync -av club/ tartarus-coordinator/src/main/kotlin/club/

# Remove the temp directory
rm -rf club
