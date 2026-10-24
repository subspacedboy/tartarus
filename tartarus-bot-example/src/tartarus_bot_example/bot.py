import argparse
import asyncio
import base64
from datetime import datetime, timedelta, timezone
import json
import random
import traceback
import urllib.request

import websockets

from club.subjugated.fb.event.EventType import EventType
from club.subjugated.fb.event.SignedEvent import SignedEvent

from tartarus_bot_example.api_helper import (
    make_signed_contract_bytes,
    make_signed_message_bytes,
    make_signed_release_command_bytes,
)
from tartarus_bot_example.file_database import FileDatabase
from tartarus_bot_example.signer import Signer


def has_callable(obj, method_name):
    return hasattr(obj, method_name) and callable(getattr(obj, method_name))


class AsyncTartarusClient:
    def __init__(
        self,
        bot_name: str,
        signer: Signer,
        coordinator_url: str = "http://localhost:8080",
        ws_url: str = "ws://localhost:8080/ws/events",
        callback_obj=None,
    ):
        self.bot_name = bot_name
        self.signer = signer
        self.coordinator_url = coordinator_url.rstrip("/")
        self.ws_url = ws_url
        self.callback_obj = callback_obj

    def _auth_headers(self) -> dict[str, str]:
        jwt = self.signer.create_jwt(self.bot_name)
        return {
            "Authorization": f"Bearer {jwt}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    def _sync_request(self, method: str, path: str, body: dict | None = None) -> dict:
        url = f"{self.coordinator_url}{path}"
        data = json.dumps(body).encode("utf-8") if body is not None else None
        req = urllib.request.Request(url, data=data, headers=self._auth_headers(), method=method)
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}

    async def get_lock_session(self, shareable_token: str) -> dict:
        return await asyncio.to_thread(
            self._sync_request,
            "GET",
            f"/bots/{self.bot_name}/lock_sessions/{shareable_token}",
        )

    async def create_contract(self, lock_session_token: str, signed_contract_bytes: bytes) -> dict:
        payload = {
            "lockSessionToken": lock_session_token,
            "signedMessageBase64": base64.b64encode(signed_contract_bytes).decode("ascii"),
        }
        return await asyncio.to_thread(
            self._sync_request,
            "POST",
            f"/bots/{self.bot_name}/contracts",
            payload,
        )

    async def get_contract(self, lock_session: str, contract_serial_number: int) -> dict:
        return await asyncio.to_thread(
            self._sync_request,
            "GET",
            f"/bots/{self.bot_name}/contracts/bySession/{lock_session}/{contract_serial_number}",
        )

    async def create_command(
        self,
        lock_session_token: str,
        contract_serial_number: int,
        signed_command_bytes: bytes,
    ) -> dict:
        payload = {
            "lockSessionToken": lock_session_token,
            "contractSerialNumber": contract_serial_number,
            "signedMessageBase64": base64.b64encode(signed_command_bytes).decode("ascii"),
        }
        return await asyncio.to_thread(
            self._sync_request,
            "POST",
            f"/bots/{self.bot_name}/commands",
            payload,
        )

    async def add_message(
        self,
        contract_name: str,
        message_text: str,
        signed_message_bytes: bytes,
    ) -> dict:
        payload = {
            "message": message_text,
            "signedMessageBase64": base64.b64encode(signed_message_bytes).decode("ascii"),
        }
        return await asyncio.to_thread(
            self._sync_request,
            "POST",
            f"/bots/{self.bot_name}/contracts/{contract_name}/messages",
            payload,
        )

    async def _handle_ws_message(self, raw_text: str):
        msg = json.loads(raw_text)
        if msg.get("type") != "bot_event" or not msg.get("rawSignedEventBase64"):
            return

        raw_bytes = base64.b64decode(msg["rawSignedEventBase64"])
        se = SignedEvent.GetRootAsSignedEvent(raw_bytes, 0)
        e = se.Payload()
        common_metadata = e.Metadata()
        print(common_metadata.LockSession())
        print(common_metadata.ContractSerialNumber())

        match e.EventType():
            case EventType.AcceptContract:
                print("Event -> Contract accepted")
                if has_callable(self.callback_obj, "on_accept"):
                    await self.callback_obj.on_accept(self, se)
            case EventType.LocalLock:
                print("Event -> Local lock")
                if has_callable(self.callback_obj, "on_local_lock"):
                    await self.callback_obj.on_local_lock(self, se)
            case EventType.LocalUnlock:
                print("Event -> Local Unlock")
                if has_callable(self.callback_obj, "on_local_unlock"):
                    await self.callback_obj.on_local_unlock(self, se)
            case EventType.ReleaseContract:
                print("Event -> Contract released")
                if has_callable(self.callback_obj, "on_release"):
                    await self.callback_obj.on_release(self, se)
            case EventType.Lock:
                print("Event -> Locked via command")
                if has_callable(self.callback_obj, "on_lock"):
                    await self.callback_obj.on_lock(self, se)
            case EventType.Unlock:
                print("Event -> Unlocked via command")
                if has_callable(self.callback_obj, "on_unlock"):
                    await self.callback_obj.on_unlock(self, se)
            case EventType.EmergencyUnlock:
                print("Event -> Emergency unlocked")
                if has_callable(self.callback_obj, "on_emergency_unlock"):
                    await self.callback_obj.on_emergency_unlock(self, se)

    async def start(self):
        while True:
            try:
                bot_jwt = self.signer.create_jwt(self.bot_name)
                uri = f"{self.ws_url}?botJwt={bot_jwt}"
                async with websockets.connect(uri) as ws:
                    print(f"Connected to {self.ws_url} as {self.bot_name}!")
                    async for raw_text in ws:
                        await self._handle_ws_message(raw_text)
            except asyncio.CancelledError:
                break
            except Exception as e:
                print(f"WebSocket connection lost ({e}), reconnecting in 3s...")
                await asyncio.sleep(3)


