import asyncio
import argparse
import ssl
import paho.mqtt.client as mqtt
import random
from datetime import datetime, timedelta, timezone
import traceback

from club.subjugated.fb.bots.GetLockSessionResponse import GetLockSessionResponse
from club.subjugated.fb.event.SignedEvent import SignedEvent
from club.subjugated.fb.event.EventType import EventType
from club.subjugated.fb.bots.BotApiMessage import BotApiMessage
from club.subjugated.fb.event.SignedEvent import SignedEvent

from tartarus_bot_example.file_database import FileDatabase
from tartarus_bot_example.signer import Signer
from tartarus_bot_example.api_helper import make_create_contract_request, make_contract_request, make_get_lock_session_request, make_new_message_request, make_release_command_request, response_as_create_contract_response, response_as_get_contract_response, response_as_get_lock_session_response

signer = Signer()

def has_callable(obj, method_name):
    return hasattr(obj, method_name) and callable(getattr(obj, method_name))

class AsyncTartarusClient:
    def __init__(self, bot_name, broker, port, websocket_path="/mqtt", callback_obj=None, require_tls=False):
        self.broker = broker
        self.port = port
        self.bot_name = bot_name
        self.client = mqtt.Client(
            client_id=f"bot-{bot_name}",
            transport="websockets",)
        self.client.username = self.bot_name
        self.client.keepalive = 30

        self.client.ws_set_options(path=websocket_path)

        if require_tls:
            self.client.tls_set(cert_reqs=ssl.CERT_REQUIRED)

        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message

        self.callback_obj = callback_obj
        self.loop = asyncio.get_event_loop()

        self.responses = {}

    def on_connect(self, client, userdata, flags, rc):
        print(f"Connected!")
        client.subscribe(f"bots/inbox_events_{self.bot_name}")
        client.subscribe(f"bots/inbox_api_{self.bot_name}")

    def on_message(self, client, userdata, msg):
        print(f"Received message on: {msg.topic}")

        if "events" in msg.topic:
            se = SignedEvent.GetRootAsSignedEvent(msg.payload)

            e = se.Payload()
            common_metadata = e.Metadata()
            print(common_metadata.LockSession())
            print(common_metadata.ContractSerialNumber())
            
            match e.EventType():
                case EventType.AcceptContract:
                    print("Event -> Contract accepted")
                    if has_callable(self.callback_obj, 'on_accept'):
                        asyncio.run_coroutine_threadsafe(self.callback_obj.on_accept(self, se), self.loop)
                case EventType.LocalLock:
                    print("Local lock")
                    if has_callable(self.callback_obj, 'on_local_lock'):
                        asyncio.run_coroutine_threadsafe(self.callback_obj.on_local_lock(self, se), self.loop)
                case EventType.LocalUnlock:
                    print("Local Unlock")
                    if has_callable(self.callback_obj, 'on_local_unlock'):
                        asyncio.run_coroutine_threadsafe(self.callback_obj.on_local_unlock(self, se), self.loop)
                case EventType.ReleaseContract:
                    print("Contract released")
                    if has_callable(self.callback_obj, 'on_release'):
                        asyncio.run_coroutine_threadsafe(self.callback_obj.on_release(self, se), self.loop)
                case EventType.Lock:
                    print("Locked via command")
                    if has_callable(self.callback_obj, 'on_lock'):
                        asyncio.run_coroutine_threadsafe(self.callback_obj.on_lock(self, se), self.loop)
                case EventType.Unlock:
                    print("Unlocked via command")
                    if has_callable(self.callback_obj, 'on_unlock'):
                        asyncio.run_coroutine_threadsafe(self.callback_obj.on_unlock(self, se), self.loop)
        if "api" in msg.topic:
            api_message = BotApiMessage.GetRootAsBotApiMessage(msg.payload)

            if api_message.RequestId() in self.responses:
                queue = self.responses[api_message.RequestId()]
                del self.responses[api_message.RequestId()]
                self.loop.call_soon_threadsafe(queue.put_nowait, api_message)
            else:
                print("WARN: We got an API response for something we don't have a request ID for")

    def make_api_request(self, message: BotApiMessage, queue):
        self.client.publish("coordinator/inbox", message._tab.Bytes)
        self.responses[message.RequestId()] = queue

    async def start(self):
        self.client.connect(self.broker, self.port, 60)
        self.client.loop_start()

        try:
            while True:
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            self.client.loop_stop()
            self.client.disconnect()


