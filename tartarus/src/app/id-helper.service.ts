import { Injectable } from '@angular/core';
import * as base32 from 'hi-base32';

@Injectable({
  providedIn: 'root'
})
export class IdHelperService {

  constructor() { }

  generateBase32Id(length: number = 6): string {
    const byteLength = Math.max(8, Math.ceil((length * 5) / 8) + 4);
    const randomBytes = new Uint8Array(byteLength);
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      crypto.getRandomValues(randomBytes);
    } else {
      for (let i = 0; i < byteLength; i++) {
        randomBytes[i] = Math.floor(Math.random() * 256);
      }
    }
    const encoded = base32.encode(randomBytes);
    return encoded.replace(/=+$/, '').substring(0, length).toUpperCase();
  }
}
