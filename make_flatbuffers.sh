#!/bin/zsh -ex

flatc --rust flatbuffers/contract.fbs
mv contract_generated.rs src/contract_generated.rs

#rm -rf tartarus-coordinator/tartarus-coordinator/subjugated
#mv subjugated tartarus-coordinator/tartarus-coordinator/

#flatc --ts flatbuffers/contract.fbs
#rm -rf tartarus/src/app/subjugated
#mv subjugated tartarus/src/app/
