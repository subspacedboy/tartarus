import {Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class CryptoService {

  constructor() { }

  async generateCompressedPublicKey(publicKey : CryptoKey): Promise<Uint8Array> {
    console.log("publicKey: " + publicKey);
    // Export and compress public key
    const rawPublicKey = await this.exportRawPublicKey(publicKey);
    const compressedKey = this.compressPublicKey(rawPublicKey);

    console.log("Compressed Public Key:", compressedKey);
    return compressedKey;
  }

  async sha256(data: Uint8Array): Promise<ArrayBuffer> {
    return await crypto.subtle.digest("SHA-256", data);
  }

  async hashAndSignData(privateKey: CryptoKey, data: Uint8Array): Promise<ArrayBuffer> {
    console.log("Hashing data: " + new Uint8Array(data));
    const hash = await this.sha256(data); // Compute hash first

    console.log("Hash: " + new Uint8Array(hash));
    return await crypto.subtle.sign(
      {name: "ECDSA", hash: {name: "SHA-256"}},
      privateKey,
      hash
    )
  }

  convertSignatureFromRawToDER(rawSig: Uint8Array): Uint8Array {
    if (rawSig.length !== 64) {
      throw new Error("Invalid raw signature length");
    }

    const r = rawSig.slice(0, 32);
    const s = rawSig.slice(32, 64);

    function encodeInteger(bytes: Uint8Array): number[] {
      let arr = Array.from(bytes);

      // Ensure positive number (avoid leading zeros)
      if (arr[0] & 0x80) arr.unshift(0);

      return [0x02, arr.length, ...arr]; // DER INTEGER (0x02) format
    }

    const der = [
      0x30, // DER sequence tag
      4 + encodeInteger(r).length + encodeInteger(s).length, // Length
      ...encodeInteger(r),
      ...encodeInteger(s),
    ];

    return new Uint8Array(der);
  }

  pemToArrayBuffer(pem: string): ArrayBuffer {
    const base64 = pem.replace(/-----(BEGIN|END) [A-Z ]+-----/g, "").replace(/\s+/g, "");
    const binary = atob(base64);

    const buffer = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      buffer[i] = binary.charCodeAt(i);
    }
    return buffer.buffer;
  }

  async importKeyPairForECDSA(pem: string): Promise<{ privateKey: CryptoKey; publicKey: CryptoKey }> {
    // Extract private and public key parts
    const privateKeyMatch = pem.match(/-----BEGIN PRIVATE KEY-----(.*?)-----END PRIVATE KEY-----/s);
    const publicKeyMatch = pem.match(/-----BEGIN PUBLIC KEY-----(.*?)-----END PUBLIC KEY-----/s);

    if (!privateKeyMatch || !publicKeyMatch) {
      throw new Error("Invalid PEM format: Missing private or public key");
    }

    const privateKeyPEM = privateKeyMatch[0];
    const publicKeyPEM = publicKeyMatch[0];

    // Convert to binary
    const privateKeyBuffer = this.pemToArrayBuffer(privateKeyPEM);
    const publicKeyBuffer = this.pemToArrayBuffer(publicKeyPEM);

    // Import Private Key
    const privateKey = await crypto.subtle.importKey(
      "pkcs8",
      privateKeyBuffer,
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["sign"]
    );

    // Import Public Key
    const publicKey = await crypto.subtle.importKey(
      "spki",
      publicKeyBuffer,
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["verify"]
    );

    return { privateKey, publicKey };
  }

  async importPrivateKeyForECDH(pem: string): Promise<CryptoKey> {
    // Extract Base64 content from PEM
    const base64Key = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s+/g, "");

    // Decode Base64 to ArrayBuffer
    const pkcs8 = Uint8Array.from(atob(base64Key), c => c.charCodeAt(0)).buffer;

    // Import the private key for ECDH
    return await crypto.subtle.importKey(
      "pkcs8",
      pkcs8,
      { name: "ECDH", namedCurve: "P-256" },
      true, // Extractable
      ["deriveBits"] // Required for ECDH key exchange
    );
  }


  async importPublicKeyOnlyFromPem(pem: string): Promise<CryptoKey> {
    // Extract Base64 content from PEM
    const base64Key = pem.replace(/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s+/g, "");

    // Decode Base64 to ArrayBuffer
    const spki = Uint8Array.from(atob(base64Key), c => c.charCodeAt(0)).buffer;

    // Import the public key into WebCrypto API
    return await crypto.subtle.importKey(
      "spki",
      spki,
      { name: "ECDH", namedCurve: "P-256" },
      true,
      []
    );
  }

  async exportRawPublicKey(publicKey: CryptoKey): Promise<Uint8Array> {
    const rawKey = await crypto.subtle.exportKey("raw", publicKey); // Returns x || y
    return new Uint8Array(rawKey);
  }

  compressPublicKey(uncompressedKey: Uint8Array): Uint8Array {
    if (uncompressedKey.length !== 65 || uncompressedKey[0] !== 0x04) {
      throw new Error("Invalid uncompressed public key format");
    }

    const x = uncompressedKey.slice(1, 33); // First 32 bytes after 0x04
    const y = uncompressedKey.slice(33, 65); // Last 32 bytes

    // Determine prefix based on y's parity
    const prefix = (y[y.length - 1] % 2 === 0) ? 0x02 : 0x03;

    // Compressed key format: prefix || x
    return new Uint8Array([prefix, ...x]);
  }

  async deriveSharedSecret(privateKey : CryptoKey, publicKey : CryptoKey) {
    return await crypto.subtle.deriveBits(
      { name: "ECDH", public: publicKey },
      privateKey,
      256 // Output length in bits
    );
  }

  async deriveAESKey(sharedSecret: ArrayBuffer, salt: Uint8Array, info: Uint8Array): Promise<CryptoKey> {
    const hkdfKey = await crypto.subtle.importKey("raw", sharedSecret, { name: "HKDF" }, false, ["deriveKey"]);

    return await crypto.subtle.deriveKey(
      {
        name: "HKDF",
        salt: salt,
        info: info,
        hash: "SHA-256"
      },
      hkdfKey,
      { name: "AES-GCM", length: 256 },
      true,
      ["encrypt", "decrypt"]
    );
  }

  async initializeAESCipher(aesKey: CryptoKey) {
    const iv = crypto.getRandomValues(new Uint8Array(12)); // 12-byte IV for AES-GCM

    return {
      encrypt: async (plaintext: string) => {
        const encoded = new TextEncoder().encode(plaintext);
        return await crypto.subtle.encrypt({ name: "AES-GCM", iv }, aesKey, encoded);
      },
      decrypt: async (ciphertext: ArrayBuffer) => {
        return new TextDecoder().decode(
          await crypto.subtle.decrypt({ name: "AES-GCM", iv }, aesKey, ciphertext)
        );
      },
      iv
    };
  }
}
