import { TestBed } from '@angular/core/testing';
import { CryptoService } from './crypto.service';

describe('CryptoService', () => {
  let service: CryptoService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CryptoService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should generate a valid ECDSA key pair in PEM format', async () => {
    const keyPair = await service.generateKeyPair();
    expect(keyPair.privateKeyPEM).toContain('-----BEGIN PRIVATE KEY-----');
    expect(keyPair.privateKeyPEM).toContain('-----END PRIVATE KEY-----');
    expect(keyPair.publicKeyPEM).toContain('-----BEGIN PUBLIC KEY-----');
    expect(keyPair.publicKeyPEM).toContain('-----END PUBLIC KEY-----');
  });

  it('should format DER to PEM via derToPem', () => {
    const fakeDer = new Uint8Array([1, 2, 3, 4, 5]);
    const pem = service.derToPem(fakeDer, 'TEST KEY');
    expect(pem).toContain('-----BEGIN TEST KEY-----');
    expect(pem).toContain('-----END TEST KEY-----');
  });

  it('should compute SHA-256 hash correctly', async () => {
    const data = new TextEncoder().encode('hello world');
    const hashBuffer = await service.sha256(data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    // SHA-256("hello world") = b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9
    expect(hashHex).toBe('b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9');
  });

  it('should import key pair and sign/verify JWT', async () => {
    const keyPair = await service.generateKeyPair();
    const jwt = await service.jwtForKeypair('test-user', {
      privatePem: keyPair.privateKeyPEM,
      publicPem: keyPair.publicKeyPEM
    });
    expect(jwt).toBeTruthy();
    const parts = jwt.split('.');
    expect(parts.length).toBe(3);
  });

  it('should convert raw 64-byte signature to DER format', () => {
    const rawSig = new Uint8Array(64).fill(0x01);
    rawSig[0] = 0x85; // Test MSB set to verify padding
    const derSig = service.convertSignatureFromRawToDER(rawSig);
    expect(derSig[0]).toBe(0x30); // DER sequence tag
    expect(derSig.length).toBeGreaterThan(64);
  });

  it('convertSignatureFromRawToDER should throw for invalid length', () => {
    expect(() => service.convertSignatureFromRawToDER(new Uint8Array(32))).toThrowError('Invalid raw signature length');
  });

  it('should compress uncompressed public key', () => {
    const uncompressed = new Uint8Array(65);
    uncompressed[0] = 0x04; // uncompressed tag
    uncompressed.fill(0x01, 1, 33); // x
    uncompressed.fill(0x02, 33, 65); // y (even -> prefix 0x02)

    const compressed = service.compressPublicKey(uncompressed);
    expect(compressed.length).toBe(33);
    expect(compressed[0]).toBe(0x02);

    // Test odd y -> prefix 0x03
    uncompressed[64] = 0x03;
    const compressedOdd = service.compressPublicKey(uncompressed);
    expect(compressedOdd[0]).toBe(0x03);
  });

  it('compressPublicKey should throw for invalid format', () => {
    expect(() => service.compressPublicKey(new Uint8Array(33))).toThrowError('Invalid uncompressed public key format');
    expect(() => service.compressPublicKey(new Uint8Array(65))).toThrowError('Invalid uncompressed public key format');
  });

  it('should derive AES key and encrypt/decrypt successfully with initializeAESCipher', async () => {
    const sharedSecret = new Uint8Array(32).fill(0x42).buffer;
    const salt = new Uint8Array(16).fill(0x11);
    const info = new Uint8Array(16).fill(0x22);

    const aesKey = await service.deriveAESKey(sharedSecret, salt, info);
    expect(aesKey).toBeTruthy();

    const cipher = await service.initializeAESCipher(aesKey);
    const plaintext = 'Secret tartarus message 123';
    const ciphertext = await cipher.encrypt(plaintext);
    expect(ciphertext.byteLength).toBeGreaterThan(0);

    const decrypted = await cipher.decrypt(ciphertext);
    expect(decrypted).toBe(plaintext);
  });
});
