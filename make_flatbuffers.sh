#!/bin/zsh -ex

flatc --rust flatbuffers/*.fbs
mv contract_generated.rs src/
mv configuration_generated.rs src/
mv event_generated.rs src/
mv firmware_generated.rs src/
# We don't actually use any bots tables/messages in the firmware
rm bots_generated.rs

flatc --python flatbuffers/*.fbs
rm -rf tartarus-bot-example/src/club
mv club tartarus-bot-example/src

flatc --ts flatbuffers/*.fbs
rm -rf tartarus/src/app/club
mv club tartarus/src/app/
# It's making this extra file with bad import paths?
rm tartarus/src/app/club/subjugated/fb/message.ts

flatc --kotlin flatbuffers/*.fbs
rsync -av club/ tartarus-coordinator/src/main/kotlin/club/

# Remove the temp directory
rm -rf club
