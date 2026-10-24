import {Injectable} from '@angular/core';
import {SignJWT} from 'jose';

@Injectable({
  providedIn: 'root'
})
export class CryptoService {

  constructor() { }

  async generateKeyPair(): Promise<{ privateKeyPEM: string; publicKeyPEM: string }> {
    // Generate key pair using WebCrypto API
    const keyPair = await crypto.subtle.generateKey(
      { name: "ECDSA", namedCurve: "P-256" },
      true, // Extractable keys
      ["sign", "verify"]
    );

    // Export the private key (PKCS#8 format)
    const privateKeyDER = await crypto.subtle.exportKey("pkcs8", keyPair.privateKey);
    const privateKeyPEM = this.derToPem(new Uint8Array(privateKeyDER), "PRIVATE KEY");

    // Export the public key (SPKI format)
    const publicKeyDER = await crypto.subtle.exportKey("spki", keyPair.publicKey);
    const publicKeyPEM = this.derToPem(new Uint8Array(publicKeyDER), "PUBLIC KEY");

    return { privateKeyPEM, publicKeyPEM };
  }

  derToPem(der: Uint8Array, label: string): string {
    const base64 = btoa(String.fromCharCode(...der));
    const formatted = base64.match(/.{1,64}/g)?.join("\n") ?? "";
    return `-----BEGIN ${label}-----\n${formatted}\n-----END ${label}-----`;
  }

  async jwtForKeypair(subject : string, keypair : {privatePem : string | null, publicPem : string | null}) : Promise<string> {
    const importedKeypair = await this.importKeyPairForECDSA(`${keypair.privatePem}${keypair.publicPem}`);
    return await new SignJWT({sub: subject, name: subject})
      .setProtectedHeader({alg: 'ES256'})
      .setIssuedAt()
      .setExpirationTime('1h')
      .sign(importedKeypair.privateKey);
  }

  async generateCompressedPublicKey(publicKey : CryptoKey): Promise<Uint8Array> {
    // console.log("publicKey: " + publicKey);
    // Export and compress public key
    const rawPublicKey = await this.exportRawPublicKey(publicKey);
    const compressedKey = this.compressPublicKey(rawPublicKey);

    // console.log("Compressed Public Key:", compressedKey);
    return compressedKey;
  }

  async sha256(data: Uint8Array): Promise<ArrayBuffer> {
    return await crypto.subtle.digest("SHA-256", data as BufferSource);
  }

  async hashAndSignFb(privateKey: CryptoKey, data: Uint8Array): Promise<ArrayBuffer> {
    const offsetToTable = data[0] | (data[1] << 8);
    const offsetToVTable = data[offsetToTable] | (data[offsetToTable + 1] << 8);
    const vTableStart = offsetToTable - offsetToVTable;
    const vtableAndContract = data.slice(vTableStart);

    return await this.hashAndSignData(privateKey, vtableAndContract);
  }

