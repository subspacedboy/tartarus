#!/bin/zsh -ex

FBS_FILES=(
  flatbuffers/contract.fbs
  flatbuffers/configuration.fbs
  flatbuffers/event.fbs
  flatbuffers/firmware.fbs
)

flatc --rust "${FBS_FILES[@]}"
mv contract_generated.rs src/
mv configuration_generated.rs src/
mv event_generated.rs src/
mv firmware_generated.rs src/

flatc --python "${FBS_FILES[@]}"
rm -rf tartarus-bot-example/src/club
mv club tartarus-bot-example/src

flatc --ts "${FBS_FILES[@]}"
rm -rf tartarus/src/app/club
mv club tartarus/src/app/
# It's making this extra file with bad import paths?
rm tartarus/src/app/club/subjugated/fb/message.ts

flatc --kotlin "${FBS_FILES[@]}"
rm -rf tartarus-coordinator/src/main/kotlin/club/subjugated/fb
rm -rf overlord_exe/src/main/kotlin/club/subjugated/fb
rsync -av club/ tartarus-coordinator/src/main/kotlin/club/
rsync -av club/ overlord_exe/src/main/kotlin/club/
find tartarus-coordinator/src/main/kotlin/club overlord_exe/src/main/kotlin/club -type f -name "*.kt" -exec sed -i '' 's/FLATBUFFERS_[0-9_]*/FLATBUFFERS_24_3_25/g' {} +

# Remove the temp directory
rm -rf club