class TimerBot:
    def __init__(self, bot_name: str, work_dir: str, signer: Signer):
        self.bot_name = bot_name
        self.signer = signer
        self.tartarus: AsyncTartarusClient | None = None
        self.db = FileDatabase(work_dir)

    async def issue_contract(self, shareable_token: str) -> dict:
        serial_number = random.getrandbits(16)

        # Make the record first because it's possible that the accept_contract event
        # will arrive before the response.
        self.db.save(serial_number, {})

        signed_contract = make_signed_contract_bytes(serial_number, self.bot_name, self.signer)
        print("Making create contract request")
        response = await self.tartarus.create_contract(shareable_token, signed_contract)
        print(f"Received response: {response}")

        data = {
            "contract_name": response["name"],
            "shareable_token": shareable_token,
        }
        self.db.save(serial_number, data)
        return data

    async def add_message(self, contract_name: str, message_body: str):
        print("Make API request from bot")
        signed_msg = make_signed_message_bytes(self.bot_name, message_body, self.signer)
        response = await self.tartarus.add_message(contract_name, message_body, signed_msg)
        print(f"Received response: {response}")

    async def get_lock_session(self, shareable_token: str):
        print("Making lock session request")
        data = await self.tartarus.get_lock_session(shareable_token)
        print(f"Received response: {data}")
        self.db.save(data["name"], data)
        return data

    async def get_contract(self, lock_session: str, contract_serial_number: int) -> dict:
        print("Make API request from bot")
        response = await self.tartarus.get_contract(lock_session, contract_serial_number)
        print(f"Received response: {response}")
        return {
            "name": response["name"],
            "state": response["state"],
            "next_counter": response.get("nextCounter", 0),
        }

    async def release_contract(
        self,
        contract_name: str,
        shareable_token: str,
        serial_number: int,
        counter: int,
    ):
        signed_cmd = make_signed_release_command_bytes(
            self.bot_name,
            serial_number,
            counter,
            self.signer,
        )
        print("Making release contract request")
        response = await self.tartarus.create_command(shareable_token, serial_number, signed_cmd)
        print(f"Received response: {response}")

    async def on_accept(self, tartarus, event):
        print("Contract accepted")
        try:
            e = event.Payload()
            common_metadata = e.Metadata()
            print(common_metadata.LockSession())
            print(common_metadata.ContractSerialNumber())
            data = self.db.load(common_metadata.ContractSerialNumber())

            now = datetime.now(timezone.utc)
            num_minutes = 2
            release_time = now + timedelta(minutes=num_minutes)

            await self.add_message(
                data["contract_name"],
                f"Your lockup time was decided: {num_minutes} minutes",
            )

            data["now"] = now
            data["release_time"] = release_time
            data["lock_session"] = common_metadata.LockSession().decode("utf-8")
            self.db.save(common_metadata.ContractSerialNumber(), data)
            print("Saved release time")
            print(data)
        except Exception as e:
            print(f"Encountered exception: {e}")

    async def on_lock(self, tartarus, event):
        print("TimerBot on_lock")
        e = event.Payload()
        common_metadata = e.Metadata()
        lock_session = common_metadata.LockSession().decode("utf-8")
        serial = common_metadata.ContractSerialNumber()
        await self.get_contract(lock_session, serial)

    async def on_unlock(self, tartarus, event):
        print("TimerBot un_lock")

    async def on_release(self, tartarus, event):
        e = event.Payload()
        common_metadata = e.Metadata()
        print(common_metadata.LockSession())
        print(common_metadata.ContractSerialNumber())

        # Only delete in on_release because that's the lock
        # generated event.
        self.db.delete(common_metadata.ContractSerialNumber())


