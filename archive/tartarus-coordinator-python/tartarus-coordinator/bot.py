import asyncio
import websockets
from google.protobuf.json_format import MessageToJson

async def server_handler(websocket, path):
    async for message in websocket:
        print(f"Received from client: {message}")
        await websocket.send(f"Echo: {message}")

# async def start_server():
#     server = await websockets.serve(server_handler, "localhost", 8765)
#     await server.wait_closed()

async def client():
    async with websockets.connect("ws://localhost:5002/ws/bots") as websocket:
        await websocket.send("Hello, Server!")

        while True:    
            response = await websocket.recv()
            print(f"Received from server: {response}")

# Run the server and client concurrently
async def main():
    # server_task = asyncio.create_task(start_server())
    # await asyncio.sleep(1)  # Give the server some time to start
    await client()
    # server_task.cancel()

asyncio.run(main())