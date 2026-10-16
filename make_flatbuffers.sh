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
rsync -av club/ overlord_exe/src/main/kotlin/club/
find tartarus-coordinator/src/main/kotlin/club overlord_exe/src/main/kotlin/club -type f -name "*.kt" -exec sed -i '' 's/FLATBUFFERS_[0-9_]*/FLATBUFFERS_24_3_25/g' {} +

# Remove the temp directory
rm -rf club