def parse_args():
    parser = argparse.ArgumentParser(description="Process a shareable token.")
    parser.add_argument("shareableToken", type=str, help="A shareable token string")
    parser.add_argument("--bot-name", type=str, default="b-42R6AGO", help="Bot external name (b-...)")
    parser.add_argument("--author-key", type=str, default=None, help="Path to Author private key PEM for deterministic derivation")
    parser.add_argument("--internal-name", type=str, default="timer_bot", help="Internal bot name for HKDF derivation")
    parser.add_argument("--key-version", type=int, default=1, help="Bot key version for HKDF derivation")
    parser.add_argument("--coordinator-url", type=str, default="http://localhost:8080", help="Tartarus Coordinator HTTP URL")
    parser.add_argument("--ws-url", type=str, default="ws://localhost:8080/ws/events", help="Tartarus Coordinator WebSocket URL")
    return parser.parse_args()


async def call_timer_method(timer: TimerBot, shareable: str):
    await asyncio.sleep(1)
    print("Calling timer method...")
    contract_data = await timer.issue_contract(shareable)
    await timer.add_message(contract_data["contract_name"], "Hello!")


async def scan_contracts_and_release(timer: TimerBot):
    while True:
        print("Checking on contracts")
        contracts = timer.db.scan()
        print(contracts)
        for c in contracts:
            try:
                contract = timer.db.load(c)
                print(contract)

                now = datetime.now(timezone.utc)

                if "release_time" in contract:
                    if now > contract["release_time"]:
                        print("Release time has passed")
                        updated_contract = await timer.get_contract(contract["lock_session"], int(c))
                        print(f"Updated contract {updated_contract}")
                        if updated_contract["state"] != "RELEASED" and updated_contract["state"] != "ABORTED":
                            await timer.add_message(contract["contract_name"], "You've done your time.")
                            await timer.release_contract(
                                contract["contract_name"],
                                contract["shareable_token"],
                                int(c),
                                updated_contract.get("next_counter", 2000),
                            )
                else:
                    print(f"Waiting for {c} to be accepted.")

            except Exception as e:
                print(f"Failed to read contract {c} -> {e}")
                traceback.print_exception(e)
        await asyncio.sleep(20)


async def main():
    args = parse_args()

    if args.author_key:
        bot_signer = Signer.from_author_key(args.author_key, args.internal_name, args.key_version)
    else:
        bot_signer = Signer()

    timer = TimerBot(bot_name=args.bot_name, work_dir="/tmp/workspace", signer=bot_signer)

    try:
        client = AsyncTartarusClient(
            bot_name=args.bot_name,
            signer=bot_signer,
            coordinator_url=args.coordinator_url,
            ws_url=args.ws_url,
            callback_obj=timer,
        )
        timer.tartarus = client
        asyncio.create_task(call_timer_method(timer, args.shareableToken))
        asyncio.create_task(scan_contracts_and_release(timer))

        await client.start()
    except Exception as e:
        print(f"Unhandled exception in main: {e}")
        raise e


if __name__ == "__main__":
    asyncio.run(main())
