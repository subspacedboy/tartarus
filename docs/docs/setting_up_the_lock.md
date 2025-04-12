# Initial Setup

## Getting on Wi-Fi

The first, and really only step, to completing setup is getting the lock on Wi-Fi.

There's a few ways to do this.

Note: The generated code is a standard WPA3 Wi-Fi URI. Anything offering to make you a Wi-fi QR code is probably going to work. [The spec](https://www.wi-fi.org/system/files/WPA3%20Specification%20v3.2.pdf#page=25).

* There's a helper page included in the webapp called the
[Wi-Fi helper](https://tartarus.subjugated.club/wifi-helper). You can put your SSID/Network name and password (nothing is transmitted or leaves the page, you can verify it in the source) and it will
give you a QR code that you can read with the lock.
* If you're using newer iOS or OSX the "Passwords" app can make scannable QR codes directly with your saved credentials. [Share Wi-Fi Network Passwords Using QR Codes](https://austinmacworks.com/share-wi-fi-network-passwords-using-qr-codes/).
* Windows 11 has something similar. [Windows 11 24H2: Create QR Code to share Wi-Fi access](https://www.youtube.com/watch?v=i4rae2FXzrQ)

## Verifying it worked

Once the lock is online it will try and connect to the default message broker at `wss://tartarus-mqtt.subjugated.club:4447/mqtt`.
If it succeeds it will download a configuration blob containing everything it needs to get create login credentials for you.

Give it a reset and a scannable QR code should be on the display after it boots.