class TimerBot():
    def __init__(self, bot_name, work_dir):
        self.response_queue = asyncio.Queue()
        self.bot_name = bot_name
        self.tartarus = None
        self.db = FileDatabase(work_dir)

    async def issue_contract(self, shareableToken) -> dict:
        signer = Signer()
        serial_number = random.getrandbits(16)

        # Make the record first because it's possible that the accept_contract event
        # will arrive before the response.
        self.db.save(serial_number, {})

        message = make_create_contract_request(shareableToken, serial_number, self.bot_name, signer)
        print("Making create contract request")
        self.tartarus.make_api_request(message, self.response_queue)

        try:
            response : BotApiMessage = await asyncio.wait_for(self.response_queue.get(), timeout=5.0)
            print(f"Received response: {response}")

            data = response_as_create_contract_response(response)
            data['shareable_token'] = shareableToken
            self.db.save(serial_number, data)
            return data

        except asyncio.TimeoutError:
            print("Response timeout!")

    async def add_message(self, contract_name, message_body):
        print("Make API request from bot")
        message = make_new_message_request(self.bot_name, message_body, contract_name)
        print(f"message {message}")
        self.tartarus.make_api_request(message, self.response_queue)

        try:
            response = await asyncio.wait_for(self.response_queue.get(), timeout=5.0)
            print(f"Received response: {response}")
            
        except asyncio.TimeoutError as t:
            print("Response timeout!")
            raise t

    async def get_lock_session(self, shareableToken):
        message = make_get_lock_session_request(self.bot_name, shareableToken)
        print("Making lock session request")
        self.tartarus.make_api_request(message, self.response_queue)

        try:
            response : BotApiMessage = await asyncio.wait_for(self.response_queue.get(), timeout=5.0)
            print(f"Received response: {response}")

            data = response_as_get_lock_session_response(response)
            print(data)
            self.db.save(data['name'], data)

        except asyncio.TimeoutError:
            print("Response timeout!")

    async def get_contract(self, lock_session, contract_serial_number):
        print("Make API request from bot")
        message = make_contract_request(self.bot_name, lock_session, contract_serial_number)
        print(f"message {message}")
        self.tartarus.make_api_request(message, self.response_queue)

        try:
            response = await asyncio.wait_for(self.response_queue.get(), timeout=5.0)
            print(f"Received response: {response}")
            return response_as_get_contract_response(response)
        except asyncio.TimeoutError as t:
            print("Response timeout!")
            raise t

    async def release_contract(self, contract_name, shareable_token, serial_number : int, counter):
        signer = Signer()
        message = make_release_command_request(self.bot_name, contract_name, shareable_token, serial_number, counter, signer)
        print("Making release contract request")
        self.tartarus.make_api_request(message, self.response_queue)

        try:
            response : BotApiMessage = await asyncio.wait_for(self.response_queue.get(), timeout=5.0)
            print(f"Received response: {response}")
        except asyncio.TimeoutError:
            print("Response timeout!")

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

            await self.add_message(data['contract_name'], f"Your lockup time was decided: {num_minutes} minutes")

            data['now'] = now
            data['release_time'] = release_time
            data['lock_session'] = common_metadata.LockSession().decode('utf-8')
            self.db.save(common_metadata.ContractSerialNumber(), data)
            print("Saved release time")
            print(data)
        except Exception as e:
            print(f"Encountered exception: {e}")


    async def on_lock(self, tartarus, event):
        print("TimerBot on_lock")
        e = event.Payload()
        common_metadata = e.Metadata()
        print(common_metadata.LockSession())
        print(common_metadata.ContractSerialNumber())

        print("Make API request from bot")
        message = make_contract_request(self.bot_name, common_metadata.LockSession(),common_metadata.ContractSerialNumber())
        print(f"message {message}")
        tartarus.make_api_request(message, self.response_queue)

        try:
            response = await asyncio.wait_for(self.response_queue.get(), timeout=5.0)
            print(f"Received response: {response}")
        except asyncio.TimeoutError:
            print("Response timeout!")

    async def on_unlock(self, tartarus, event):
        print("TimerBot un_lock")

    async def on_release(self, tartarus, event):
        e = event.Payload()
        common_metadata = e.Metadata()
        print(common_metadata.LockSession())
        print(common_metadata.ContractSerialNumber())

        self.db.delete(common_metadata.ContractSerialNumber())

def parse_args():
    parser = argparse.ArgumentParser(description="Process a shareable token.")
    parser.add_argument("shareableToken", type=str, help="A shareable token string")
    return parser.parse_args()

async def call_timer_method(timer: TimerBot, shareable):
    # while True:
    await asyncio.sleep(1)  # Adjust the interval as needed
    print("Calling timer method...")
    contract_data = await timer.issue_contract(shareable)  # Replace with your actual timer method
    await timer.add_message(contract_data['contract_name'], "Hello!")

    # await timer.get_lock_session(shareable)  # Replace with your actual timer method
    # await timer.release_contract("c-AQP0MJG", "s-RVVHU4O", 25798, 100)

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
                
                if 'release_time' in contract:
                    if now > contract['release_time']:
                        print("Release time has passed")
                        updated_contract = await timer.get_contract(contract['lock_session'], int(c))
                        print(f"Updated contract {updated_contract}")
                        if updated_contract['state'] != 'RELEASED' and updated_contract['state'] != 'ABORTED':
                            await timer.add_message(contract['contract_name'], "You've done your time.")
                            await timer.release_contract(contract['contract_name'], contract['shareable_token'], int(c), 2000)
                        else:
                            # Already ended.
                            timer.db.delete(c)
                else:
                    print(f"Waiting for {c} to be accepted.")

            except Exception as e:
                print(f"Failed to read contract {c} -> {e}")
                traceback.print_exception(e)
        await asyncio.sleep(20)

async def main():
    args = parse_args()

    bot_name = "b-JKSMF9G" # Production bot name

    # bot_name = "b-42R6AGO" # Local development

    # make_create_contract_request(args.shareableToken, "b-42R6AGO")
    # make_get_lock_session_request(bot_name, args.shareableToken)

    timer = TimerBot(bot_name=bot_name, work_dir="/tmp/workspace")

    try:
        # client = AsyncTartarusClient(bot_name=bot_name, broker="localhost", port=4447, callback_obj=timer)
        client = AsyncTartarusClient(bot_name=bot_name, broker="tartarus-mqtt.subjugated.club", port=4447, callback_obj=timer, require_tls=True)
        timer.tartarus = client
        asyncio.create_task(call_timer_method(timer, args.shareableToken))
        asyncio.create_task(scan_contracts_and_release(timer))

        await client.start()
    except Exception as e:
        print(f"Unhandled exception in main: {e}")
        raise e



asyncio.run(main())