  async hashAndSignData(privateKey: CryptoKey, data: Uint8Array): Promise<ArrayBuffer> {
    // console.log("Hashing data: " + new Uint8Array(data));
    const hash = await this.sha256(data); // Compute hash first

    // console.log("Hash: " + new Uint8Array(hash));
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

  async importKeyPairForECDSA(pem: string): Promise<{ privateKey: CryptoKey; publicKey: CryptoKey, privateKeyPem: string, publicKeyPem: string }> {
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

    return { privateKey, publicKey, privateKeyPem : privateKeyPEM, publicKeyPem : publicKeyPEM };
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
        salt: salt as BufferSource,
        info: info as BufferSource,
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

  /**
   * Deterministically derives a secp256r1 (P-256) bot keypair from the Author's PKCS#8 private key PEM
   * using HKDF-SHA256 (salt = "tartarus-bot-kdf-v1", info = "${internalName}:${keyVersion}", L = 48 bytes).
   */
  async deriveBotKeyPair(
    authorPrivateKeyPem: string,
    internalName: string,
    keyVersion: number = 1
  ): Promise<{
    compressedPublicKeyBase64: string;
    privateKeyPEM: string;
    publicKeyPEM: string;
  }> {
    const privateKeyBuffer = this.pemToArrayBuffer(authorPrivateKeyPem);
    const authorPrivKey = await crypto.subtle.importKey(
      "pkcs8",
      privateKeyBuffer,
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["sign"]
    );
    const authorJwk = await crypto.subtle.exportKey("jwk", authorPrivKey);
    if (!authorJwk.d) {
      throw new Error("Failed to extract private scalar from Author key");
    }

    const ikm = this.base64UrlToBytes(authorJwk.d);
    const salt = new TextEncoder().encode("tartarus-bot-kdf-v1");
    const info = new TextEncoder().encode(`${internalName}:${keyVersion}`);

    const hkdfKey = await crypto.subtle.importKey("raw", ikm as BufferSource, { name: "HKDF" }, false, ["deriveBits"]);
    const okmBuffer = await crypto.subtle.deriveBits(
      {
        name: "HKDF",
        hash: "SHA-256",
        salt: salt as BufferSource,
        info: info as BufferSource,
      },
      hkdfKey,
      48 * 8
    );
    const okm = new Uint8Array(okmBuffer);

    const n = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551n;
    const okmBigInt = this.bytesToBigInt(okm);
    const dBot = (okmBigInt % (n - 1n)) + 1n;

    const { x, y } = this.p256ScalarBaseMult(dBot);
    const dBytes = this.bigIntTo32Bytes(dBot);
    const xBytes = this.bigIntTo32Bytes(x);
    const yBytes = this.bigIntTo32Bytes(y);

    const prefix = (y & 1n) === 0n ? 0x02 : 0x03;
    const compressedPub = new Uint8Array(33);
    compressedPub[0] = prefix;
    compressedPub.set(xBytes, 1);
    const compressedPublicKeyBase64 = btoa(String.fromCharCode(...compressedPub));

    const botPrivJwk: JsonWebKey = {
      kty: "EC",
      crv: "P-256",
      x: this.bytesToBase64Url(xBytes),
      y: this.bytesToBase64Url(yBytes),
      d: this.bytesToBase64Url(dBytes),
      ext: true,
    };
    const botPubJwk: JsonWebKey = {
      kty: "EC",
      crv: "P-256",
      x: this.bytesToBase64Url(xBytes),
      y: this.bytesToBase64Url(yBytes),
      ext: true,
    };

    const botPrivCryptoKey = await crypto.subtle.importKey(
      "jwk",
      botPrivJwk,
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["sign"]
    );
    const botPubCryptoKey = await crypto.subtle.importKey(
      "jwk",
      botPubJwk,
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["verify"]
    );

    const pkcs8Der = await crypto.subtle.exportKey("pkcs8", botPrivCryptoKey);
    const spkiDer = await crypto.subtle.exportKey("spki", botPubCryptoKey);

    return {
      compressedPublicKeyBase64,
      privateKeyPEM: this.derToPem(new Uint8Array(pkcs8Der), "PRIVATE KEY"),
      publicKeyPEM: this.derToPem(new Uint8Array(spkiDer), "PUBLIC KEY"),
    };
  }

  private base64UrlToBytes(b64url: string): Uint8Array {
    const pad = "=".repeat((4 - (b64url.length % 4)) % 4);
    const b64 = (b64url + pad).replace(/-/g, "+").replace(/_/g, "/");
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      out[i] = bin.charCodeAt(i);
    }
    return out;
  }

  private bytesToBase64Url(bytes: Uint8Array): string {
    return btoa(String.fromCharCode(...bytes))
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");
  }

  private bytesToBigInt(bytes: Uint8Array): bigint {
    let res = 0n;
    for (const b of bytes) {
      res = (res << 8n) | BigInt(b);
    }
    return res;
  }

  private bigIntTo32Bytes(val: bigint): Uint8Array {
    const out = new Uint8Array(32);
    let temp = val;
    for (let i = 31; i >= 0; i--) {
      out[i] = Number(temp & 0xffn);
      temp >>= 8n;
    }
    return out;
  }

  private p256ScalarBaseMult(k: bigint): { x: bigint; y: bigint } {
    const p = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFFn;
    const a = p - 3n;
    const Gx = 0x6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296n;
    const Gy = 0x4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5n;

    const mod = (n: bigint) => ((n % p) + p) % p;
    const modInv = (num: bigint): bigint => {
      let [t, newT] = [0n, 1n];
      let [r, newR] = [p, mod(num)];
      while (newR !== 0n) {
        const q = r / newR;
        [t, newT] = [newT, t - q * newT];
        [r, newR] = [newR, r - q * newR];
      }
      return mod(t);
    };

    type Point = { x: bigint; y: bigint } | null;
    const pointAdd = (P: Point, Q: Point): Point => {
      if (!P) return Q;
      if (!Q) return P;
      if (P.x === Q.x && P.y !== Q.y) return null;
      const lam =
        P.x === Q.x
          ? mod((3n * P.x * P.x + a) * modInv(2n * P.y))
          : mod((Q.y - P.y) * modInv(Q.x - P.x));
      const rx = mod(lam * lam - P.x - Q.x);
      const ry = mod(lam * (P.x - rx) - P.y);
      return { x: rx, y: ry };
    };

    let result: Point = null;
    let addend: Point = { x: Gx, y: Gy };
    let scalar = k;
    while (scalar > 0n) {
      if ((scalar & 1n) === 1n) {
        result = pointAdd(result, addend);
      }
      addend = pointAdd(addend, addend);
      scalar >>= 1n;
    }
    if (!result) {
      throw new Error("Invalid scalar multiplication result");
    }
    return result;
  }
}
